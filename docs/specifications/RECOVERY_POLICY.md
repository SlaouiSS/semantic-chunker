# RECOVERY_POLICY.md

*The behavioral specification of failure handling, retry, degradation, and run bookkeeping.*

The key words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are used in the
sense of RFC 2119.

---

## Purpose

Because the library reaches outside itself to a language model, difficulty is a normal operating
condition rather than an exceptional event. This document defines how the pipeline responds to
difficulty: which failures are localized and recovered, which are fundamental and end the run, how many
times a window is retried, how an unresolvable window is degraded, what warnings and diagnostics are
recorded, and how the run's processing information and execution metadata are populated. It defines this
behavior deterministically, so that the library's response to trouble is knowable in advance.

## Scope

This document governs the cross-cutting failure behavior that wraps the per-window processing and the
run's bookkeeping. It covers retry, degradation, fallback, warning generation, and the population of the
result's processing information and execution metadata. It applies from the moment a window is submitted
for a decision through the assembly of the run's account.

## Relationship to the canonical documentation

This document refines, and never contradicts, the frozen canonical documents:

- CONTRIBUTING_ARCHITECTURE.md §9 — the failure model: a single bad window MUST NOT fail the whole job by
  default; bounded retry with reprompt, then degrade that one window to a single chunk (a no-op boundary
  decision) and record a warning; every degraded window, retry, and recoverable anomaly is surfaced in the
  result; a hard, non-recoverable failure MAY throw, a recoverable localized failure MUST NOT throw by
  default.
- PIPELINE.md §7 — the graduated error flow: detect at validation, retry within limits, fall back and
  record when retry is exhausted, and reserve termination for failures that genuinely prevent completion.
- MODEL.md §9 — the result carries the chunks, processing information, warnings, and execution metadata.
- API.md §8 — validation is early and plain; recoverable failures are recovered not raised; exceptions are
  reserved for genuine failure; the result carries warnings.
- SPI.md §7 — the model implementation reports failures plainly and never performs the library's recovery.
- The domain model (frozen): the result's processing information carries the number of windows processed and
  the number degraded; each warning carries a message; the execution metadata carries the total token usage.

It uses the outcomes defined by [RESPONSE_VALIDATION.md](RESPONSE_VALIDATION.md), the over-budget window
notion of [WINDOW_PLANNING.md](WINDOW_PLANNING.md) §12, the empty-decision behavior of
[BOUNDARY_MERGING.md](BOUNDARY_MERGING.md) §7, and the boundary convention of
[PROMPT_SPECIFICATION.md](PROMPT_SPECIFICATION.md) §6.

## What this document does NOT specify

- It does not specify the wording of any warning message; it specifies what a warning MUST identify.
- It does not classify sub-kinds of a model transport failure; the model abstraction surfaces a failure to
  obtain a response as a single condition, and this document defines the library's response to it without
  inspecting provider specifics.
- It does not define the merge or chunk rules; a degraded window's effect is expressed as an empty boundary
  decision and handled uniformly by [BOUNDARY_MERGING.md](BOUNDARY_MERGING.md) and
  [CHUNK_CONSTRUCTION.md](CHUNK_CONSTRUCTION.md).

### Specification parameter

Fixed by this specification, internal, never exposed to callers (API.md §7), changeable only by a versioned
change to this document (see Future evolution):

- **`ATTEMPT_MAX` = 3** — the maximum number of model attempts for a single window: one initial attempt plus
  up to two retries. It realizes the "bounded retry" requirement of CONTRIBUTING_ARCHITECTURE.md §9.2.

---

## 1. Failure model

Every failure is exactly one of two kinds:

- **Recoverable failure (window-local).** A difficulty affecting one window that does not prevent the rest
  of the document from being processed. The pipeline recovers from it and continues, and the whole run does
  not throw because of it. Recoverable failures are:
  1. A window's model response is **rejected by validation** (any of the rejection classes of
     [RESPONSE_VALIDATION.md](RESPONSE_VALIDATION.md) §4). The model returned something, but it was not a
     usable decision.
  2. A window is **over-budget** (WINDOW_PLANNING §12): its single unit cannot fit, so it is never sent to
     the model.

- **Terminal failure (run-level).** A fundamental condition that genuinely prevents the run from completing.
  The run stops and reports the failure rather than returning a result (API.md §8;
  CONTRIBUTING_ARCHITECTURE.md §9.4). Terminal failures are:
  1. A **non-positive usable budget** (WINDOW_PLANNING §14): no window can be formed for any content.
  2. A **model execution failure** — the model abstraction fails to return any response for an attempt
     (surfaced as the model's failure condition). This is treated as the model being unusable, which is not
     localized to one window (§8).
  3. An **internal invariant violation** — a stage receiving input that a guarantee upstream should have
     ensured (for example a validated decision referencing a unit not in its window). This indicates a
     defect, not a model-caused condition.
  4. An **input the pipeline cannot process at all**, reported by extraction as an extraction failure before
     any window exists.

The distinction is stable and knowable in advance (API.md §8): a bad *response* is recoverable; an inability
to *obtain* a response, or to *form* windows, or a broken invariant, is terminal.

## 2. Per-window processing and where recovery attaches

Each window that requires a decision is processed independently, in window order. For such a window the
pipeline renders the request, executes it against the model, and validates the response. Recovery attaches at
two points: around validation of the response (retry, then degrade — §3, §5) and before execution for a
window that cannot be sent (over-budget — §6), and, for windows that cannot produce a boundary at all,
before any model interaction (§7).

## 3. Retry philosophy

- Retry exists to recover from a window whose model response could not be validated — most commonly a
  response that was not well-formed. A fresh attempt may yield a usable decision where the first did not.
- Retry applies **only** to recoverable, validation-level failures (§1). It does **not** apply to terminal
  failures; in particular, a failure to obtain any response is terminal and is never retried by this
  specification (§8).
- A retry **re-presents the same window** to the model. The request is the deterministic request for that
  window ([PROMPT_SPECIFICATION.md](PROMPT_SPECIFICATION.md) §10); the pipeline does not alter the prompt, the
  generation hints, or the window between attempts. No prompt variation or parameter escalation is introduced,
  because none is specified and none may be invented.

## 4. Retry limits

- A single window is attempted at most **`ATTEMPT_MAX`** times in total: the initial attempt plus up to
  `ATTEMPT_MAX − 1` retries.
- An attempt is counted when the model is executed for the window and returns a response that is then
  validated. Attempts stop as soon as a response is accepted by validation, or when `ATTEMPT_MAX` attempts
  have all been rejected.
- Retry is bounded and never unbounded (CONTRIBUTING_ARCHITECTURE.md §9.2). The **count** of attempts is
  fixed and deterministic; whether any given attempt succeeds depends on the model's response, which is the
  only non-deterministic element (§14).

## 5. Retry ordering

- Windows are processed in the fixed window order (WINDOW_PLANNING §10). Within a window, attempts are made
  sequentially: an attempt is validated before the next attempt is made.
- A window's attempts are completed — reaching either an accepted response or `ATTEMPT_MAX` rejections and
  degradation — before the next window is processed. Recovery for one window never interleaves with another
  window.
- This ordering is deterministic and independent of timing.

## 6. Degradation philosophy and fallback conditions

A window is **degraded** when it cannot be resolved cleanly. Degradation is the fallback that lets the run
continue rather than fail because of one window (CONTRIBUTING_ARCHITECTURE.md §9.2). A window is degraded in
exactly these cases:

- **Retry exhausted.** The window's response was rejected by validation on all `ATTEMPT_MAX` attempts (§4).
- **Over-budget.** The window's single unit cannot fit and the window is never sent to the model
  (WINDOW_PLANNING §12); retry cannot help, so the window is degraded without any attempt.

No other condition degrades a window. A terminal failure is never degraded; it ends the run (§1, §8).

## 7. Single-chunk fallback (the no-op boundary decision)

Degrading a window yields a **no-op boundary decision**: the window's validated decision becomes the **empty
set** — it contributes no cut (CONTRIBUTING_ARCHITECTURE.md §9.2, which defines "degrade to a single chunk" as
"a no-op boundary decision").

- A degraded window therefore reports no boundary. It still participates in merging as a covering window whose
  silence means "no cut here where I am authoritative" ([BOUNDARY_MERGING.md](BOUNDARY_MERGING.md) §7). The
  exclusive region of a degraded window, having no cut, is absorbed into a single chunk; a region it shares
  with a neighbor may still be cut by that neighbor where the neighbor is the authoritative, better-informed
  window. This is the precise meaning of "single chunk" for a degraded window: it declines to divide.
- **Windows that cannot produce a boundary at all** are distinct from degradation and are handled before any
  model interaction: a window that presents fewer than two units admits no cut (a cut is a division between
  two units), so it is resolved locally as contributing the **empty** decision, **without** a model call,
  **without** a retry, and **without** a warning. This is normal behavior, not degradation, and does not count
  toward degraded windows (§10). It arises only for a single-unit document (WINDOW_PLANNING §11).

## 8. Fatal (terminal) failures

- **Model execution failure.** When the model abstraction fails to return a response for an attempt, the model
  cannot be used, and this condition is not confined to one window. The run **terminates**, propagating the
  model failure (CONTRIBUTING_ARCHITECTURE.md §9.4: a model that is unreachable MAY throw). This specification
  does **not** retry a failure to obtain a response and does **not** degrade it, because the model abstraction
  surfaces such a failure as a single condition without a signal that it is transient, and inferring one would
  be inventing provider behavior. Retry and degradation are reserved for a response that was obtained but could
  not be validated (§3, §6).
- **Non-positive usable budget.** When window planning cannot form any window (WINDOW_PLANNING §14), the run
  terminates with a chunking failure.
- **Internal invariant violation.** When a stage receives input that an upstream guarantee should have
  ensured, the run terminates; this is a defect, not a recoverable condition.
- **Extraction failure.** When the document cannot be interpreted at all, extraction reports a failure before
  any window exists, and the run terminates (SPI.md §7).

A terminal failure ends the run without returning a result. No partial result is returned for a terminal
failure (API.md §8).

## 9. Warning generation

- Exactly **one warning** is recorded for **each degraded window** (§6). A window that is retried but then
  accepted produces **no** warning (it was resolved). A window resolved locally for want of a possible boundary
  (§7) produces **no** warning.
- A warning MUST identify the affected window unambiguously by the span of units it covers (its units' global
  ordinals) and MUST convey the degradation category (retry-exhausted or over-budget). The exact wording is an
  implementation detail and is not fixed here; the identification and category are required.
- Warnings are recorded in the order of the windows they concern (window order), so the account is
  deterministic.
- Recovery is never silent: every degradation is surfaced as a warning in the result
  (CONTRIBUTING_ARCHITECTURE.md §9.3).

## 10. Diagnostic recording and ProcessingInfo interaction

The run's **processing information** is populated deterministically:

- **Windows processed.** The number of windows the document was processed as — that is, the total number of
  windows window planning produced for the document, including any that were degraded and any single-unit
  window resolved locally (§7). For an empty document this is zero.
- **Windows degraded.** The number of windows that were degraded (§6): the count of retry-exhausted windows
  plus the count of over-budget windows. A single-unit window resolved locally (§7) is **not** counted as
  degraded. This count is always less than or equal to the number of windows processed, and equals the number
  of warnings recorded (§9).

These counts, together with the warnings, are the per-window diagnostics the caller uses to learn whether a
run was clean (CONTRIBUTING_ARCHITECTURE.md §9.3).

## 11. ExecutionMetadata interaction

The run's **execution metadata** carries the **total token usage** of the run, populated deterministically:

- Every response the model returns contributes its reported token usage to the total — including responses that
  were subsequently rejected by validation and including every retry attempt that returned a response. Token
  usage reflects work the model actually performed, whether or not the response was usable
  (CONTRIBUTING_ARCHITECTURE.md §6.2.2).
- An attempt that failed to obtain any response contributes nothing (there is no response and, per §8, the run
  terminates).
- A window never sent to the model — an over-budget window (§6) or a single-unit window resolved locally (§7) —
  contributes nothing.
- The total is the sum, over all responses obtained during the run, of their reported input and output token
  usage.

## 12. Recoverable failures (summary)

- A rejected response (validation) is recoverable: retry up to `ATTEMPT_MAX`, then degrade (empty decision) and
  warn.
- An over-budget window is recoverable: degrade (empty decision) and warn, without a model call.
- Recoverable failures never cause the run to throw (API.md §8; CONTRIBUTING_ARCHITECTURE.md §9.4). They are
  absorbed and recorded.

## 13. Terminal failures (summary)

- A failure to obtain any response, a non-positive usable budget, an internal invariant violation, or an
  extraction failure is terminal: the run throws and returns no result (§8).
- Terminal failures carry enough context to diagnose the incident (API.md §8;
  CONTRIBUTING_ARCHITECTURE.md §9.1). A caller may treat any exception from the library as a genuine,
  non-recoverable problem.

## 14. Determinism

- The **decision procedure** of recovery is fully deterministic: given the same sequence of model responses and
  model outcomes, the pipeline makes the same retry decisions, degrades the same windows, records the same
  warnings in the same order, and computes the same processing information and execution metadata.
- Which windows end up degraded depends on **whether** each attempt's response validates, and that depends on
  the model's responses — the single non-deterministic element of the pipeline (PIPELINE.md §8). The number of
  attempts, the order of processing, the classification of every failure, and the bookkeeping are all
  deterministic.
- Recovery MUST NOT depend on wall-clock time, randomness, iteration order of unordered collections,
  environmental state, or any prior run, except through the model's responses.

## 15. Future evolution

- `ATTEMPT_MAX` MAY be revised by a versioned change to this document; it MUST remain a finite bound.
- A more nuanced treatment of a failure to obtain a response — for example distinguishing a transient failure
  that is worth retrying from a fundamental one that is not — would require the model abstraction to surface
  that distinction. The abstraction is frozen and surfaces no such signal today, so this specification treats a
  failure to obtain a response as terminal. If the abstraction later gains such a capability, this policy MAY be
  extended additively to retry transient failures, without changing how validation-level failures are handled.
- Degradation MUST remain a no-op boundary decision (§7); it MUST NOT become an alternative chunking method
  (CONTRIBUTING_ARCHITECTURE.md §9.2). Warnings MUST continue to surface every degradation.
- No change may introduce caller-visible configuration of retry or degradation, provider-specific behavior, or
  a new public concept.
