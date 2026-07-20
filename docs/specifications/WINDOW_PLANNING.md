# WINDOW_PLANNING.md

*The behavioral specification of the window-planning stage.*

The key words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are used in the
sense of RFC 2119.

---

## Purpose

Window planning divides a normalized document into an ordered set of overlapping **windows** —
temporary processing views over contiguous runs of the document's units — each sized so that the
request built from it, together with room for the model's response, fits within what the model can
consider at once. Window planning exists so that documents of any size can be processed without
asking the caller to divide them, and so that every later stage receives views it can reason about
in isolation while their conclusions remain reconcilable.

This document defines *what* window planning produces, *when* it produces it, *why* each rule
exists, the guarantees and invariants that hold, and the failures that are possible. It defines the
behavior completely enough that two independent teams implement equivalent behavior.

## Scope

This document governs the single pipeline stage that transforms a prepared document into a set of
windows. It begins after extraction and normalization have produced the prepared document and ends
when the set of windows has been produced. It does not cover any stage before or after it.

## Relationship to the canonical documentation

This document refines, and never contradicts, the frozen canonical documents. In particular:

- MODEL.md §6 (Windows) — a window is a *view*, not a fragment; it references units and owns no
  content; overlap is essential; windows disappear after processing.
- ARCHITECTURE.md §3–4 — window planning covers the whole document, overlaps adjacent windows,
  reserves room for the response, and preserves unit identity.
- CONTRIBUTING_ARCHITECTURE.md §7.2 (the index space), §7.3.1–§7.3.4 (global-ordinal stability and
  window sizing that reserves output budget), and §6.2.3 (the model exposes its context-window
  limit and a token-estimation capability, which MAY be heuristic).
- PIPELINE.md §3 (Window Planning stage responsibilities and guarantees).

Where the canonical documents state a requirement, it is authoritative. Where they delegate a
determination to "the algorithm phase," this document makes that determination and marks it as a
**specification parameter**.

## What this document does NOT specify

- It does not specify how a window is rendered into a request; that is
  [PROMPT_SPECIFICATION.md](PROMPT_SPECIFICATION.md).
- It does not specify the wording of any instruction, nor any concrete token count that depends on
  wording; token quantities are defined as functions of the model's token estimator and the
  implementation's own fixed request structure.
- It does not specify how the model is reached, nor any provider behavior.
- It does not specify what happens to an over-budget window beyond producing it and flagging it; the
  handling is defined in [RECOVERY_POLICY.md](RECOVERY_POLICY.md).

---

## 1. Definitions and terminology

- **Unit** — a document unit as defined in MODEL.md §4: the atomic, addressable element of the
  prepared document.
- **Document order** — the fixed order of the units within the prepared document (MODEL.md §4). A
  unit's **global ordinal** (its stable identity, CONTRIBUTING_ARCHITECTURE.md §7.2.4) increases
  with document order, so ordinal comparison and document-order comparison agree. This document
  never assumes ordinals are contiguous; it relies only on their order and identity.
- **First unit / last unit** — the units that are first and last in document order.
- **Window** — an ordered, contiguous run of units viewed for the purpose of obtaining one boundary
  decision. A window references units by their global ordinals; it copies nothing and owns nothing.
- **Window coverage** — the set of units a window views.
- **Content representation of a set of units** — the textual form those units take within a request,
  as defined by [PROMPT_SPECIFICATION.md](PROMPT_SPECIFICATION.md). Window planning uses this same
  representation solely to estimate size; it does not build the request.
- **Token estimate of a text** — the value returned by the model's token-estimation capability for
  that text (CONTRIBUTING_ARCHITECTURE.md §6.2.3).
- **Context-window limit** — the model's maximum usable input size in tokens, exposed by the model
  (CONTRIBUTING_ARCHITECTURE.md §6.2.3), referred to here as `maxInputTokens`.
- **Maximal valid response for a window** — the largest response the validation stage could accept
  for that window: the response that names, as boundaries, every unit of the window except the
  window's first unit (see [RESPONSE_VALIDATION.md](RESPONSE_VALIDATION.md) and
  [PROMPT_SPECIFICATION.md](PROMPT_SPECIFICATION.md) for the boundary-reference convention).

### Specification parameters

These values are fixed by this specification. They are internal, are never exposed to callers
(API.md §7), and MAY change only by a versioned change to this document (see Future evolution).

- **`CONTEXT_UNITS` = 1** — the minimum number of units of context that a candidate boundary
  position is guaranteed to have on each side within at least one window. The **overlap size** in
  units is `OVERLAP_UNITS = 2 × CONTEXT_UNITS = 2`.
- **`SAFETY_MARGIN_FRACTION` = 0.05** — the fraction of `maxInputTokens` held back to reduce the
  risk that token-estimate imprecision causes the model's input to overflow.

## 2. Inputs

Window planning consumes:

1. The **prepared document** — an ordered sequence of units with stable global ordinals.
2. The **model abstraction**, used only for two read-only capabilities: its `maxInputTokens` and its
   token estimation. Window planning MUST NOT execute the model.

It consumes nothing else. It does not consult provenance internals, metadata, the caller, or any
external state.

## 3. Outputs

Window planning produces an **ordered list of windows**. Each window records the contiguous run of
units it views, in document order, by their global ordinals. Each window is additionally marked as
either **admissible** or **over-budget** (§10). The list is ordered by each window's first unit in
ascending document order.

The output contains no content of its own, records nothing about how many boundaries exist, and
carries no artifact that must survive past merging.

## 4. Preconditions

- The prepared document is immutable and its units are in a fixed document order with stable global
  ordinals (guaranteed by normalization).
- The model reports a positive `maxInputTokens` and a token-estimation capability that returns a
  non-negative estimate for any text.

If either precondition does not hold, the stage cannot proceed and the outcome is a terminal failure
(§16).

## 5. Postconditions

- Every unit of the document is viewed by at least one window (Coverage, §12).
- Windows are in ascending document order and adjacent windows overlap by the defined amount, subject
  to the constrained-budget degenerate case (§8, §11).
- Every unit appears in every window that views it under its original global ordinal, unchanged
  (Global-ordinal preservation, §13).
- No admissible window's estimated request size exceeds the maximum usable input (§9); an over-budget
  window is one that cannot satisfy this and is flagged rather than silently oversized.

## 6. Invariants

The following MUST hold in every run:

- **I1 — Order.** Windows are produced and listed in ascending document order of their first unit.
- **I2 — Contiguity.** Each window views a contiguous run of units in document order, with no gaps.
- **I3 — Coverage.** The union of all windows' coverage is the entire set of units.
- **I4 — Identity.** A unit's global ordinal within a window is exactly its global ordinal in the
  prepared document; window planning never renumbers, reorders, or reassigns identities
  (CONTRIBUTING_ARCHITECTURE.md §7.3.1).
- **I5 — Progress.** Each window after the first begins at a strictly later unit than the previous
  window, so planning terminates.
- **I6 — No ownership.** A window holds no content; removing all windows removes no unit.

## 7. Token estimation assumptions

- Window planning MUST obtain every token quantity from the model's token estimation applied to the
  relevant text; it MUST NOT assume a fixed number of characters or bytes per token.
- Token estimation MAY be heuristic (CONTRIBUTING_ARCHITECTURE.md §6.2.3). Window planning therefore
  treats estimates as approximate and holds back a safety margin (§9). It MUST NOT assume estimates
  are exact.
- Token estimation MUST NOT be assumed additive across units: the estimate of a run of units is the
  estimate of that run's content representation taken as a whole, not the sum of per-unit estimates.
- If the estimator under-counts by more than the safety margin, the model's input may overflow at
  execution time; that outcome is a possible failure handled downstream (§16 and
  [RECOVERY_POLICY.md](RECOVERY_POLICY.md)), not a defect of this stage.

## 8. Context-window reservation, maximum usable input, and window sizing

Window planning reserves budget for the fixed request framing and for the model's response, so that a
window is never sized to the full context window — which would cause the model to truncate its
answer, an outcome no retry can fix (CONTRIBUTING_ARCHITECTURE.md §7.3.4).

For a candidate window whose coverage is a contiguous run of units `P`, define:

- `promptOverhead` — the token estimate of the fixed request framing that does not depend on the
  window's units (the constant instruction and structural scaffolding defined by
  [PROMPT_SPECIFICATION.md](PROMPT_SPECIFICATION.md)). It is computed once per run.
- `contentCost(P)` — the token estimate of the content representation of `P`.
- `outputReservation(P)` — the token estimate of the **maximal valid response for the window** `P`.
  Because the maximal valid response names every unit of the window except its first, this reservation
  scales with the number of boundaries the window can produce, as required by
  CONTRIBUTING_ARCHITECTURE.md §7.3.4.
- `safetyMargin` — `ceil(SAFETY_MARGIN_FRACTION × maxInputTokens)`.

A window with coverage `P` is **admissible** when, and only when:

> `contentCost(P) + promptOverhead + outputReservation(P) + safetyMargin ≤ maxInputTokens`.

The **maximum usable input** for content is the largest `contentCost(P)` an admissible window may
carry; it is not a fixed number but the residual left after `promptOverhead`, `outputReservation(P)`,
and `safetyMargin` are reserved from `maxInputTokens`.

**Window sizing (deterministic).** Windows are formed left to right:

1. The first window begins at the first unit.
2. A window that begins at a given unit is grown by appending the next unit in document order, one at
   a time, for as long as the window remains admissible after the append. Growth stops when appending
   the next unit would make the window inadmissible, or when no units remain.
3. The window's last unit is the last unit appended.
4. The next window begins at the unit whose document-order position is
   `max(startPosition + 1, endPosition − OVERLAP_UNITS + 1)`, where `startPosition` and `endPosition`
   are the document-order positions of the current window's first and last units. This repeats the
   last `OVERLAP_UNITS` units of the current window as the leading units of the next window whenever
   the window is long enough, and otherwise advances by exactly one unit to guarantee progress (I5).
5. Planning stops once a window's last unit is the document's last unit.

This procedure is a pure function of the units, `maxInputTokens`, and the token estimates; it
introduces no other source of variation.

## 9. Overlap definition and guarantees

**Overlap definition.** Two adjacent windows overlap when the trailing units of the earlier window
are repeated as the leading units of the later window. The intended overlap is `OVERLAP_UNITS` units.
When a window is too short for full overlap while still making progress, the overlap is the largest
amount that still advances the next window by at least one unit (§8, step 4).

**Overlap guarantees.** Overlap exists so that a boundary falling near a window's edge is also seen
within a neighboring window, where it sits away from the edge with context on both sides (MODEL.md
§6). This specification guarantees:

- **O1 — Seam continuity.** Adjacent windows always share at least the boundary region between them,
  so there is no position between two consecutive units that lies at the exclusive edge of every
  window covering it, except in the constrained-budget degenerate case (§11).
- **O2 — Two-sided context.** When windows can contain more than `OVERLAP_UNITS` units, every
  candidate boundary position interior to the document has, in at least one window, at least
  `CONTEXT_UNITS` units on each side. This is the property the boundary merger relies upon to choose
  the better-informed window (see [BOUNDARY_MERGING.md](BOUNDARY_MERGING.md)).

Overlap is the reason merging must reconcile decisions at all; that reconciliation is specified in
[BOUNDARY_MERGING.md](BOUNDARY_MERGING.md).

## 10. Window ordering and global-ordinal preservation

- Windows are ordered by ascending document order of their first unit (I1). This order is meaningful
  and MUST be preserved for the per-window processing and for merging.
- Every unit is presented within every window under exactly the global ordinal it holds in the
  prepared document (I4). No window-local renumbering is ever introduced. This is the single most
  consequential invariant of the stage: a boundary decision expressed against a unit's global ordinal
  means the same thing in every window (CONTRIBUTING_ARCHITECTURE.md §7.3.1–§7.3.3).

## 11. Coverage guarantees and document-size cases

- **Coverage (I3).** Every unit is viewed by at least one window. Because each window ends at some
  unit and the next window begins at or before that unit (§8, step 4), the windows' coverage is
  gap-free.

- **Large document.** When the document does not fit in a single admissible window, multiple
  overlapping windows are produced by §8. Their number is bounded by the number of units (§15).

- **Tiny document (fits in one window).** When all units form a single admissible window, exactly one
  window is produced; overlap does not arise, and planning stops immediately (the single-window case).

- **Single-window case.** A single window covering the whole document is produced when the whole
  document is admissible. It is the normal outcome for small documents and is not special beyond
  having no neighbors and therefore no overlap.

- **Empty document (zero units).** No window is produced; the output is the empty list. No model
  interaction occurs, and later stages receive no windows (see
  [CHUNK_CONSTRUCTION.md](CHUNK_CONSTRUCTION.md) for the resulting empty output).

- **Single-unit document.** Exactly one window is produced, covering the one unit. Such a window can
  yield no boundary (a boundary is a division *between* units, and one unit admits none); its handling
  as a boundary-less window is defined in [RECOVERY_POLICY.md](RECOVERY_POLICY.md) §"windows that
  cannot produce a boundary."

- **Constrained-budget degenerate case.** When the usable content budget is so small that admissible
  windows hold fewer than `OVERLAP_UNITS + 1` units, full overlap cannot be maintained while making
  progress. The stage MUST still guarantee coverage (I3) and progress (I5) by advancing one unit at a
  time; the two-sided-context guarantee (O2) is then best-effort rather than guaranteed. This case
  MUST NOT cause an error by itself.

## 12. Oversized unit behavior

A single unit whose content representation alone makes a window inadmissible (its `contentCost`
already exceeds the maximum usable input) cannot be made to fit, because units are atomic and MUST
NOT be split (MODEL.md §4). Such a unit forms a window by itself, and that window is marked
**over-budget**.

- An over-budget window MUST NOT be sent to the model, because it would overflow the input, and
  overflow cannot be repaired by retry (CONTRIBUTING_ARCHITECTURE.md §7.3.4).
- An over-budget window is handed to the recovery path, which degrades it without a model call
  (see [RECOVERY_POLICY.md](RECOVERY_POLICY.md)).
- An over-budget window always covers exactly one unit; when two units are each individually
  admissible but jointly inadmissible, window sizing (§8) places them in separate admissible windows.

## 13. Determinism requirements

- Given the same prepared document, the same `maxInputTokens`, and the same token estimates, window
  planning MUST produce the identical ordered list of windows — identical count, identical coverage,
  identical overlap, identical ordering, and identical over-budget flags.
- Window planning MUST NOT depend on wall-clock time, randomness, iteration order of unordered
  collections, environmental state, or any prior run.
- **Residual variation, stated explicitly.** The number of units a window carries depends on
  `promptOverhead` and `contentCost`, which depend on the implementation's own fixed request
  structure and on the model's token estimator. The canonical documentation deliberately keeps the
  request wording internal and free to evolve (PROMPT.md §1, §6). Two implementations that differ
  only in that wording MAY therefore place different numbers of units in each window. This is the
  only permitted source of difference: both implementations still satisfy every invariant and
  guarantee in this document, and every later stage's semantics are identical. Whenever the two
  implementations' request framing yields the same token costs, their windows are identical.

## 14. Failure conditions

- **Non-positive usable budget (terminal).** If `promptOverhead + safetyMargin` plus the smallest
  possible `outputReservation` already meets or exceeds `maxInputTokens`, no admissible window can be
  formed for any content. This is a fundamental, non-localized failure of configuration or model
  capability and MUST be reported as a terminal chunking failure (API.md §8;
  CONTRIBUTING_ARCHITECTURE.md §9.4). It MUST NOT be silently degraded.
- **Oversized unit (localized).** A single unit that cannot fit is not a failure of this stage; it is
  flagged over-budget and localized to that one unit (§12), then handled by recovery.
- **Model capability failure (terminal).** If the model cannot supply `maxInputTokens` or a token
  estimate, planning cannot proceed and the outcome is terminal.

Window planning itself performs no model execution and therefore raises no recoverable, content-level
failures; those arise only later.

## 15. Complexity expectations

- The number of windows MUST be at most the number of units, and is typically far smaller.
- Window sizing evaluates admissibility as windows grow. Implementations MUST compute
  `contentCost(P)` and `outputReservation(P)` from the model's estimator on the relevant text; in the
  worst case this is on the order of the number of units token-estimations per window. Correctness and
  determinism take precedence over minimizing token-estimation calls; the dominant cost of the overall
  library is the model call, not this local computation (CONTRIBUTING_ARCHITECTURE.md §15).
- Memory MUST remain proportional to the number of units, since windows hold references, not copies.

## 16. Non-goals

- Window planning does not decide boundaries; it only decides views.
- It does not construct prompts, execute the model, validate responses, merge boundaries, or build
  chunks.
- It does not expose window sizing or overlap as configuration; these are internal and fixed
  (API.md §7).
- It does not attempt to guarantee that the model's actual input never overflows when the estimator is
  heuristic; it reduces that risk with a safety margin and delegates the residual outcome to recovery.

## 17. Future evolution

- The specification parameters (`CONTEXT_UNITS`, `SAFETY_MARGIN_FRACTION`) MAY be revised by a
  versioned change to this document. Such a change alters how many windows a document is divided into
  but MUST preserve every invariant and guarantee here.
- The reservation model MAY become more precise (for example, a tighter output reservation) provided
  it never reserves less than is required to represent the maximal valid response, so that truncation
  remains impossible.
- No change may introduce caller-visible configuration, provider-specific behavior, or a dependence on
  anything other than the prepared document and the model's read-only capabilities.
