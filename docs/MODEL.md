# MODEL.md

*The conceptual domain model.*

This document defines the domain model of the `semantic-chunker` library: every
conceptual object the library manipulates, the meaning of each, and the
relationships and invariants that bind them together. It is the canonical
reference for what the library's concepts *are*, independent of how they are
implemented.

It is not an implementation guide, an API reference, a class design, or a
serialization specification. It names no classes, no fields, no methods, and no
packages. It says nothing about persistence, transport, or encoding. Its concern
is exclusively the conceptual model — the shared vocabulary and structure that
any correct implementation must reflect. It is written so that two independent
teams could each implement the library and arrive at conceptually compatible
models.

The document assumes the architecture already established: a single linear
pipeline that carries a raw document through extraction, normalization, window
planning, prompt orchestration, model execution, response validation, and
boundary merging to a set of semantic chunks and a result. The concepts defined
here are the representations that flow through that pipeline. The architectural
invariants are defined in the architecture document; this document defines the
invariants of the model itself.

---

## Table of Contents

1. [Modeling Principles](#1-modeling-principles)
2. [Domain Overview](#2-domain-overview)
3. [Prepared Document](#3-prepared-document)
4. [Document Units](#4-document-units)
5. [Stable Identity](#5-stable-identity)
6. [Windows](#6-windows)
7. [Boundary Decisions](#7-boundary-decisions)
8. [Semantic Chunks](#8-semantic-chunks)
9. [Chunking Result](#9-chunking-result)
10. [Provenance](#10-provenance)
11. [Model Relationships](#11-model-relationships)
12. [Model Invariants](#12-model-invariants)
13. [What the Model Deliberately Does Not Represent](#13-what-the-model-deliberately-does-not-represent)

---

## 1. Modeling Principles

The domain model is governed by a small set of principles. They are not
stylistic preferences; each exists because the correctness of the pipeline
depends on it. Understanding why they hold is the key to understanding every
concept that follows.

**Immutable domain objects.** Every concept in the model, once created, does not
change. A prepared document, a unit, a boundary decision, a chunk, a result —
each is a fixed value from the moment it exists. This principle exists because the
pipeline passes information from one stage to the next as a succession of
representations, and the only honest way for a stage to influence a later one is
by producing a new value rather than mutating a shared one. Immutability makes the
flow of information explicit and removes the possibility that one stage silently
alters what another is relying upon.

**Explicit representations.** Each meaningful thing the library works with is a
named concept with a defined purpose, not an incidental structure that happens to
carry data. The model prefers to make its concepts visible and distinct rather
than to fold several ideas into one convenient container. This exists so that the
model can be reasoned about precisely: when every representation stands for
exactly one thing, there is no ambiguity about what a value means or what may be
assumed of it.

**Stable identities.** The addressable units of a document carry identities that
are assigned once and never change for the life of a run. This is the single most
important principle in the model, because the entire process of dividing a
document, reasoning about it in pieces, and reassembling the conclusions depends
on being able to refer to the same unit unambiguously at every stage. It is
treated at length in its own section.

**Ordered data.** The units of a document have a definite order that reflects the
order of the document itself, and that order is preserved everywhere. This exists
because a document is inherently sequential — meaning depends on what precedes and
follows — and because the final chunks must reflect the document's true order.
Order is not metadata about the content; it is part of what the content means.

**Traceability.** Every concept that ends up in the result can be traced back to
where it came from in the original document. A chunk knows which units compose it;
a unit knows where in the source it originated. This exists because the systems
that consume chunks frequently need to point back to the source — to cite it, to
display it, to justify an answer — and a model that discards the path back to the
origin forecloses an entire class of legitimate uses.

**Deterministic interpretation.** Given the same values, the meaning of the model
is fixed. The concepts do not depend on hidden context, on the order in which
they happen to be examined, or on anything outside themselves to be understood. A
boundary decision means the same thing wherever it is read; a chunk composes the
same units however it is inspected. This exists so that the pipeline's stages can
rely on one another's outputs without ambiguity, and so that two implementations
interpreting the same values will agree on what they mean.

**Minimal concepts.** The model contains only the concepts the architecture
requires, and no more. Each concept earns its place by being necessary to the
pipeline; none exists for the convenience of an implementation. This exists
because every additional concept is something that must be understood, kept
consistent, and maintained for the life of the project, and a model that grows
concepts freely becomes a model no one can hold in mind.

**No duplicated information.** A given fact is represented in exactly one place in
the model, and other concepts refer to it rather than restating it. Content
belongs to units; concepts that operate over content refer to those units rather
than copying them. This exists because duplicated information is information that
can disagree with itself, and a model in which the same fact is stated twice is a
model that can become internally inconsistent. Referring to a single source of
truth keeps the model coherent by construction.

---

## 2. Domain Overview

The library manipulates a small number of concepts, arranged as a progression.
Each is derived from the one before it, and together they trace the journey from
an opaque input to a finished, traceable output.

**Document.** The raw input: an opaque file supplied by the caller. It is not yet
a modeled concept in any detail; it is the material from which the model's first
real concept is produced.

**Prepared document.** The normalized representation of the document, independent
of how it was extracted. It is the central concept of the model and the input to
all semantic-chunking work. It owns the ordered document units.

**Document units.** The addressable pieces that a prepared document is composed
of — its headings, paragraphs, list items, tables, images, and page breaks,
rendered as uniform conceptual units. They are the atoms of the model: the
smallest things the library reasons about and refers to, and the things
boundaries are expressed in terms of.

**Windows.** Temporary processing views over the units, created when a document is
too large to be considered at once. A window is a way of looking at a contiguous
run of units; it is not a new piece of the document and it holds no content of its
own.

**Boundary decisions.** Conclusions about where the document should be divided,
expressed in terms of the units' stable identities. They are the output of
reasoning over windows, and they are meaningful independently of the windows that
produced them.

**Semantic chunks.** The contiguous, self-contained sections of the document that
result from applying the merged boundary decisions to the units. They are the
library's primary output.

**Chunking result.** The complete output of a run: the chunks together with the
account of how they were produced. It is the form in which everything the caller
needs leaves the library.

These concepts relate as a chain of derivation. A document becomes a prepared
document; the prepared document owns document units; windows are views over those
units; boundary decisions are conclusions expressed against those units'
identities; chunks are composed from those units according to the merged
boundaries; and the result contains those chunks alongside the record of the run.
At no point does a later concept replace an earlier one; each is derived from what
came before, and the units established at the beginning remain the common
reference through which every later concept is understood.

---

## 3. Prepared Document

The prepared document is the central concept of the model. Everything the library
does to perform semantic chunking is done in terms of it, and every later concept
is derived from it. If the model has a heart, this is it.

Its purpose is to be the single, normalized representation of a document that the
semantic-chunking pipeline consumes. Extraction produces content whose exact
shape and richness differ from one extraction technology to another; the prepared
document is the form into which all of that variation is resolved. It represents
a document as the library understands documents, in terms the library defines for
itself, so that no part of the pipeline beyond normalization needs to know
anything about how the document was obtained.

Every later stage depends on it because it is the point at which the pipeline
becomes self-contained. Before the prepared document exists, the process is
concerned with interpreting an external file through an external technology. Once
it exists, the process is concerned only with the library's own concepts. Window
planning divides it, prompt orchestration presents portions of it, boundary
decisions are expressed against its units, and chunks are composed from its units.
Nothing downstream reaches back past the prepared document to the original file or
the extraction technology; the prepared document is the whole of what they know.

It represents a normalized document independent of extraction technology. This
independence is its defining characteristic and its reason for existing. Two
different extraction technologies applied to the same document should yield
prepared documents that the pipeline treats in the same way, differing only to
the extent that the technologies genuinely recovered different content or
structure. The prepared document absorbs the differences between technologies so
that the rest of the model does not have to.

Its essential characteristics are these. It is immutable: once normalization has
produced it, it does not change for the remainder of the run. It is ordered: the
content it represents carries the sequence of the original document. It is
self-contained: it holds everything the pipeline needs and refers to nothing
outside itself. And it is the owner of the document units — the units do not exist
independently of a prepared document, but as its constituent parts, established
when it is created and belonging to it thereafter.

---

## 4. Document Units

A document unit is the atom of the model: the smallest addressable piece of a
document that the library reasons about, refers to, and expresses conclusions in
terms of. A prepared document is, in essence, an ordered collection of units, and
almost every other concept in the model is defined by reference to them. This is
the most detailed section of the document because the unit is the concept on which
all the others rest.

Every unit possesses five essential qualities, and each exists for a specific
reason.

**Stable identity.** Every unit has an identity that distinguishes it from every
other unit and that never changes for the life of the run. This is what allows any
stage of the pipeline to refer to a specific unit unambiguously, and it is the
foundation on which windowing, boundary decisions, and merging all depend.
Because it is so central, it is treated in full in the next section.

**Stable order.** Every unit has a definite position in the sequence of the
document, and that position never changes. Order exists because a document is
sequential and its meaning depends on sequence, and because the final chunks must
reproduce the document's true order. A unit's order is as intrinsic to it as its
content; the two together are what make it a faithful representation of a piece of
the document.

**Provenance.** Every unit records where it came from in the original document —
its place in the source, expressed in the terms provenance defines. Provenance
exists because the systems that ultimately consume the library's output often need
to trace content back to its origin, and the only way for that tracing to be
possible at the end is for it to be captured at the beginning and carried
throughout. Provenance is treated as a first-class concept in its own section.

**Textual content.** Every unit carries the text that represents it — the words a
reader would read, or, for units that are not themselves textual, a textual
rendering that stands in for them. Textual content exists because the library's
reasoning about boundaries is fundamentally reasoning about text: the language
model decides where to divide a document by reading it, and a unit that offered no
text could not participate in that decision. Even units that originate as
non-textual elements must present some textual form if they are to have any
bearing on where the document is divided.

**Metadata.** Every unit may carry additional descriptive information beyond the
essentials above — information that some extraction technologies can provide and
others cannot, or that particular kinds of units naturally possess. Metadata
exists as the model's controlled means of accommodating variation and richness
without multiplying the essential structure of a unit. It is peripheral by
intent: the model's core reasoning does not depend on it, and it is the place
where extractor-specific richness lives without contaminating the concepts the
pipeline relies upon.

### Conceptual kinds of units

Units come in several conceptual kinds, distinguished by what sort of document
element they represent. These kinds are part of the model's vocabulary:

**Heading.** A unit representing a title or section header — text that introduces
and labels the material that follows it.

**Paragraph.** A unit representing a run of prose: the ordinary body text of a
document, and the most common kind of unit.

**List item.** A unit representing a single entry within an enumerated or bulleted
list — one element of a sequence of related points.

**Table.** A unit representing tabular content: information arranged in rows and
columns, presented to the pipeline in a textual form that preserves its meaning
while remaining readable as text.

**Image.** A unit representing a graphical element. Because the pipeline reasons
over text, an image participates only through whatever textual rendering stands in
for it — a caption, a description, or recovered text — and where no such rendering
exists it contributes nothing to boundary reasoning.

**Page break.** A unit representing the transition from one page of the source to
the next. It carries little or no content of its own but marks a structural fact
about the document that may matter both to boundary reasoning and to provenance.

These are conceptual types rather than extraction-specific objects, and this is a
deliberate and important choice. Extraction technologies each have their own
vocabulary for the elements they recover, and those vocabularies differ from one
another and evolve over time. If the model spoke in any one technology's terms, it
would be bound to that technology and would have to change whenever the technology
did. By defining its own small, stable set of conceptual kinds, the model gives
every extraction technology a common target to normalize into, keeps the pipeline
independent of any particular technology's vocabulary, and ensures that a unit
means the same thing to the library regardless of which technology produced it.
The conceptual kinds are the model's own language for the parts of a document; the
extraction technologies merely translate into it.

Not every extraction technology can recover every kind of unit. A technology of
limited fidelity may produce only paragraphs and page breaks, while a richer one
distinguishes headings, tables, and lists. The model accommodates this: a prepared
document composed only of the kinds a particular technology could recover is a
complete and valid prepared document. The set of conceptual kinds defines what the
model *can* express, not what every technology *must* provide.

---

## 5. Stable Identity

Stable identity is one of the most important concepts in the entire project, and
it deserves to be understood on its own terms. Everything the library does to
process a document in pieces and reassemble the conclusions rests upon it.

Stable identity means that each unit, from the moment it is established during
normalization, carries a mark that distinguishes it from every other unit and that
never changes for the remainder of the run. It is not a description of the unit's
content and it is not derived from where the unit currently sits in some
processing view; it is a fixed reference to *that particular unit*, valid
everywhere and for the whole life of the run. To refer to a unit's identity is to
refer to that unit and no other, unambiguously, regardless of the context in which
the reference is made.

Concretely, this identity is the unit's **global ordinal**: a single stable value
assigned in document order at the moment the prepared document is created. It is
*global* — defined over the whole document rather than over any window — which is
precisely why it is not derived from where the unit happens to sit in a transient
processing view, and why it is the same value in every window in which the unit
appears. Because it is assigned in document order, that one value carries both the
unit's identity and its position in the sequence. Boundary decisions are expressed
against this global ordinal, and it is the identity on which merging depends. The
engineering constitution refers to this same value as the global ordinal; the two
names denote one concept.

The reason this matters becomes clear when one follows an identity through the
pipeline:

- **Normalization** establishes each unit's identity, assigning it once as the
  prepared document is created.
- **Windows** are formed over the units, and a unit that appears in a window
  appears there under the same identity it was given at normalization. Windowing
  does not create new identities; it distributes existing ones across views.
- **Boundary decisions** returned for each window are expressed in terms of these
  identities. The model does not say "divide after the third thing in this
  window"; it says "divide at the unit with this identity". The conclusion is
  anchored to the unit itself, not to the unit's position in a transient view.
- **Merging** reconciles the boundary decisions from all the windows into a single
  set. It can only do this because the decisions are expressed against identities
  that mean the same thing across every window: a decision from one window and a
  decision from an overlapping window can be recognized as referring to the same
  place precisely because they name the same identity.
- **Final chunks** are composed of units, and a chunk records the identities of
  the units it contains. The path from a chunk back to the exact units of the
  original document runs along these same identities.

Identity never changes because the moment it did, this chain would break. If a
unit's identity differed between two windows, a boundary decision from one could
not be compared with a decision from the other, and merging would combine
conclusions that only appeared to refer to the same place while in fact referring
to different ones. If identity were reassigned between normalization and
chunking, a chunk could no longer be traced to the units it was built from. Stable
identity is the thread that runs unbroken from the first stage to the last, and it
is what makes it possible to take a document apart, reason about the pieces
separately, and put the conclusions back together into a coherent whole.

Merging depends on it absolutely and more than any other operation. Merging exists
solely to reconcile overlapping windows, and reconciliation is only meaningful if
the things being reconciled can be recognized as the same across windows. Identity
is what provides that recognition. A failure of stable identity does not announce
itself; it produces a merged result that is quietly wrong, and only on documents
large enough to have been divided into windows in the first place. For this
reason, stable identity is not merely a convenience of the model but a condition
of its correctness.

---

## 6. Windows

A window is a temporary processing view over a contiguous run of a document's
units. Windows exist for one reason: a document may be larger than the language
model can consider at once, and it must therefore be presented to the model in
portions small enough to be handled. A window is one such portion — a way of
looking at part of the document for the purpose of obtaining a boundary decision
about it.

The most important thing to understand about a window is that it is a *view*, not
a *fragment*. A window does not hold content of its own and does not own any part
of the document. It refers to units that belong to the prepared document; it does
not copy them, contain them, or replace them. When a unit appears in a window, it
is the very same unit, with the very same identity, that belongs to the prepared
document — seen through the window rather than possessed by it. This distinction
is what allows the conclusions drawn over separate windows to be reconciled
afterward: because windows share the units rather than holding private copies,
decisions expressed over different windows are expressed over common ground.

Windows preserve identity without exception. A unit's identity within a window is
the identity it was given at normalization; windowing neither assigns new
identities nor alters existing ones. This is what makes a window a faithful view:
looking at a unit through a window tells you nothing different about which unit it
is than looking at it in the prepared document.

Overlap is an essential characteristic of windows. Adjacent windows are arranged
so that they share a run of units at their common edge — the units near the end of
one window also appear near the start of the next. Overlap exists because a
boundary that falls near the edge of a window might be difficult to judge with the
context on only one side of it; by ensuring that the region near every edge is also
seen within a neighboring window, where it sits away from the edge and has context
on both sides, the pipeline gives every part of the document a chance to be judged
with adequate surrounding context. Overlap is the reason merging has to reconcile
decisions at all: within an overlapping region, two windows both had an opinion,
and those opinions must be brought together.

Windows disappear after processing. They exist only for the duration of the work
of obtaining boundary decisions, and once those decisions have been obtained and
merged, the windows have served their purpose and cease to be part of the model.
Nothing in the final output records that windows ever existed or how the document
was divided among them; the fact of windowing is an internal mechanism, not a
property of the result. A window is scaffolding — necessary to construct the
result, absent from the result itself.

---

## 7. Boundary Decisions

A boundary decision represents a conclusion about where a document should be
divided: a determination that one coherent section of the document ends and the
next begins at a particular point. Boundary decisions are the substance of what
the semantic-chunking process produces before those conclusions are turned into
chunks. Where the units carry the document's content, boundary decisions carry
judgments about that content's structure.

A boundary decision is expressed in terms of stable identities. It does not
describe a division by position within a window or by any transient property; it
names the units at which the division occurs, by their identities. This is what
gives a boundary decision meaning that outlasts the window that produced it: a
decision expressed against identities refers to the same places in the document no
matter where or when it is read, because the identities it names are themselves
stable and universal within the run.

Consequently, a boundary decision is independent of any particular window. Although
each decision is produced by reasoning over a specific window, the decision itself
is not tied to that window. Once expressed, it stands on its own as a statement
about the document's units, and it can be compared with, reconciled against, and
combined with decisions produced over other windows. This independence is exactly
what makes merging possible: merging gathers boundary decisions that were produced
window by window and treats them as a single body of conclusions about one
document, which it can do only because each decision's meaning does not depend on
the window it came from.

The model distinguishes, conceptually, between raw and validated boundary
decisions. A raw decision is what the language model returns before it has been
checked: a purported conclusion that has not yet been confirmed to be well-formed
or to refer to genuine units of the document. A validated decision is one that has
been confirmed — established to be well-formed and to name real units in a
sensible way — and is therefore trustworthy for use downstream. The distinction
exists because the model's output cannot be assumed correct merely because it was
produced, and the model must have a way to speak of a conclusion that has been
vouched for as distinct from one that has merely been offered. Only validated
decisions proceed to merging; raw decisions that cannot be validated are subject
to the library's recovery and failure handling rather than being allowed to shape
the result.

---

## 8. Semantic Chunks

A semantic chunk is a contiguous section of the document produced by applying the
merged boundary decisions to the units — the material lying between one boundary
and the next. Chunks are the library's primary output, the thing that everything
else in the pipeline exists to produce. They are what downstream systems will
ultimately consume.

Four qualities define what a good chunk is, and the model holds every chunk to
them.

**Coherent.** A chunk represents a unit of meaning that hangs together — a
section of the document whose parts belong with one another. This coherence is the
entire point of semantic chunking: the boundaries were chosen so that what falls
within them forms a meaningful whole rather than an arbitrary span.

**Self-contained.** A chunk can be understood on its own, without requiring the
reader to consult the surrounding chunks to make sense of it. This matters because
chunks are typically consumed in isolation, retrieved individually and considered
apart from their neighbors; a chunk that cannot stand alone fails at the very
purpose for which it was created.

**Ordered.** Chunks carry the order of the document. The sequence of chunks
reflects the sequence of the source, and each chunk's place among the others is
meaningful. Order is preserved from the units, through the boundaries, into the
chunks, because the document's sequence is part of its meaning and must survive to
the end.

**Traceable.** A chunk can be traced back to the document it came from. It is
composed of specific units, and through those units and their provenance it can be
located within the original source. Traceability is what allows a system built on
the library to point back from a chunk to the place it originated.

A chunk's relationship to the original document runs entirely through the units.
A chunk is not an independent copy of a portion of text severed from its origins;
it is the composition of a contiguous run of units, and those units are the same
units the prepared document established, bearing the same identities and the same
provenance. Through them, a chunk remains connected to the exact material of the
source. This is why provenance matters so much to chunks: without it, a chunk
would be text without an origin — usable, perhaps, but unable to answer the
question of where it came from, and therefore useless to any system that must cite
or justify its use of the material. Because chunks inherit the provenance of their
units, they arrive at the end of the pipeline still knowing their place in the
document that produced them.

---

## 9. Chunking Result

The chunking result is the complete output of a run — the form in which everything
the caller needs leaves the library. It is more than the chunks alone, and the
difference is deliberate.

A result contains, conceptually, four things. It contains the **semantic chunks**
themselves, in order: the primary output and the reason the run took place. It
contains **processing information** describing how the run proceeded — an account
of the work performed on the way from document to chunks. It contains
**warnings**: notices of anything that occurred during the run which the caller
ought to know about, including any windows that had to be recovered or handled
under the failure policy rather than resolved cleanly. And it contains **execution
metadata**: the record of what the run consumed and involved, the information a
caller needs to understand the cost and character of the process.

The result is deliberately richer than a bare list of chunks, and this richness is
a considered choice rather than an embellishment. The library performs work that
reaches outside itself, calling upon a language model, and that work can encounter
difficulties which it recovers from rather than failing outright. A caller
operating the library in a real system needs to know not only what was produced
but how — whether every part of the document was resolved cleanly or some parts
were handled by fallback, what the run cost, what anomalies arose. A model that
returned only the chunks would hide all of this, forcing the caller to operate the
library blind and discover its behavior indirectly. By making the result carry the
full account of the run alongside its chunks, the model treats observability and
honesty about what happened as part of the output rather than as an afterthought.
The result is the complete, truthful record of a run, of which the chunks are the
central but not the only part.

---

## 10. Provenance

Provenance is a first-class concept in the model — not an attribute tacked onto
units and chunks, but a distinct idea that the model maintains deliberately from
the first stage to the last. Provenance is the answer to the question "where did
this come from?", asked of any piece of the library's output and traced back to
the original document.

A unit's provenance is composed of exactly three mandatory facts, and every unit
carries all three. It includes the **global ordinal** — the unit's stable identity
and, because that identity is assigned in document order, its position within the
overall sequence of the document, which situates each unit relative to everything
before and after it. It includes the **page** on which the material originated,
locating content within the physical or logical pages of the source. And it
includes the **character offsets** — the span of the document's *normalized text*
the unit occupies, tying the unit to the exact characters it contributed. These
three, and only these three, are mandatory; anything further an extraction
technology can offer belongs to metadata rather than to provenance.

The **normalized text** of a prepared document is the concatenation, in
global-ordinal order and with no separator between units, of the textual content of
every unit the document contains — each unit contributing its own text where it has
text, and the empty string where it has none. The offsets address that normalized
text. They are zero-based, half-open (the start inclusive, the end exclusive), and
measured in Java `String` UTF-16 code units. They never address the bytes of the
original document.

This is a deliberate choice about *which* text the offsets are into, and it is the
same choice for every extractor, whatever technology stands behind it. For a PDF, a
scan, or a photograph the original source is binary, and a character span into it is
not merely unknown but undefined; there is no honest answer an extractor of such a
document could give. The normalized text is the one coordinate space that every
extraction technology produces and every consumer can reconstruct, so the model
defines the span there and asks the same thing of all extractors. What provenance
promises is therefore exact rather than approximate: a unit's span always locates it
within the text the library actually carries.

Two further notions follow from these facts rather than adding to them. Because a
chunk is composed of units and inherits their provenance, tracing any content in
the output back to the **source unit** it derived from is a direct consequence of
the units' ordinals, not a separate field a unit must store. And relating a decided
boundary to the units it falls between — the **boundary mapping** — is likewise
expressed against those same ordinals, and it belongs to the result the pipeline
produces rather than to the provenance a unit is given at normalization.

Provenance survives the entire pipeline, and this survival is its defining
property. It is established at normalization, when units are created and each is
given its origin in the source. It is carried unchanged through windowing, because
windows are only views and do not disturb the units they present. It is preserved
through boundary decisions, which are expressed against the very units whose
provenance is recorded. It is inherited by chunks, which are composed of those
units and therefore carry their provenance forward. And it arrives intact in the
result, where the final chunks can still say where in the original document they
originated.

This survival is not automatic; it is a requirement the model imposes on itself
because the alternative forecloses too much. The systems that consume semantic
chunks frequently must do more than use the text — they must attribute it, cite
it, display its source, or justify a conclusion by pointing to where it came from.
A chunk that had lost its provenance could not support any of this. By treating
provenance as a first-class concept that no stage is permitted to discard, the
model guarantees that the path back to the source is available at the end to
anyone who needs it, which is a large part of what makes the library's output
usable in serious systems rather than merely produced.

---

## 11. Model Relationships

The concepts of the model are bound together by a small number of relationships,
and understanding those relationships is understanding how the model fits
together. The relationships are described here conceptually; they are the same
relationships that govern the flow of the pipeline, seen from the standpoint of the
model rather than the process.

**A prepared document owns its document units.** The units are not independent
entities that exist on their own and are gathered into a document; they are the
constituents of a prepared document, established when it is created and belonging
to it thereafter. The ownership is exclusive and definitional: to be a unit is to
be a unit *of* some prepared document, and the prepared document is the whole
within which its units have their place and their order.

**Windows reference units; they do not own them.** A window is a view over units
that belong to the prepared document. It points at a contiguous run of them but
possesses none of them. The units a window presents remain the prepared document's
units throughout; the window merely offers a way of looking at a portion of them.
This referencing-without-owning is what keeps windows from fragmenting the
document and what allows their separate conclusions to be reconciled.

**Boundary decisions reference units.** A boundary decision names the units at
which a division occurs, by their identities. It does not contain those units or
duplicate them; it refers to them. The decision is a statement *about* units that
live in the prepared document, anchored to them by identity.

**Chunks reference units.** A chunk is composed by referring to the contiguous run
of units that falls between two boundaries. The units it names are, once again, the
prepared document's units, bearing their original identities and provenance. A
chunk is defined by the units it comprises, not by an independent copy of their
content severed from them.

**The chunking result contains the chunks.** The result is the one concept that
*contains* rather than *references*: it gathers the finished chunks together with
the account of the run and presents them as the output. The chunks are part of the
result in a way that units are not part of windows or decisions — the result is
their home, the whole of which they are the central part.

Seen together, these relationships reveal the model's essential shape. A single
owner — the prepared document — holds the units, and every other concept in the
model refers back to those same units rather than holding content of its own.
Windows refer to units, decisions refer to units, chunks refer to units, and the
result gathers the chunks. The units are the common ground on which the entire
model stands, and the relationships are the ways the other concepts point back to
that common ground. This is the structural expression of the principle that
information lives in exactly one place: content belongs to the units, and
everything else refers.

---

## 12. Model Invariants

Certain properties must hold of the model in every correct implementation. They
are the conditions under which the model is coherent, and any implementation that
violates one has produced a different and incorrect model, however plausible its
individual parts may appear. These are conceptual invariants of the model; the
invariants of the architecture and the pipeline are defined elsewhere.

**Unit identity never changes.** Once a unit's identity is established at
normalization, it remains fixed for the entire run. No stage reassigns, alters, or
regenerates it. Every reference to a unit anywhere in the model refers to it by
this unchanging identity.

**Unit ordering never changes.** The order of the units, established when the
prepared document is created, is preserved everywhere and never rearranged. Windows
present units in their existing order; decisions and chunks respect it; the final
output reproduces it. Order is intrinsic and permanent.

**Chunks preserve document order.** The sequence of chunks in the output reflects
the sequence of the document. Chunks do not reorder the material they represent,
and the order among chunks is the order of the units they are composed from.

**Provenance is never lost.** From the moment it is established at normalization,
provenance is carried through every stage and every concept without being
discarded. No transformation in the model strips a unit or a chunk of its origin.

**Offsets address the normalized text.** For every unit of a prepared document, the
span `[startOffset, endOffset)` selects exactly that unit's textual content from the
document's normalized text — that is, `normalizedText.substring(startOffset,
endOffset)` equals the unit's own text, or the empty string for a unit that carries
none. It follows that a unit without text has a zero-length span, and that the spans
are contiguous and non-overlapping in global-ordinal order, each unit beginning where
the preceding one ended. This holds for every extractor without exception; an
extractor is the sole party able to establish it, because it alone creates units.

**Every boundary references valid units.** A boundary decision, once validated,
names only units that genuinely belong to the prepared document. A decision that
referred to a unit not present in the document, or that named an identity with no
corresponding unit, is not a valid decision and does not enter the model's
trustworthy state.

**Windows never own data.** A window holds no content of its own. It references
units that belong to the prepared document and possesses nothing. Nothing in the
model depends on a window for the existence of any content, and when windows cease
to exist, no content ceases to exist with them.

**Chunks are reconstructed from units.** A chunk is defined by the units it
comprises, not by independent content of its own. The material of a chunk is the
material of its units, and a chunk can always be understood as a composition of
units drawn from the prepared document.

**Model objects remain immutable.** Every concept in the model, once created, does
not change. Whatever transformations occur between one stage and the next occur by
producing new values, never by mutating existing ones. No concept in the model is
observed to hold one set of values at one moment and a different set at another.

---

## 13. What the Model Deliberately Does Not Represent

The model is defined as much by what it excludes as by what it contains. A number
of things that might plausibly appear in a domain model of this kind are absent
from this one on purpose, and their absence is what keeps the model pure.

**Language-model provider information.** The model contains nothing about which
language-model provider was used, how it was reached, or what is peculiar to it.
The provider is an external technology reached through an abstraction, and its
identity and characteristics have no place among the concepts the library reasons
about. The model would be the same whichever provider satisfied it.

**Transport and communication.** The model represents nothing about how the
library communicates with the outside world — no notion of requests travelling
over a network, of connections, or of the mechanics of reaching an external
service. These are concerns of the machinery that carries out the pipeline, not of
the concepts the pipeline manipulates.

**Frameworks.** No framework the library happens to sit near or be used within is
represented in the model. The concepts owe nothing to any framework and would be
unchanged in its absence. The model is expressed purely in terms of documents,
units, boundaries, and chunks, and never in terms of the frameworks that
surrounding code may employ.

**Persistence and storage entities.** The model contains no notion of how its
concepts might be stored, retrieved, or represented in any store. A unit is not a
record in a database and a chunk is not a stored row; they are conceptual objects,
and how a particular system might choose to persist them is outside the model
entirely.

**Serialization and encoding.** The model says nothing about how its concepts are
encoded for exchange or how they might be written in any particular format. The
form in which the library instructs the model or receives its response is a matter
of the machinery, not of the domain; the concepts exist independently of any
encoding of them.

**Adapter-specific details.** Nothing particular to any extraction technology or
any provider adapter appears in the model. Whatever an adapter must know or do to
satisfy the library's abstractions stays within that adapter and never becomes a
concept the model carries. The model speaks its own vocabulary and requires
adapters to translate into it, not the reverse.

**Temporary implementation objects.** The model does not include the incidental
structures that an implementation might create for its own convenience — the
scratch values, intermediate helpers, and passing conveniences that assist in
carrying out the pipeline but do not correspond to any genuine concept in the
domain. The model contains only concepts that the architecture requires, and an
object that exists merely because an implementation found it handy is not among
them.

The model remains pure for a definite reason. Its concepts are meant to outlast
every technology the library is currently built upon — the providers, the
extraction tools, the frameworks, the encodings, all of which will change over the
long life the project intends to have. A domain model entangled with any of them
would have to change whenever they did, and would carry the accidents of today's
implementation into a future in which they no longer apply. By admitting only the
concepts that the problem of semantic chunking genuinely requires, and refusing
every concept that belongs to the machinery rather than to the domain, the model
stays stable while everything around it is free to change. That purity is what
makes it fit to be the canonical reference for the project's concepts for as long
as the project exists.
