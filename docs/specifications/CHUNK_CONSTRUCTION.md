# CHUNK_CONSTRUCTION.md

*The behavioral specification of chunk construction.*

The key words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are used in the
sense of RFC 2119.

---

## Purpose

Chunk construction builds the final semantic chunks by applying the merged boundaries to the document's
units. Each chunk is the contiguous run of units lying between one boundary and the next. Chunks are the
library's primary output — the material downstream systems embed, store, and retrieve — and this stage is
where the merged conclusions become that output.

This document defines exactly how the merged boundary set partitions the units into chunks, and the
guarantees the resulting chunks provide.

## Scope

This document governs the single stage that consumes the document's units and the merged boundary set and
produces the ordered semantic chunks. It begins once the merged boundary set exists and ends when the
ordered chunks exist. It does not cover result assembly (gathering chunks with warnings and metadata),
which is a separate, mechanical step outside this specification's algorithmic concern.

## Relationship to the canonical documentation

This document refines, and never contradicts, the frozen canonical documents:

- MODEL.md §8, §11 — a chunk is a contiguous section produced by applying merged boundaries to units; a
  chunk is defined by the units it comprises, not by an independent copy; chunks are coherent,
  self-contained, ordered, and traceable, and inherit their units' provenance.
- ARCHITECTURE.md §3 (Semantic chunks) and PIPELINE.md §3 (Chunk Construction) — each chunk is a
  contiguous run of units in document order carrying the provenance of those units, and the chunks
  together cover the document in sequence.
- CONTRIBUTING_ARCHITECTURE.md §7.2–§7.3 — units are the index space; provenance and global ordinals are
  preserved.

It uses the **boundary-reference convention** defined canonically in
[PROMPT_SPECIFICATION.md](PROMPT_SPECIFICATION.md) §6 and the **merge guarantees** of
[BOUNDARY_MERGING.md](BOUNDARY_MERGING.md) §10.

## What this document does NOT specify

- It does not specify the shape of the final result object, nor how warnings, processing information, or
  execution metadata are attached; those are defined by the domain model (MODEL.md §9) and
  [RECOVERY_POLICY.md](RECOVERY_POLICY.md).
- It does not merge boundaries, validate responses, or plan windows.
- It does not define any transformation of unit content; chunks reference units unchanged.

---

## 1. Inputs

Chunk construction consumes:

1. The **prepared document's units**, in document order, with their stable global ordinals and their
   provenance and metadata.
2. The **merged boundary set** — a sorted, duplicate-free set of global ordinals, each naming a unit at
   which a new chunk begins ([BOUNDARY_MERGING.md](BOUNDARY_MERGING.md) §3, §10).

It consumes nothing else.

## 2. Outputs

Chunk construction produces an **ordered list of semantic chunks**. Each chunk references a contiguous
run of the document's units, in document order. The chunks in order partition all of the document's
units.

## 3. Chunk definition and boundary application

- The set of positions at which a new chunk begins is exactly `{ the document's first unit } ∪ { the
  merged boundaries }`. The first unit always begins the first chunk implicitly
  ([PROMPT_SPECIFICATION.md](PROMPT_SPECIFICATION.md) §6); each merged boundary begins a further chunk.
- Applying these start positions partitions the units into maximal contiguous runs: a chunk begins at each
  start position and extends, in document order, up to but not including the next start position; the last
  chunk extends to the document's last unit.
- Each chunk is therefore the contiguous run of units from one start position up to the unit immediately
  before the next boundary (or the last unit, for the final chunk).

Boundary application is a pure partition of the ordered units; it neither adds nor removes units and does
not alter any unit.

## 4. Chunk ordering

- Chunks are produced and listed in ascending document order: the chunk beginning at the first unit is
  first, and each subsequent chunk begins at the next boundary in ascending global-ordinal order.
- The order among chunks is the order of the units they are composed from (MODEL.md §8, §12). This order
  MUST be preserved; chunks are never reordered.

## 5. Unit preservation

- A chunk references the **same** unit instances the prepared document established; it copies no content
  and creates no independent representation of a unit (MODEL.md §11).
- The units a chunk references keep their global ordinals and their relative order unchanged.
- No unit is duplicated across chunks, and no unit is omitted from every chunk; each unit belongs to
  exactly one chunk (§9).

## 6. Provenance preservation

- Every unit carries its provenance unchanged into the chunk that references it (MODEL.md §10).
- A chunk is therefore traceable to the exact material of the source through the provenance of its units;
  chunk construction MUST NOT strip, alter, or regenerate any provenance.

## 7. Metadata preservation

- Each unit's metadata is carried unchanged into the chunk that references it, exactly as the unit holds
  it. Chunk construction reads no meaning from metadata and MUST NOT alter it.
- Chunk construction introduces no chunk-level metadata beyond what the domain model already defines; a
  chunk is defined by the units it comprises (MODEL.md §11). (The domain model does not attribute
  independent metadata to a chunk; this stage adds none.)

## 8. Empty chunk policy

- A chunk MUST NEVER be empty: every chunk contains at least one unit.
- This is guaranteed by construction: start positions are distinct units in ascending order, and the
  document's first unit is always a start position, so every partition segment spans at least one unit.
  Because merged boundaries are a set (no duplicates) and none names the first unit
  ([BOUNDARY_MERGING.md](BOUNDARY_MERGING.md) §10), no two start positions coincide and no zero-length
  segment can arise.

## 9. Coverage and partition guarantees

- **Total coverage.** Every unit of the document belongs to exactly one chunk.
- **No gaps.** The chunks in order cover the units contiguously, with no unit left out between chunks.
- **No overlap.** No unit belongs to more than one chunk.
- Together these make the chunks a true partition of the document's units, in order.

## 10. Single-chunk behavior

- When the merged boundary set is empty, the whole document is one chunk containing all units in order.
- This is the normal outcome for a document the model found internally coherent, and for a document small
  enough that no internal boundary was produced.

## 11. Final-chunk behavior

- The final chunk begins at the last boundary (or at the first unit when there is no boundary) and extends
  to the document's last unit.
- The final chunk always covers the tail of the document; there is never a unit after the final chunk.

## 12. Empty-document behavior

- When the document has no units, chunk construction produces the **empty list of chunks**. It produces no
  empty chunk (§8) and no placeholder.
- This is the natural result of an empty prepared document (WINDOW_PLANNING §11) and yields a result whose
  chunk list is empty.

## 13. Determinism

- Given the same units and the same merged boundary set, chunk construction MUST produce the identical
  ordered list of chunks — the same number of chunks, each composed of the same units in the same order.
- It MUST NOT depend on wall-clock time, randomness, iteration order of unordered collections,
  environmental state, or any prior run.
- Chunk construction is a pure function of its two inputs; it contains no non-determinism.

## 14. Failure conditions

- Chunk construction is a **total** operation over inputs that satisfy the merge guarantees
  ([BOUNDARY_MERGING.md](BOUNDARY_MERGING.md) §10): it always produces the chunks and raises no
  recoverable failure of its own.
- If a merged boundary referenced a unit that does not exist, named the document's first unit, or the set
  were not ordered and duplicate-free, the merge guarantees would have been violated upstream. Chunk
  construction MUST be able to assume those guarantees; encountering such an input indicates an internal
  invariant violation and is a **terminal** failure (a fundamental defect, not a recoverable, model-caused
  condition), consistent with CONTRIBUTING_ARCHITECTURE.md §9.4.
- Chunk construction performs no model interaction and therefore has no content-level recoverable
  failures.

## 15. Future evolution

- Chunk construction is fixed by the domain model, which is frozen. Its rules — contiguous partition,
  preservation of units, provenance, order, and the no-empty-chunk guarantee — are stable and are not
  expected to change.
- Any future change MUST preserve the partition guarantees (§9), the empty-chunk prohibition (§8), and the
  preservation guarantees (§5–§7), and MUST NOT introduce provider-specific behavior or a new public
  concept.
