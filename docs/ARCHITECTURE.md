# ARCHITECTURE.md

*How the system is organized.*

This document describes the architecture of the `semantic-chunker` library at a
conceptual level. It defines how the system is structured — its stages, its
components, its modules, the direction its dependencies flow, and the invariants
that hold it together — so that an experienced engineer can understand the design
before reading any source code, and can implement or extend the project while
preserving its intent.

It is not an implementation guide, an API reference, or a data-model
specification. It names no classes, no methods, and no packages. The shape of the
domain model is defined in the model document; the public surface is defined in
the API document; the binding engineering rules are defined in the engineering
constitution. This document assumes all three and describes only the
architecture that sits above them.

---

## Table of Contents

1. [Architectural Overview](#1-architectural-overview)
2. [Architectural Principles](#2-architectural-principles)
3. [System Pipeline](#3-system-pipeline)
4. [Major Components](#4-major-components)
5. [Modules](#5-modules)
6. [Dependency Direction](#6-dependency-direction)
7. [Data Flow](#7-data-flow)
8. [Extension Philosophy](#8-extension-philosophy)
9. [Architectural Invariants](#9-architectural-invariants)
10. [What This Architecture Deliberately Does Not Do](#10-what-this-architecture-deliberately-does-not-do)

---

## 1. Architectural Overview

The library is organized as a single, linear pipeline. A document enters at one
end in its raw form and leaves at the other end as a set of semantic chunks
accompanied by a record of how they were produced. Between those two points the
work is divided into a fixed sequence of stages, each with one responsibility,
each consuming the output of the stage before it and producing the input of the
stage after it.

Two of these stages reach outside the library. Extraction relies on an external
technology to turn a file into structured content, and model execution relies on
an external language model to make boundary decisions. Everything the library
considers its own — normalization, planning, prompt orchestration, validation,
and merging — sits between those two external touchpoints and is where the
project's value lives. The external touchpoints are reached only through
abstractions the library owns, so that the pipeline's logic never depends on any
particular extraction technology or any particular model provider.

At the highest level the flow is: a raw document is extracted into structured
content and normalized into a single consistent representation; that
representation is divided into windows sized to what the model can consider at
once; each window is turned into a prompt, executed against the model, and its
response validated; the boundary decisions from all windows are merged into one
coherent set; and from that set the final chunks and an accompanying result are
produced. The stages are ordered and the order is meaningful — each stage
depends on guarantees established by the stages before it, and this ordering is
itself an architectural commitment, not an incidental arrangement.

The remainder of this document describes the principles behind this shape, the
stages and components in detail, the modules that package them, the direction in
which dependencies are allowed to flow, the representations that information
passes through, where the architecture is open to extension and where it is
deliberately closed, and the invariants every implementation must preserve.

---

## 2. Architectural Principles

The engineering constitution states the rules the code must follow. This section
does not restate them; it explains the architectural decisions those rules
produce — the reasoning that gives the system its shape.

**Separation of concerns.** Each stage of the pipeline addresses exactly one
concern and knows nothing of the concerns on either side of it. Normalization
does not know how content was extracted; window planning does not know how
documents are turned into prompts; merging does not know how the model was
called. This separation is what allows each stage to be understood, tested, and
changed in isolation, and it is why the pipeline can be reasoned about one stage
at a time rather than as a whole.

**Immutable data flow.** Information moves through the pipeline as immutable
representations. A stage receives its input, produces a new output, and never
alters what it was given. No stage mutates a shared structure that a later stage
also observes. This makes the flow of information explicit — the only way for one
stage to influence another is through the value it returns — and it removes an
entire category of defects that arise when several stages share mutable state.

**Explicit boundaries.** The seams between stages, and between the library and
the outside world, are deliberate and visible. The transition from one
representation to the next is a defined handoff, not an implicit sharing of
state, and the two points where the library reaches external technology are
narrow, named crossings rather than diffuse coupling scattered through the code.
Because the boundaries are explicit, it is always clear what each stage promises
and what it requires.

**Dependency inversion.** The pipeline depends on abstractions the library owns,
not on the concrete technologies that satisfy them. Extraction and model
execution are expressed as contracts the core defines, and the concrete
extractors and model providers implement those contracts. The core's logic is
written entirely against its own abstractions, which is what keeps it independent
of any specific external tool.

**Provider independence.** No knowledge of any particular language-model provider
exists anywhere in the pipeline. The model is reached only through the core's own
model abstraction, and everything provider-specific lives behind an adapter. The
choice of provider is therefore a deployment decision, invisible to the pipeline
and changeable without touching it.

**Extractor independence.** The same holds for extraction. No knowledge of any
particular extraction technology exists in the pipeline. Extraction is reached
only through the core's own extraction abstraction, and each concrete technology
lives behind its own adapter. The pipeline consumes a normalized representation
and is indifferent to how that representation was obtained.

**Deterministic orchestration.** While the language model's output is not itself
deterministic, the orchestration around it is. Given the same normalized
representation and the same configuration, the library performs the same
sequence of steps in the same order: it plans the same windows, constructs the
same prompts, applies the same validation, and merges results by the same rules.
The orchestration is a fixed, predictable procedure; the only source of variation
is the model, and that variation is confined to a single stage.

**Minimal public surface.** The architecture exposes very little of itself. The
pipeline's internal stages are not visible to the outside world; only the entry
point, the data that flows in and out, and two extension points are public. This
is an architectural choice as much as an API choice: by keeping the internal
structure private, the architecture is free to evolve its stages without
disturbing anyone who depends on it.

---

## 3. System Pipeline

The pipeline is the heart of the architecture. Its stages run in a fixed order,
and each exists because the stage after it depends on something the stage before
it guarantees. This section describes each stage's responsibility and the reason
it exists.

**Document.** The pipeline begins with a raw document supplied by the caller —
a file in some format, of unknown internal structure. This is the sole input, and
it is treated as opaque until extraction has interpreted it.

**Extraction.** The first stage interprets the raw document, turning an opaque
file into structured content: the text it contains together with whatever
structural signals the extraction technology can recover, such as headings,
paragraphs, lists, tables, and page references. This stage exists because the
library does not itself parse files; it delegates that solved problem to a mature
external technology and receives structured content in return. Extraction is one
of the two points where the pipeline reaches outside the library, and it does so
only through the core's extraction abstraction.

**Normalization.** The second stage converts the extracted content — whose exact
shape and richness vary from one extraction technology to another — into a single
consistent internal representation that the rest of the pipeline understands.
This stage exists to absorb the differences between extraction technologies so
that no later stage has to account for them. Whatever the source, everything
downstream operates on one uniform representation. Normalization is also where the
addressable units that boundaries will later refer to are established and given a
stable identity, so that every subsequent stage refers to the same units in the
same way.

**Prepared document.** The output of normalization is the normalized
representation: a consistent, ordered account of the document's content and
structure. It is the single representation the semantic-chunking logic consumes,
and the point at which the pipeline becomes independent of how the document was
obtained. From here forward, nothing depends on the original file or the
extraction technology.

**Window planning.** A document may be larger than a language model can consider
in a single pass. This stage divides the normalized representation into windows —
contiguous portions sized to fit within what the model can handle, with
deliberate overlap between adjacent windows so that a boundary falling near the
edge of one window is also visible within its neighbor. This stage exists so that
documents of any size can be processed correctly without asking the caller to
divide them, and it must reserve room within each window for the model's own
response, not only for the content it presents. The windows it produces preserve
the identity of the units established during normalization, so that a unit's
identity is the same regardless of which window it appears in.

**Prompt orchestration.** For each window, this stage constructs the instruction
that will be given to the language model — presenting the window's content in the
form the model is asked to reason about and expressing what it is asked to
decide. The instruction it produces is fixed by the library rather than supplied
by the caller, so that the library's behavior is defined by the library itself.
This stage exists to translate a window of normalized content into a
well-defined request for a boundary decision.

**LLM execution.** This stage sends each prompt to the language model and
receives its response. It is the second point where the pipeline reaches outside
the library, and it does so only through the core's model abstraction; no
knowledge of any particular provider exists here. This is the single stage whose
output is not deterministic, and the architecture deliberately confines that
non-determinism to it. Its responsibility is narrow: obtain the model's raw
response for a given prompt.

**Response validation.** The model's raw response cannot be trusted to be
well-formed simply because it was returned. This stage checks each response
against the structure the library requires, accepting it only if it is valid and,
where it is not, invoking the library's defined recovery procedure rather than
passing malformed data downstream. This stage exists to ensure that every
boundary decision entering the merging stage is well-formed, so that later stages
can rely on the integrity of what they receive. It is also where the library's
failure policy is enforced: a window that cannot be resolved after recovery is
handled according to that policy rather than being allowed to fail the whole
document.

**Boundary merging.** Because a large document is processed as several
overlapping windows, the boundary decisions arrive in separate pieces, and
adjacent pieces overlap. This stage reconciles those pieces into a single,
coherent set of boundaries over the whole document, resolving disagreements
within the overlapping regions where two windows both had an opinion. This stage
exists because the division into windows is an internal mechanism that must not
be visible in the result; merging restores the document to a single whole. It is
the most delicate stage in the pipeline, because it depends entirely on the
stable identity of units established during normalization and preserved through
planning.

**Semantic chunks.** From the merged set of boundaries, this stage produces the
final chunks: contiguous, self-contained units of the document, each
corresponding to a span between two boundaries. This is the library's primary
output — the material that downstream systems will embed, store, and retrieve.

**Chunking result.** The pipeline concludes by assembling the chunks together
with an account of how they were produced — the information a caller needs to
understand and operate the process, including any windows that were recovered or
handled by the failure policy. This stage exists because a library that performs
external calls and can encounter recoverable difficulties must report what
happened, not only what it produced. The result is the complete, honest record of
the run.

---

## 4. Major Components

The pipeline stages are carried out by a small set of architectural components,
each owning one responsibility. This section describes what each component is
responsible for, not how it is built or what it exposes.

**Extraction.** Responsible for obtaining structured content from a raw document
by delegating to an external extraction technology. It is the pipeline's first
external touchpoint and is defined by the core as an abstraction, with each
concrete technology providing its own implementation. Its responsibility ends the
moment structured content has been produced.

**Normalization.** Responsible for converting the varied output of extraction
into the single consistent representation the rest of the system consumes, and
for establishing the stable identity of the units that boundaries will later
reference. It is the component that makes the rest of the pipeline independent of
how content was obtained.

**Chunking orchestration.** Responsible for driving the pipeline from the
normalized representation to the final result. It coordinates the other
components in the correct order but performs none of their specialized work
itself; it is a thin coordinator, not a place where logic accumulates. It is the
component that guarantees the pipeline's order and that the flow of information
between stages is honored.

**Window planning.** Responsible for deciding how the normalized representation is
divided into windows, how large each window may be, and how much adjacent windows
overlap, while preserving the identity of the units it distributes across those
windows. It owns the sizing decisions that keep each request within the model's
limits and leave room for the response.

**Prompt orchestration.** Responsible for turning a window of normalized content
into a well-defined request for a boundary decision, using the library's fixed
instruction. It owns the translation from internal content to a model-facing
request.

**Model interaction.** Responsible for executing a request against the language
model and returning its raw response, through the core's model abstraction and
without any provider-specific knowledge. The same abstraction also reports the
underlying model's context-window limit and estimates token counts on request;
window planning consults these read-only properties to size its windows, since
only the adapter knows which model it wraps. It is the pipeline's second external
touchpoint and the sole location of non-deterministic behavior — a non-determinism
confined to request execution, as the reported limits and estimates do not vary
with it.

**Boundary validation.** Responsible for confirming that each model response is
well-formed, invoking recovery when it is not, and enforcing the failure policy
when a window cannot be resolved. It is the guardian that ensures only trustworthy
boundary decisions proceed to merging.

**Boundary merging.** Responsible for reconciling the per-window boundary
decisions into a single coherent set over the whole document, resolving overlap
disagreements. It depends on the stable identity of units and is the component
where the correctness of large-document processing is ultimately decided.

---

## 5. Modules

The project is divided into independent modules. The division exists so that a
caller takes on only what they use, so that the volatility of external
technologies is contained where it belongs, and so that the core remains
permanently free of any dependency on the technologies it orchestrates.

**Core.** The core module contains the pipeline and everything the library
considers its own: normalization, window planning, prompt orchestration, response
validation, boundary merging, chunk production, the orchestration that drives
them, the normalized representation and the result, and the two abstractions
through which the pipeline reaches the outside world. The core depends on no
extraction technology and no model provider. It defines the contracts; it does
not implement them for any particular external tool.

**Spring AI adapter.** This module allows the pipeline's model abstraction to be
satisfied by way of Spring AI, translating between the core's model contract and
that framework. It exists so that teams already working within that ecosystem can
supply a model to the library without the core knowing anything about it. It
depends on the core; the core does not depend on it.

The adapter is written against Spring AI's provider-neutral model abstraction and
against nothing else. It names no provider, reaches for no provider-specific type,
and takes no part in choosing or configuring one: the caller supplies a model that
is already configured, and the adapter executes requests against it. Because that
neutral abstraction does not report the size of the model's context window, the
caller supplies that too — it is a property of the specific model in use, and no
neutral contract exposes it. The adapter translates a request, executes it,
translates the response, and surfaces a failure to obtain one. It does not retry,
recover, validate, or interpret; those remain the pipeline's own work.

**Unstructured extractor.** This module satisfies the pipeline's extraction
abstraction using the Unstructured technology, translating its output into the
form the core's extraction contract expects. It exists so that documents can be
extracted by that technology without the core knowing anything about it. It
depends on the core; the core does not depend on it.

**Future integrations.** Additional model providers and additional extraction
technologies are expected over the life of the project. Each is expected to
arrive as its own dedicated module that depends on the core and satisfies one of
the core's two abstractions, and each is added only when a maintainer commits to
owning it. No future integration alters the core; it only provides an
implementation of a contract the core already defines.

The reason the core remains completely independent is that its independence is
what gives it a long life. The external technologies the library orchestrates —
extraction tools and model providers alike — evolve on their own schedules and at
their own pace. By confining each of them to its own module and keeping the core
ignorant of all of them, the volatility of the outside world is prevented from
reaching the part of the system that must remain stable. The core changes only
when semantic chunking itself changes, never because an external technology did.

---

## 6. Dependency Direction

The architecture permits dependencies to flow in exactly one direction, and this
rule is absolute.

The core owns the abstractions. It defines the contract for extraction and the
contract for model execution, and it defines the representations that flow through
the pipeline. It is the center of the system and the thing every other module is
arranged around.

Adapters depend on the core. Every extraction module and every model-provider
module is written against the core's abstractions and depends on the core to
obtain them. An adapter's entire purpose is to satisfy a contract the core has
already defined.

The core never depends on adapters. It has no knowledge of any adapter's
existence. It does not know which extraction technologies or which model providers
exist, and it must never be written in a way that assumes any of them. Nothing in
the core imports, references, or is shaped by anything specific to an external
technology.

Extraction technologies remain isolated. Whatever an extraction tool requires —
its own dependencies, its own quirks, its own evolution — is contained entirely
within that tool's adapter module and never leaks into the core or into any other
adapter.

Language-model providers remain isolated in the same way. Whatever a provider
requires is contained entirely within that provider's adapter module and reaches
no further.

This direction matters because it is what makes the library maintainable across a
span of years. The parts of the system most likely to change are the external
technologies, and they are precisely the parts held furthest from the core.
Because dependencies flow only inward, a change in any external technology is
confined to its own module; it cannot ripple into the core or into unrelated
adapters. The core, where the library's real value and its long-term stability
reside, is insulated from the churn of everything it depends upon. Reversing this
direction even once would couple the core to the outside world and forfeit that
insulation, which is why the rule admits no exception.

---

## 7. Data Flow

Information moves through the system as a succession of immutable
representations. Each is produced by one part of the pipeline and consumed by the
next, and each transition is a deliberate handoff from one form to another. The
internal composition of these representations is defined in the model document;
here the concern is only how information moves between them.

**Raw document.** The information enters as an opaque file. Nothing about its
content is known to the library until extraction has interpreted it.

**Prepared document.** Extraction and normalization together transform the raw
document into the normalized representation. This is the first form the library's
own logic understands, and the transition into it is where the pipeline sheds all
dependence on how the document was obtained. From this point forward, information
flows in terms the library defines for itself. This is also where the units that
boundaries will reference acquire their stable identity, an identity that every
later representation preserves unchanged.

**Windows.** Window planning transforms the single normalized representation into
a set of windows. This transition does not create new content; it partitions
existing content into overlapping portions while preserving the identity of every
unit. A unit that appears in more than one window is the same unit in each, and
carries the same identity in each — this is what later allows the separate views
to be reconciled.

**Boundary decisions.** Prompt orchestration, model execution, and validation
together transform each window into a validated boundary decision expressed in
terms of the stable unit identities. The information has now changed in kind:
where the windows carried content, the boundary decisions carry conclusions about
where that content should be divided. Because these conclusions are expressed
against the same stable identities the normalized representation established, they
remain meaningful independently of the window that produced them.

**Semantic chunks.** Boundary merging reconciles the per-window boundary
decisions into a single set over the whole document, and from that set the chunks
are produced. This transition collapses the windowing back into a single whole;
the fact that the document was ever divided leaves no trace in the chunks
themselves.

**Chunking result.** Finally, the chunks are assembled together with the account
of how they were produced into the result. This is the form in which information
leaves the library, complete with the record of the process that created it.

Across all of these transitions, no representation is altered once produced. Each
stage reads its input and emits a new output, and the movement of information is
therefore always explicit and always traceable from one form to the next.

---

## 8. Extension Philosophy

The architecture is open to extension in exactly two places and closed
everywhere else. This is a deliberate and load-bearing decision, not an
incidental one.

The two public extension points are the extraction abstraction and the model
abstraction — the pipeline's two external touchpoints. These are extensible
because they must be: the library does not itself parse documents or run language
models, and it cannot anticipate every extraction technology or every model
provider that a caller might wish to use. Making these two points extensible is
what allows the library to remain provider-independent and extractor-independent
over its lifetime, and it is what allows new integrations to arrive as isolated
modules without the core ever changing. These are the seams along which the
library is designed to grow.

Everywhere else, the architecture is intentionally closed. The internal stages of
the pipeline — normalization, window planning, prompt orchestration, response
validation, boundary merging, chunk production — are not extension points and are
not exposed for replacement or customization. This is deliberate for several
reasons. These stages embody the library's understanding of how semantic chunking
should be performed; they are the substance of what the project offers, and
allowing them to be replaced piecemeal would mean the library no longer defines
its own behavior. They also depend on one another through shared invariants — most
critically the stable identity of units that merging relies upon — and exposing
them individually would expose those invariants to violation from outside. And
every exposed extension point is a contract that must be honored for the life of
the project; keeping the internal stages private is what allows them to evolve and
improve without disturbing anyone.

The guiding rule is that the library is configurable in *what* it works with — the
extraction technology and the model — and fixed in *how* it performs semantic
chunking. The former is where variation genuinely belongs to the caller; the
latter is the library's own responsibility, and it keeps it. Under-exposing the
internals is a chosen strength: it is what lets the project both improve its
methods over time and keep the promises it makes to those who depend on it.

---

## 9. Architectural Invariants

Certain properties must hold in every implementation of this architecture,
regardless of how the individual stages are built. They are the conditions under
which the design is correct, and violating any of them breaks the system in ways
that may not be immediately visible. These are architectural invariants; the
invariants internal to the data model are defined in the model document.

**Pipeline order is fixed.** The stages run in the defined sequence, and no stage
runs before the stage whose guarantees it depends upon. The order is not an
implementation convenience; it is a correctness requirement, because each stage is
written assuming the guarantees established by those before it.

**Data flow is immutable.** Every representation that passes between stages is
immutable. No stage alters what it was given, and no two stages share mutable
state. Information moves only by a stage producing a new value from the value it
received.

**There is one normalized representation.** All semantic-chunking logic operates
on a single, uniform representation of the document. The differences between
extraction technologies are absorbed entirely at normalization and are invisible
thereafter. No later stage may reintroduce a dependence on how the document was
extracted.

**Orchestration is deterministic.** Given the same normalized representation and
the same configuration, the library performs the same sequence of steps in the
same order. The only non-deterministic element is the model's own output, and it
is confined to the single stage that executes the model. The orchestration
surrounding that stage is a fixed procedure.

**Document identity is stable.** Once normalization establishes the addressable
units of a document and their identities, those identities remain fixed for the
entire run. No stage renumbers, reorders, or reassigns them. Every stage that
refers to a unit refers to it by the same identity — the unit's global ordinal,
assigned once in document order and used unchanged thereafter.

**Boundary mapping is stable across windows.** The identity a unit carries is the
same in every window in which it appears, and a boundary decision expressed
against that identity means the same thing regardless of which window produced it.
This is the invariant on which boundary merging depends absolutely: without it,
the reconciliation of overlapping windows would combine decisions that only appear
to refer to the same place. It is the most consequential invariant in the
architecture and the one whose violation is hardest to detect, because it fails
silently and only on documents large enough to be windowed.

**Adapters are isolated.** Each external technology is confined to its own
adapter module, and nothing specific to it appears in the core or in any other
adapter. An adapter satisfies a core contract and does nothing more that the rest
of the system can observe.

**The core carries no framework dependencies.** The core depends on none of the
technologies it orchestrates — no extraction tool, no model provider, no
transport framework. It is written entirely against its own abstractions. This
invariant is what makes provider independence and extractor independence real
rather than aspirational.

---

## 10. What This Architecture Deliberately Does Not Do

The architecture is defined as much by what it refuses as by what it provides.
The following are absent by design, and their absence is a considered choice, not
an unfinished corner of the system.

**There is no plugin system.** The library does not offer a general mechanism for
third parties to insert arbitrary behavior into the pipeline. The two extension
points are specific, named contracts with clear purposes; beyond them, the
pipeline is not an open framework to be assembled from parts. A plugin system
would trade the coherence and guarantees of a fixed pipeline for an open-endedness
the project does not want.

**The pipeline is not configurable in its structure.** The stages, their order,
and the way information flows between them are fixed. A caller cannot rearrange
the pipeline, remove stages, or insert their own. The pipeline is the library's
definition of how semantic chunking is performed, and a configurable pipeline
would mean the library no longer defines it.

**Extension points do not exist everywhere.** Extensibility is confined to the two
places where it is genuinely required. The architecture resists the temptation to
make every stage replaceable, because every extension point is a permanent
contract and a permanent constraint, and most of the pipeline's stages have no
business being exposed.

**There is no framework coupling.** The core is bound to no external framework.
Frameworks are reached only through adapters and are prevented from shaping the
core, so that the library's longevity is never hostage to the evolution of a
framework it happens to sit near.

**There is no provider-specific behavior.** The pipeline behaves the same
regardless of which model provider or extraction technology satisfies its
abstractions. No provider is privileged, and no branch of the pipeline exists to
accommodate the peculiarities of one. Whatever is specific to a provider stays
inside that provider's adapter.

**There is no hidden magic.** The pipeline does nothing implicit or concealed. Its
stages are explicit, its data flow is traceable, and its behavior follows from the
defined sequence rather than from inference or convention operating out of sight.
An engineer reading the architecture can predict what the system will do.

**There is no automatic discovery.** The library does not scan its surroundings to
find extractors or providers and wire them in on its own. What the pipeline uses
is supplied to it deliberately. Automatic discovery would introduce behavior that
depends on the environment rather than on explicit choice, and the architecture
prefers the predictability of the latter.

**There is no room for feature creep.** The architecture has a defined scope and a
defined shape, and it is designed to keep them. Capabilities that belong to
adjacent problems — parsing, embedding, storage, retrieval, and the rest — are not
absorbed into the pipeline, and the pipeline is not extended to reach for them.

These absences share a single rationale: architectural simplicity is a deliberate
design choice and one of the project's most important assets. A system with one
fixed pipeline, two narrow extension points, one direction of dependency, and a
small set of invariants is a system that can be understood in full, implemented
faithfully, and maintained for many years. Every mechanism the architecture
declines to add is a mechanism that would have made it harder to understand and
harder to keep correct. The restraint is the design.
