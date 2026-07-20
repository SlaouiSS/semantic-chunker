# RESPONSE_VALIDATION.md

*The behavioral specification of response validation.*

The key words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are used in the
sense of RFC 2119.

---

## Purpose

Response validation turns the model's raw response for a window into a **validated boundary decision**,
or rejects it. It exists because the model's output cannot be trusted merely because it was returned:
the model may return something malformed, or something that refers to positions that do not exist.
Validation is the guardian that ensures only well-formed decisions that reference genuine units of the
window proceed to merging, and it is where a response that cannot be trusted is turned over to the
recovery path instead of being allowed to corrupt the result.

This document defines exactly which responses are accepted, which are rejected, how an accepted
response is interpreted, and how a rejection is classified.

## Scope

This document governs the single stage that consumes one model response for one window and produces a
validated boundary decision for that window or a rejection. It begins when the model has returned a
raw response for a window and ends when a validated decision exists or the response has been rejected.

## Relationship to the canonical documentation

This document refines, and never contradicts, the frozen canonical documents:

- MODEL.md §7 — the distinction between a raw decision (what the model returns before checking) and a
  validated decision (one confirmed well-formed and to name real units); only validated decisions
  proceed.
- ARCHITECTURE.md §3–4 and PIPELINE.md §3, §7 — validation confirms well-formedness, references only
  real units, and invokes recovery when a response cannot be made valid; malformed output never reaches
  merging.
- CONTRIBUTING_ARCHITECTURE.md §9 — the failure model: a single bad window is recoverable and MUST NOT
  fail the whole job by default.
- SPI.md §4, §8 — the model implementation delivers the raw response and never validates; validation is
  the library's responsibility.

It uses the **boundary-reference convention** defined canonically in
[PROMPT_SPECIFICATION.md](PROMPT_SPECIFICATION.md) §6.

## What this document does NOT specify

- It does not specify the serialization of the response; it specifies the logical content that
  serialization MUST yield, and the acceptance rules over that content. The concrete parsing contract
  is the implementation's internal counterpart to its request output shape
  ([PROMPT_SPECIFICATION.md](PROMPT_SPECIFICATION.md) §8) and MUST be identical to it within an
  implementation.
- It does not specify what happens after a rejection beyond classifying it; retry, degradation, and
  warnings are in [RECOVERY_POLICY.md](RECOVERY_POLICY.md).
- It does not merge decisions ([BOUNDARY_MERGING.md](BOUNDARY_MERGING.md)) or build chunks.

---

## 1. Inputs and outputs

- **Input.** The model's raw response for exactly one window, together with that window's coverage
  (the set of units it presents, by global ordinal). The window is required because validity is
  defined relative to the units the window presented.
- **Output.** Either a **validated boundary decision** for the window — a set of global ordinals,
  deduplicated and sorted in ascending order, each naming a unit present in the window at which a new
  chunk begins — or a **rejection** with a classification (§12).

## 2. The logical content of a response

Per [PROMPT_SPECIFICATION.md](PROMPT_SPECIFICATION.md) §8, a well-formed response encodes exactly an
ordered collection of integer global ordinals — the named boundaries — and nothing else the library
interprets. Validation operates on this logical collection after parsing the response per the
implementation's fixed parsing contract. Everything below is defined over that collection and the
window's presented units.

## 3. Accepted response

A response is **accepted** when all of the following hold:

- **A1 — Parseable.** The response can be parsed, by the implementation's fixed parsing contract, into
  the required logical collection of integer global ordinals (possibly empty).
- **A2 — Integer identifiers.** Every element of the collection is an integer.
- **A3 — In range.** Every element names a unit that the window actually presented.
- **A4 — Not the document's first unit.** No element names the document's first unit, because there can
  be no cut before the first unit (PROMPT_SPECIFICATION.md §6).

An accepted response is **interpreted** as the **set** of the named ordinals: duplicates are collapsed
and the set is sorted in ascending order to form the validated boundary decision (§7, §8). An accepted
response with an empty collection yields an **empty validated decision** (a window with no internal
boundary), which is a normal, valid outcome (§6).

## 4. Rejected response

A response is **rejected** when any of the following hold. Rejection is always window-local and
recoverable (§13); it never fails the whole run by itself.

- **R1 — Malformed.** The response cannot be parsed into the required logical collection (§9).
- **R2 — Missing collection.** The response parses but the required collection is absent (§5).
- **R3 — Non-integer element.** Any element is not an integer.
- **R4 — Out-of-range element.** Any element names a unit the window did not present (§10).
- **R5 — First-unit element.** Any element names the document's first unit (§10).

A rejected response produces no validated decision and is handed to the recovery path
([RECOVERY_POLICY.md](RECOVERY_POLICY.md)).

## 5. Required fields and missing fields

- The **only** required field of a response is the ordered collection of integer global ordinals.
- If that collection is present and parseable, the response satisfies the field requirement, even when
  the collection is empty (an empty collection is the correct answer for a window with no internal
  boundary).
- If that collection is absent or unparseable, the required field is missing and the response is
  rejected (R1/R2).
- No other field is required, and no other field is interpreted (§11).

## 6. Empty and boundary-less responses

- An accepted response whose collection is empty is **not** a failure. It denotes a window in which the
  model found no internal boundary. Its validated decision is the empty set.
- A window that cannot produce a boundary at all (fewer than two units) is handled before the model is
  consulted (WINDOW_PLANNING §11; [RECOVERY_POLICY.md](RECOVERY_POLICY.md)); validation therefore never
  needs to invent a boundary for such a window.

## 7. Duplicate boundaries

- A collection that names the same global ordinal more than once is **accepted** and **normalized**: the
  duplicates are collapsed to a single occurrence. A duplicate conveys the same cut and is therefore not
  ambiguous and not an error.
- Normalization of duplicates is deterministic and changes only multiplicity, never which cuts are
  named.

## 8. Non-monotonic (unordered) boundaries

- The named ordinals form a **set** of cut positions; their order within the response has no meaning.
- A collection whose elements are not in ascending order is **accepted** and **normalized** by sorting
  the set into ascending order. Unordered input is therefore not an error.
- After normalization the validated decision is strictly increasing (a consequence of being a sorted
  set), so downstream stages always receive an ordered, duplicate-free set.

## 9. Malformed responses

- A response is **malformed** when it cannot be parsed into the required logical collection by the
  implementation's fixed parsing contract — for example, the structured result is absent, truncated, or
  not well-formed.
- A malformed response is rejected (R1) and is recoverable.
- Truncation caused by insufficient output budget is a malformed response from validation's standpoint;
  window planning reserves output budget precisely to prevent it (WINDOW_PLANNING §8), and when it
  occurs nonetheless it is handled as a recoverable rejection.

## 10. Out-of-range and non-monotonic distinction

Two failure shapes are frequently confused and are treated differently on purpose:

- **Out of range (R4/R5) — rejection.** An identifier that does not name a unit the window presented,
  or that names the document's first unit, is a reference to a position that does not validly exist.
  Such a response is **rejected as a whole**; the invalid entries are **not** silently dropped, because
  dropping them would be a guess about the model's intent and could corrupt the boundaries. Rejection
  routes the window to recovery instead.
- **Unordered / duplicated — normalization.** Order and multiplicity carry no meaning for a set of
  cuts, so they are normalized rather than rejected (§7, §8).

The guiding rule: **normalize what is unambiguous; reject what is invalid.** Validation never repairs
an invalid reference by inference.

## 11. Unknown information

- Content the parsing contract does not consume — for example prose surrounding a structured result — is
  **ignored**, provided the required collection is present and well-formed (A1).
- Unknown content MUST NOT be interpreted as boundaries or as any other instruction; the library reads
  only the required collection.
- If unknown content prevents the required collection from being parsed unambiguously by the fixed
  parsing contract, the response is malformed and rejected (R1); the parsing contract, not ad-hoc
  inference, decides whether the collection is present.

## 12. Validation guarantees

Every **validated boundary decision** that validation emits guarantees:

- **V1 — Real references.** Every named ordinal names a unit that the window presented.
- **V2 — Legal positions.** No named ordinal is the document's first unit; every cut is a division
  between two units.
- **V3 — Set form.** The decision is a set: sorted ascending, with no duplicates.
- **V4 — Window-scoped.** The decision is expressed purely against global ordinals of the window's
  units, and therefore remains meaningful independently of the window (MODEL.md §7).

These guarantees are what let merging and chunk construction trust the decision without re-checking it.

## 13. Deterministic interpretation

- Given the **same** raw response and the **same** window, validation MUST reach the **same** outcome —
  the same acceptance and the same validated decision, or the same rejection with the same
  classification.
- Interpretation MUST NOT depend on wall-clock time, randomness, iteration order of unordered
  collections, environmental state, or any other response.
- The mapping from an accepted collection to a validated decision (dedupe, sort) is a pure function of
  the collection and the window.
- The non-determinism of the pipeline is confined to the model's production of the raw response
  (PIPELINE.md §8); validation's interpretation of a given response is fully deterministic.

## 14. Error classification

- **Recoverable failures (window-local).** All rejections (R1–R5) are recoverable and localized to the
  one window. They do not fail the run. They are turned over to the recovery path, which decides whether
  to retry the window or to degrade it ([RECOVERY_POLICY.md](RECOVERY_POLICY.md)). Validation itself does
  not retry, does not degrade, and does not record warnings; it only accepts or rejects with a
  classification.
- **Terminal failures.** Validation of a single window's response never, by itself, produces a terminal
  failure. Terminal failures (for example the model failing to return any response at all) arise outside
  validation and are classified in [RECOVERY_POLICY.md](RECOVERY_POLICY.md) §"failure model."

## 15. Future evolution

- The parsing contract and the accepted output shape MAY gain optional, additive fields, provided the
  library continues to interpret only the ordered collection of integer global ordinals and continues to
  ignore everything else (consistent with PROMPT_SPECIFICATION.md §13).
- The set of normalization rules MAY be extended only in the direction of accepting more unambiguous
  inputs (for example, additional forms that unambiguously denote the same set of cuts); it MUST NOT be
  changed to silently repair invalid references, since that would trade correctness for leniency.
- No change may make validation provider-specific or dependent on anything beyond the raw response and
  the window it belongs to.
