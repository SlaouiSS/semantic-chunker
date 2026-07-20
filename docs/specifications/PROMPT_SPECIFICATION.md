# PROMPT_SPECIFICATION.md

*The behavioral and structural contract every production prompt MUST satisfy.*

The key words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are used in the
sense of RFC 2119.

---

## Purpose

Prompt construction turns one window into a single, well-defined request for a boundary decision. It
presents the window's units to the model, tells the model what to decide, and constrains what the
model returns, so that the response can be validated and consumed. This document defines the
behavioral contract and the required structure of that request: what information it MUST carry, in
what order, how units and boundaries are referenced, what it MUST NOT carry, and what shape the model
is required to answer in.

This document also defines, canonically, **how a boundary is referenced** — the convention every
later stage uses. Because the request is what conveys that convention to the model, the convention is
owned here and referenced elsewhere.

## Scope

This document governs the single stage that transforms a window into a request. It begins after
window planning has produced a window and ends when a request has been produced for that window. It
covers the request's required content, structure, and output contract, not its wording.

## Relationship to the canonical documentation

This document refines, and never contradicts, the frozen canonical documents:

- PROMPT.md — the prompt is an internal, library-owned asset; it is fixed by the library, not the
  caller; it is provider-neutral, semantically neutral, deterministic in structure, and has clear
  output expectations; its wording MAY evolve while its behavior stays stable. Its responsibilities
  are to describe the task, provide the window's content, request boundary decisions, constrain the
  output, and reduce ambiguity.
- MODEL.md §4, §7 — units and their textual content; boundary decisions expressed against stable
  identities.
- CONTRIBUTING_ARCHITECTURE.md §6.2.2 (the request carries the rendered prompt, an optional
  response-shape hint, and generation hints), §7.2.5–§7.2.6 (table and non-text unit representation),
  §7.3.1 (global-ordinal stability).
- PIPELINE.md §3 (Prompt Construction responsibilities and guarantees) and SPI.md §4 (the model
  receives a complete request and does not compose it).

Where those documents state a requirement, it is authoritative here.

## What this document does NOT specify

- It does not contain the prompt, any prompt wording, any template, or any example text. Wording is an
  internal implementation asset (PROMPT.md §1) and is out of scope.
- It does not specify a concrete serialization for the output (for example a particular structured
  notation); it specifies the *logical* output shape and the contract the serialization MUST honor.
- It does not specify how the model is reached, nor any provider behavior.
- It does not specify how the response is validated ([RESPONSE_VALIDATION.md](RESPONSE_VALIDATION.md))
  or interpreted after validation.

---

## 1. Prompt lifecycle

Consistent with PROMPT.md §4, a request is a transient artifact of a single execution:

- It is constructed internally, for exactly one window, from the library's own fixed instruction and
  that window's content. Nothing exists before this construction, and the caller has no part in it.
- It is carried to the model and presented for a response.
- Once the response is obtained, the request has served its purpose.
- It is then discarded. It is never retained, never part of the result, and never visible in the
  library's output.

A request is built from exactly one window and concerns exactly that window. Constructing a request
for a window MUST NOT require or consult any other window, the whole document beyond the window, or
any prior response.

## 2. Responsibilities

For its one window, a request MUST:

1. **Describe the task** — make clear that the model is to identify where one coherent, self-contained
   section of the presented material ends and the next begins.
2. **Provide the window's content** — present the window's units in a form the model can reason about.
3. **Request boundary decisions** — direct the model to return the boundaries, expressed in the terms
   this document defines (§6, §8).
4. **Constrain the output** — require the response to take the shape the validation stage expects, and
   nothing more (§8).
5. **Reduce ambiguity** — leave as little as possible open to interpretation about the task, the
   decision, and the answer's shape.

A request MUST NOT take on any responsibility beyond eliciting a boundary decision for its window
(PROMPT.md §9): it performs no extraction, window planning, merging, validation, chunk construction,
orchestration, or business judgment.

## 3. Required information

A request MUST contain, and MUST contain only, the following information:

- A description of the boundary-identification task (§2.1), fixed by the library.
- The window's units, each with its **global ordinal** and its **textual representation** (§5),
  presented in document order (§4).
- A statement of the **boundary-reference convention** (§6): that a boundary is named by the global
  ordinal of the unit at which a new section begins.
- A statement of the **required output shape** (§8), including that an empty result is the correct
  answer when the window contains no internal boundary.

## 4. Required ordering

- The window's units MUST be presented in ascending document order, which is the ascending order of
  their global ordinals (MODEL.md §4; CONTRIBUTING_ARCHITECTURE.md §7.2).
- The order MUST be exactly the document order of the window's coverage; no reordering, grouping, or
  omission of units within the window is permitted.
- Presentation order MUST be deterministic and identical for identical windows (§10).

## 5. How units are represented

Each unit in the window MUST be presented as an association of its **global ordinal** with its
**textual representation**, so that the model can read the unit and can name it as a boundary by its
ordinal.

The textual representation of a unit is:

- For a unit that carries textual content, that text.
- For a table, its canonical serialization, which is the same serialization retained in the model
  (CONTRIBUTING_ARCHITECTURE.md §7.2.5). The structured cell data is not presented; the model sees the
  canonical serialization, and that is also what a resulting chunk carries.
- For a unit that carries no textual content (for example an image with no caption, or a page break
  with no text), an explicit empty representation. Such a unit contributes no textual signal and
  therefore cannot influence the boundary decision (CONTRIBUTING_ARCHITECTURE.md §7.2.6), but it
  remains present in the sequence and remains addressable by its global ordinal.

Every unit of the window MUST be presented — including units with no textual content — so that the
window's index space is complete and every unit remains a nameable boundary position. The textual
representation used here MUST be the same representation whose size window planning estimated
([WINDOW_PLANNING.md](WINDOW_PLANNING.md) §1), so that sizing and presentation agree.

The representation MUST NOT include a unit's provenance internals (page reference, character
offsets) or its metadata; those are not needed for the decision and could bias it (§7).

## 6. How boundaries are referenced (canonical convention)

This convention is defined here once and referenced by every later stage.

- The units of the document partition into **chunks**: contiguous, non-overlapping runs that cover the
  document in order.
- A **boundary** (equivalently, a **cut**) is the position immediately before a unit. Naming a unit by
  its global ordinal as a boundary means: *a new chunk begins at that unit; the unit is the first unit
  of the following chunk.*
- The document's **first unit is never a boundary**: the first chunk always begins at the first unit
  implicitly, so there is no cut before it.
- Within a window, the model MAY name as a boundary any unit that the window presents (including the
  window's first unit, when that unit is not the document's first unit); such a named unit denotes the
  cut immediately before it. The same global ordinal denotes the same cut in every window that
  presents that unit, which is what allows the boundary merger to reconcile decisions
  ([BOUNDARY_MERGING.md](BOUNDARY_MERGING.md)).

The model is asked to decide boundaries by the meaning of the content, referencing them by global
ordinal; it is never asked to decide anything by position within the window or by any transient
property.

## 7. Allowed and forbidden information

**Allowed** in a request:

- The fixed task description and the fixed statement of the boundary convention and output shape.
- The window's units in document order, each as ordinal-plus-textual-representation (§5).

**Forbidden** in a request:

- Any provider-specific phrasing, instruction, or trick tuned to one model or provider (PROMPT.md §5).
- Any caller-supplied instruction or data beyond the document content itself.
- Any content outside the window: other windows, the rest of the document, or global document facts.
- Any prior response, or any information about retries or recovery.
- Provenance internals (page, offsets) and metadata maps.
- Any instruction that biases the model toward a particular substantive interpretation of the content;
  the request MUST remain semantically neutral, asking only *where* meaning divides, never steering
  *what* the content is taken to mean (PROMPT.md §8).
- Any request for output beyond the boundary decision (§8).

## 8. Required model instructions and required output shape

**Required instructions (behavioral, not wording).** The request MUST instruct the model to:

- read the presented units;
- identify the global ordinals of the units at which a new coherent, self-contained section begins,
  according to the meaning of the content;
- return exactly those ordinals in the required output shape;
- return an empty result when there is no internal boundary in the window;
- return only the required output and nothing else that the library will interpret.

**Required output shape (logical).** The response MUST be required to encode exactly an ordered
collection of integer global ordinals — the named boundaries — and nothing else that the library
interprets. The collection MAY be empty. The collection carries no other fields, weights, rationales,
or annotations that the library reads.

**Serialization.** The concrete serialization of this collection is an internal detail of the
implementation's request/response contract and is not fixed by this document (consistent with
PROMPT.md §1). Whatever serialization an implementation elicits, it MUST:

- encode exactly the ordered collection of integer global ordinals and nothing the library interprets;
- be the same shape the validation stage parses ([RESPONSE_VALIDATION.md](RESPONSE_VALIDATION.md)), so
  that the request's stated output shape and the validator's accepted shape are identical within an
  implementation;
- be machine-parseable without recourse to the model's prose.

The request MAY additionally carry the optional response-shape hint permitted by
CONTRIBUTING_ARCHITECTURE.md §6.2.2 to help a model that supports structured output; the request MUST
still fully state the required output shape in its own terms, so that it works for a model that
ignores the hint. The generation hints (a low temperature and the output-token budget) travel with the
request as defined by §6.2.2 and are not part of the prompt's content.

## 9. Provider independence

- A request MUST express its intent in terms meaningful to any capable model, not in terms that depend
  on the behavior of a particular provider (PROMPT.md §5, §8).
- The same single request form MUST serve every model behind the model abstraction; there is no
  per-provider request.
- The response-shape hint (§8) is a provider-neutral capability of the request, not provider-specific
  behavior.

## 10. Deterministic formatting

- For a given window — the same units, in the same order, with the same global ordinals and the same
  textual representations — a request MUST be constructed identically every time it is built. The
  request content MUST be a pure function of the window and the library's fixed instruction.
- A request MUST NOT depend on wall-clock time, randomness, iteration order of unordered collections,
  environmental state, or any prior request or response.
- The only element of the overall interaction that is permitted to vary is the model's *answer*; the
  *question* is fixed for a given window (PIPELINE.md §8).

## 11. Prompt invariants

Every production request MUST satisfy the invariants of PROMPT.md §8, restated here as they apply to
this contract:

- **One responsibility** — it obtains a boundary decision for one window and does nothing else.
- **Semantic neutrality** — it asks *where* the content divides, never biasing *what* it means.
- **Provider independence** — it depends on no provider's quirks (§9).
- **Deterministic structure** — it is built identically for identical windows (§10).
- **Clear output expectations** — it establishes the exact logical output shape the validator accepts
  (§8).

Additionally, structural invariants specific to this specification:

- **Complete index space** — every unit of the window is presented, each under its true global ordinal
  (§5), so any unit of the window is a nameable boundary.
- **Document order** — units are presented in ascending document order (§4).
- **Boundary convention conveyed** — the request states the boundary-reference convention (§6) so the
  model answers in the terms the library expects.

## 12. Failure conditions

- Constructing a request for a valid window is a **total** operation: given a window produced by
  window planning, a request is always produced. A unit with no textual content yields an explicit
  empty representation (§5) rather than a failure.
- Prompt construction therefore raises no recoverable or terminal failure of its own. Failures arise
  only when the request is executed against the model or when its response is validated.
- An over-budget window (WINDOW_PLANNING §12) is never presented to the model; the recovery path
  handles it, so no request is constructed for it.

## 13. Future evolution

- The prompt's wording MAY be refined continuously to elicit better boundary decisions, provided every
  invariant in §11 continues to hold and the observable behavior of the library stays stable
  (PROMPT.md §6, §10).
- The output shape MAY gain optional additional fields only additively, and only if the library either
  ignores them or treats them as non-load-bearing; the boundary encoding (an ordered collection of
  integer global ordinals) MUST remain the sole information the library interprets, so that validation
  and every later stage are unaffected.
- No change may introduce provider-specific behavior, caller-supplied prompt content, or any new
  public concept.
