# BOUNDARY_MERGING.md

*The behavioral specification of boundary merging.*

The key words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are used in the
sense of RFC 2119.

---

## Purpose

Boundary merging reconciles the validated per-window boundary decisions into a single coherent set of
boundaries over the whole document. It exists because a large document is processed as several
overlapping windows, so the decisions arrive in separate, overlapping pieces, and adjacent pieces may
disagree about the shared region. Merging restores the document to a single whole, so that the fact of
windowing leaves no trace in the result. It is the most delicate stage in the pipeline, and it depends
absolutely on the stable identity of units.

This document defines exactly how per-window decisions are combined, how overlap disagreements are
resolved, and what guarantees the merged result provides.

## Scope

This document governs the single stage that consumes the validated per-window decisions and the windows
that produced them, and produces one set of boundaries over the whole document. It begins once every
window that required a decision has a validated decision (or an empty decision from degradation) and ends
when the single merged boundary set exists.

## Relationship to the canonical documentation

This document refines, and never contradicts, the frozen canonical documents:

- MODEL.md §5–§7 — stable identity; boundary decisions expressed against identities and meaningful
  independently of their window; merging reconciles overlapping windows using identity.
- ARCHITECTURE.md §3–4, §9 — merging resolves overlap disagreements, depends on stable identity, and
  leaves no artifact of windowing in its output.
- CONTRIBUTING_ARCHITECTURE.md §7.3.1–§7.3.3 — global-ordinal stability across windows is the invariant
  merging relies on; overlapping windows that disagree, a boundary inside the overlap, and a window that
  returns zero boundaries are the cases merging must handle correctly.
- PIPELINE.md §3 — merging's responsibility and guarantees.

It uses the **boundary-reference convention** defined canonically in
[PROMPT_SPECIFICATION.md](PROMPT_SPECIFICATION.md) §6, the **overlap guarantees** of
[WINDOW_PLANNING.md](WINDOW_PLANNING.md) §9, and the **validation guarantees** of
[RESPONSE_VALIDATION.md](RESPONSE_VALIDATION.md) §12.

## What this document does NOT specify

- It does not build chunks from the merged boundaries; that is
  [CHUNK_CONSTRUCTION.md](CHUNK_CONSTRUCTION.md).
- It does not plan windows, construct prompts, execute the model, or validate responses.
- It does not decide how a window becomes degraded; a degraded window enters merging as an empty
  decision (see [RECOVERY_POLICY.md](RECOVERY_POLICY.md)), and merging treats it uniformly.

---

## 1. Definitions

- **Candidate boundary position (position "before u").** The place immediately before a unit `u`,
  meaning "a new chunk begins at `u`," where `u` is not the document's first unit
  ([PROMPT_SPECIFICATION.md](PROMPT_SPECIFICATION.md) §6). Each such position is named by the global
  ordinal of `u`.
- **Covering window of a position.** A window **covers** the position before `u` when the window
  presents `u`. (A window that presents `u` can name `u` as a boundary; whether it also presents the
  unit before `u` affects only how much left context it has, captured by centeredness below.)
- **Reported at a position.** A window **reports** a cut at the position before `u` when `u` is a member
  of that window's validated boundary decision.
- **Left context / right context of a position within a window.** For the position before `u` in a
  window whose coverage runs from unit `a` to unit `b` in document order: the **left context** is the
  number of the window's units that lie before `u` (the units from `a` up to and including the unit
  immediately before `u`, when present in the window); the **right context** is the number of the
  window's units from `u` through `b`. A window that presents `u` as its first unit has left context
  zero.
- **Centeredness of a position within a window.** The smaller of that position's left context and right
  context within the window. Greater centeredness means the position sits farther from both of the
  window's edges, i.e., is judged with more balanced context on both sides — the condition overlap
  exists to create ([WINDOW_PLANNING.md](WINDOW_PLANNING.md) §9).

## 2. Inputs

Boundary merging consumes:

1. The **ordered list of windows** produced by window planning, each with its coverage (its units by
   global ordinal) and its ordering.
2. The **validated boundary decision for each window** — for a degraded window, the empty decision
   ([RECOVERY_POLICY.md](RECOVERY_POLICY.md)).
3. The document's units in document order (to know adjacency and which unit is first).

It consumes nothing else.

## 3. Outputs

Boundary merging produces a single **merged boundary set**: a set of global ordinals, sorted in
ascending order, with no duplicates, each naming a unit at which a new chunk begins over the whole
document. The merged set carries no record of windows and no artifact of the windowing.

## 4. Overlap reconciliation — conflict resolution policy

The following policy is applied to **every** candidate boundary position that is reported by at least
one covering window. It resolves duplicates, disagreements, and overlap uniformly and deterministically.

For a candidate position `p` (the position before some unit `u`):

1. Determine the set of **covering windows** of `p` — the windows that present `u`.
2. Among those covering windows, determine the **authoritative window** of `p`: the covering window in
   which `p` has the **greatest centeredness** (§1). If two or more covering windows tie on centeredness,
   the authoritative window is the one whose first unit is earliest in document order (the earliest
   window). This tie-break is total and deterministic.
3. The merged set contains a cut at `p` **if and only if** the authoritative window **reports** a cut at
   `p` (i.e., `u` is in that window's validated decision).

The rationale is the same one that justifies overlap (MODEL.md §6; WINDOW_PLANNING §9): the window in
which a position has the most balanced context on both sides is the best-informed judge of whether a
boundary belongs there, so its decision governs. A less-central window's opinion — whether it adds or
omits a cut — does not override the authoritative window.

## 5. Conflicting boundaries

A conflict is a candidate position that some covering window reports and another covering window does
not. It is resolved entirely by §4: the authoritative (most-central) window decides. If the authoritative
window reports the cut, it is kept; if it does not, the cut is dropped even though a less-central window
reported it.

## 6. Duplicate boundaries

Two windows reporting a cut at the same candidate position name the **same** global ordinal (identity is
stable across windows), so they denote the same position. §4 evaluates each position once and yields a
single decision for it, so the merged set never contains a duplicate. Duplicates therefore cannot appear
in the output and require no separate handling beyond §4.

## 7. Window disagreement and the required cases

The following cases, called out by CONTRIBUTING_ARCHITECTURE.md §7.3.3, are handled by §4:

- **Overlapping windows that disagree.** Resolved by the authoritative-window rule (§4, §5).
- **A boundary that falls inside the overlap.** Such a position is covered by two windows; the more
  central one is authoritative and decides (§4). The two-sided-context guarantee (WINDOW_PLANNING §9,
  O2) ensures that, when windows are large enough, a well-contextualized authoritative window exists for
  every interior position.
- **A window that returns zero boundaries.** A window with an empty validated decision reports no cut at
  any position. It still participates as a covering window: where it is authoritative for a position, its
  silence means no cut there. This is exactly how a degraded window (empty decision) behaves, so
  degradation and a genuine "no boundaries here" answer are treated identically and deterministically.

## 8. Ordering guarantees

- The merged set is sorted in ascending order of global ordinal.
- Because global ordinals increase with document order, the merged order is document order.
- The set is strictly increasing and free of duplicates (§6).

## 9. Global-ordinal guarantees

- Merging operates purely on global ordinals; it never renumbers, reorders, or reassigns any unit's
  identity (CONTRIBUTING_ARCHITECTURE.md §7.3.1).
- A candidate position's identity is the global ordinal of the unit it precedes, and that identity is the
  same in every window, which is the sole reason decisions from different windows can be compared and
  reconciled (CONTRIBUTING_ARCHITECTURE.md §7.3.2). Merging depends on this invariant absolutely; if it
  did not hold, merging would combine decisions that only appeared to refer to the same place.

## 10. Merge invariants

The following MUST hold of every merged boundary set:

- **M1 — Legality.** Every element names a real unit that is not the document's first unit (inherited
  from validation, [RESPONSE_VALIDATION.md](RESPONSE_VALIDATION.md) §12).
- **M2 — Set form.** The set is sorted ascending and free of duplicates.
- **M3 — No windowing artifact.** The set records nothing about windows; the fact that the document was
  divided leaves no trace.
- **M4 — Decided once.** Each candidate position is decided exactly once, by its authoritative window
  (§4), so the result is independent of the order in which positions are considered.
- **M5 — Subset of reported positions.** Every element was reported by at least one covering window and
  confirmed by that position's authoritative window; merging never invents a boundary that no window
  reported, and never keeps one the authoritative window rejected.

## 11. Determinism

- Given the same windows and the same validated per-window decisions, merging MUST produce the identical
  merged boundary set.
- The result MUST NOT depend on the order in which candidate positions are examined (M4), on wall-clock
  time, on randomness, on iteration order of unordered collections, or on any environmental state.
- All tie-breaking is total and deterministic (§4, step 2).
- Merging contains no non-determinism; the only non-deterministic element of the pipeline is the model's
  production of the raw responses, which is already resolved before merging runs (PIPELINE.md §8).

## 12. Failure conditions

- Merging is a **total** operation over inputs that satisfy the validation guarantees
  ([RESPONSE_VALIDATION.md](RESPONSE_VALIDATION.md) §12) and the window guarantees
  ([WINDOW_PLANNING.md](WINDOW_PLANNING.md) §11): it always produces a merged set and raises no
  recoverable failure of its own.
- If an input decision referenced a unit not present in its window, or named the document's first unit,
  the validation guarantees would have been violated upstream. Merging MUST be able to assume those
  guarantees; encountering such an input indicates an internal invariant violation and is a **terminal**
  failure (a fundamental defect, not a recoverable, model-caused condition), consistent with
  CONTRIBUTING_ARCHITECTURE.md §9.4.
- Merging performs no model interaction and therefore has no content-level recoverable failures.

## 13. Future evolution

- The conflict-resolution policy MAY be refined only in ways that preserve determinism, total
  tie-breaking, and the principle that the better-contextualized window governs; any refinement MUST
  continue to satisfy every invariant in §10 and the required cases in §7.
- Merging MUST remain a pure function of the windows and their validated decisions; no change may make it
  provider-specific or dependent on anything else.
