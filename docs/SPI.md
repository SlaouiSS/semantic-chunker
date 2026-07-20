# SPI.md

*The extension contracts.*

This document defines the Service Provider Interfaces of the `semantic-chunker`
library: the two contracts through which external implementations supply
capabilities to the library, and the obligations those implementations must meet so
that the library's invariants are preserved. It is written for engineers building
integrations — those implementing an extractor for a document source, or an adapter
for a language-model provider.

It is not implementation documentation, an API reference, or a listing of members.
It describes no classes beyond the two contract concepts, no algorithms, and no
internal machinery. Its concern is the philosophy and the responsibilities of the
SPIs: what an implementer is answerable for, what they are not, what guarantees they
must provide, and what guarantees the library provides in return. The few code
fragments are illustrative of the shape of the relationship, not specifications of
exact signatures.

The concepts referred to — prepared document, units, stable identity, provenance,
boundary decisions, chunks, result — are defined in the model document. The pipeline
into which these SPIs plug is described in the pipeline document. The public API that
sits above them is described in the API document. This document assumes all of them
and defines only the two seams along which the library is extended.

An implementer who finishes this document should know exactly what they are
responsible for, exactly what they are not responsible for, exactly what guarantees
they must provide, and exactly what guarantees the library provides for them.

---

## Table of Contents

1. [SPI Philosophy](#1-spi-philosophy)
2. [Overview](#2-overview)
3. [DocumentExtractor](#3-documentextractor)
4. [ChunkingModel](#4-chunkingmodel)
5. [SPI Contracts](#5-spi-contracts)
6. [SPI Lifecycle](#6-spi-lifecycle)
7. [Error Responsibilities](#7-error-responsibilities)
8. [SPI Invariants](#8-spi-invariants)
9. [What SPI Implementations Deliberately Do Not Do](#9-what-spi-implementations-deliberately-do-not-do)
10. [SPI Evolution](#10-spi-evolution)

---

## 1. SPI Philosophy

The library exposes Service Provider Interfaces for one reason: there are exactly two
things it cannot do itself and must therefore obtain from outside. It does not parse
documents, and it does not run language models. Every other part of semantic chunking
is the library's own work, but these two capabilities belong to external technologies,
and the SPIs are the contracts through which those technologies are supplied.

**Extensibility.** The SPIs are where the library is open. Because it cannot anticipate
every source a document might come from or every model provider a developer might use,
it defines contracts that any such technology can satisfy. An implementer brings a new
capability by satisfying a contract, without the library needing to know in advance
that the capability exists.

**Technology independence.** The SPIs are what make the library independent of any
particular extraction technology or model provider. The library depends on the
contracts, not on the technologies that fulfill them. Which technology stands behind a
contract is a choice made by the developer assembling the library, and it can change
without the library changing, because the library never sees past the contract.

**Stable contracts.** An SPI is a long-term commitment. Implementations are written
against it and depend on it not shifting beneath them, and the library depends on every
implementation honoring it. Because a contract binds both sides for the life of the
project, the SPIs are defined with restraint and are expected to remain stable.

**Separation of concerns.** The SPIs draw a clean line between what the library does and
what an external technology does. On one side is the capability — extracting content,
executing a request against a model. On the other side is everything else — normalizing,
planning, framing, validating, merging, assembling — which is the library's alone. The
SPI is the seam, and it keeps the two concerns from bleeding into each other.

**Adapter architecture.** An SPI implementation is an adapter: it translates between the
library's contract and a specific external technology, presenting that technology to the
library in the terms the library defines. All that is peculiar to a technology lives
inside its adapter, behind the contract, and reaches no further. The adapter is where
the outside world is admitted, and the contract is the narrow door through which it is
admitted.

Only a very small number of SPIs exist — precisely two — because only two capabilities
are genuinely external to semantic chunking. The library resists defining any further
extension point, because every SPI is a permanent contract, a place where outside code
can affect the library's behavior, and a surface that must be kept stable forever. Two
is the number the problem requires, and the library holds to exactly that number.

---

## 2. Overview

The library defines exactly two Service Provider Interfaces:

- **DocumentExtractor** — supplies the capability of turning a raw document into the
  library's normalized representation.
- **ChunkingModel** — supplies the capability of executing a request against a language
  model and returning its response, and of reporting the model's context-window limit
  and estimating token counts, which the library relies on to plan windows.

These two are sufficient because they correspond exactly to the two points where the
pipeline reaches outside the library, and to the only two capabilities the library does
not provide itself. Everything between those two points — the whole of how semantic
chunking is performed — is internal work that the library neither delegates nor exposes.
The extractor sits at the entrance, where a document is brought into the library's terms;
the model sits at the one interior point where a semantic judgment must be obtained from
a technology the library does not embody. There is no third external capability, and so
there is no third SPI.

Everything else remains internal for the reason the architecture and the API both
establish: the library is configurable in *what* it works with — the extraction
technology and the model — and fixed in *how* it performs semantic chunking. The two SPIs
are the whole of the "what." The "how" is not an extension point and is not exposed. An
implementer therefore has exactly two contracts to consider, and can be certain that no
other seam exists through which the library expects, or permits, external participation.

---

## 3. DocumentExtractor

**Responsibility.** A `DocumentExtractor` turns a raw document into the library's
normalized representation. It is the adapter for an extraction technology, and it owns the
crossing from an external file to a prepared document expressed in the library's own
terms.

**What it receives.** A `DocumentSource` supplied for extraction — the library's honest input
type, carrying the raw content together with its media type and an optional filename. The
extractor receives enough to identify and read the source; it does not receive any of the
library's internal state, because none exists yet at this point in the operation.

**What it produces.** A prepared document: an ordered collection of units, each bearing a
stable identity, its provenance, and its textual content, as the model document defines.
The extractor produces the representation that everything downstream will consume. What it
hands back is the library's own concept, not the extraction technology's.

```
PreparedDocument prepared = extractor.extract(source);
```

**What it guarantees.** That the prepared document it produces is a faithful, uniform
representation of what the extraction technology recovered; that the units are in the true
order of the document; that every unit carries a stable identity that will not need to be
changed and provenance locating it within the document; and that this is expressed
entirely in the library's terms, with nothing of the extraction technology's own vocabulary
carried through.

**The offset obligation.** One part of provenance deserves stating plainly here, because the
extractor is the only party able to establish it. A unit's character offsets do not address the
original document's bytes; they address the document's **normalized text** — the concatenation,
in global-ordinal order and with no separator, of the textual content of every unit the prepared
document contains, each unit contributing its own text where it has text and the empty string
where it has none. The offsets are zero-based, half-open, and measured in Java `String` UTF-16
code units, and the invariant every extractor must satisfy is that
`normalizedText.substring(startOffset, endOffset)` equals the corresponding unit's textual
content. A unit with no text therefore has a zero-length span, and the spans are contiguous and
non-overlapping across the document.

This rule is the same for every extraction technology, and it exists so that no extractor has to
fabricate. For a PDF, a scan, or a photograph the source is binary and a character span into it
is undefined; an extractor asked for one could only invent it, which the error responsibilities
below forbid. Defining the span over the normalized text gives every technology — layout parser,
OCR engine, or vision model alike — a coordinate space it can populate exactly and honestly. The
library validates non-negativity and ordering where it can, but it cannot check the invariant
itself, so satisfying it is part of what an implementer is answerable for.

**What it must never do.** It must never leak anything specific to the extraction technology
into the representation it produces. It must never produce units whose identities or ordering
it does not intend to be final, because everything downstream treats them as fixed. It must
never reach into or assume anything about the stages that follow it. And it must never take on
any responsibility beyond extraction and normalization — it produces a prepared document and
stops.

Four concepts frame the extractor's role:

**The normalization boundary.** The extractor sits astride the boundary between the outside
world and the library. Everything before its output is concerned with an external file and an
external technology; everything after it is concerned with the library's own uniform
representation. The extractor is the stage that closes that boundary, and once it has produced
a prepared document, nothing downstream reaches back across it.

**Technology independence.** Different extractors wrap different technologies, and each
translates its technology's output into the same normalized representation. This is what
allows the library to remain independent of any one technology: the differences between
technologies are resolved inside their extractors, so that the library receives one kind of
thing regardless of which extractor produced it.

**Structured extraction.** The extractor's value is that it recovers structure, not merely
text — distinguishing the conceptual kinds of units the model defines to whatever extent its
technology allows. An extractor of limited fidelity may recover only some kinds of unit, and a
prepared document composed only of those kinds is complete and valid. The contract requires the
extractor to express what it recovered in the library's terms; it does not require it to recover
what its technology cannot.

**Prepared document creation.** The extractor is the sole creator of prepared documents. No
other participant produces one, and the library relies on the extractor to establish the units,
their identities, their order, and their provenance correctly, because every later stage builds
upon them without revisiting them.

---

## 4. ChunkingModel

**Responsibility.** A `ChunkingModel` executes a request against a language model and returns
its response. Because it is the one component that knows which model stands behind the
contract, it is also answerable for two read-only properties of that model the library needs
in order to size its work: the model's context-window limit and an estimate of how many
tokens a given piece of text will occupy. It is the adapter for a model provider, and it owns
the crossing between the library's request for a boundary decision and the external model that
answers it. These are its whole of responsibility; it neither plans windows, frames prompts,
validates, nor merges — it executes, and it reports what the underlying model is.

**What it receives.** A request prepared by the library — the framed question about one window,
expressed in the library's terms, ready to be presented to a model. The model implementation
receives a complete, well-formed request; it does not construct the request itself and does not
decide what is asked.

**What it produces.** The model's response to that request, delivered back to the library in the
form the contract defines, together with the information about the execution that the library
needs. The implementation returns what the model answered; it does not interpret, judge, or act
upon that answer.

```
ModelResponse response = model.execute(request);
```

**What it also answers for.** Beyond executing requests, the implementation reports the model's
context-window limit and estimates the number of tokens a given piece of text will occupy. These
are the inputs window planning needs in order to size windows that fit the model and reserve room
for its response. They are read-only descriptions of the underlying model, carrying no judgment
about any document, and a documented heuristic estimate is acceptable where the provider cannot
count precisely — with the caveat that under-counting risks context overflow and over-counting
wastes budget, so the heuristic's behavior must be documented.

**What it guarantees.** That the response it returns is the model's answer to the request it was
given; that it is delivered in the library's terms, with nothing of the provider's own
representation leaking through; that the information the library relies upon to account for the
execution accompanies the response; and that the context-window limit and token estimates it
reports faithfully describe the model it wraps. It guarantees faithful delivery, not correctness of
content — the content is the model's, and the library will judge it.

**What it must never do.** It must never alter the request it was given or substitute a different
question. It must never interpret or validate the model's answer — checking the answer is the
library's work, and an implementation that pre-judged it would usurp a responsibility that is not
its own. It must never leak anything specific to the provider into what it returns. And it must
never take on any responsibility beyond executing the request, delivering the response, and
reporting the model's context-window limit and token estimates — it does not plan the windows,
frame the prompts, validate the answer, or merge the decisions.

These concepts frame the model implementation's role:

**Provider independence.** Different model implementations wrap different providers, and each
presents its provider to the library through the same contract. The library depends on the
contract, not on any provider, and can therefore work with any provider for which an
implementation exists, without itself changing.

**Request execution.** The implementation's active task is to take the library's request to
the model and obtain the model's response. It is the executor of a request the library composed —
the hand that carries the question to the model and brings the answer back.

**Response delivery.** The implementation delivers the model's response to the library faithfully,
in the library's terms. Delivery is faithful transmission: the response the library receives is the
response the model gave, neither shaped nor filtered by the adapter.

**Model introspection.** Alongside execution, the implementation answers for the model it wraps: it
reports the context-window limit and estimates token counts when the library asks, so that window
planning can size windows that fit. These are read-only properties of the underlying model, not
judgments about the document, and they belong to the adapter because only the adapter knows which
model stands behind the contract.

**Provider isolation.** Everything peculiar to a provider — how it is reached, what it requires, how
its answers are shaped in its own terms — is contained entirely within the implementation and never
surfaces in the library. The implementation is where the provider's specifics live and end.

---

## 5. SPI Contracts

Every implementation of either SPI must satisfy a common set of obligations. These are the terms on
which the library admits an external capability, and they exist so that the library can trust what
an implementation hands it as thoroughly as it trusts its own internal stages.

**Deterministic behavior where applicable.** Where an implementation's work is within its control, it
should behave predictably: the same input should lead to the same behavior. An extractor should
normalize the same document the same way. A model implementation cannot make the model deterministic —
that is inherent to the model — but its own conduct, its faithful delivery of request and response,
should be predictable. Determinism is required where the implementation genuinely governs the outcome
and not demanded where it does not.

**Clear failures.** When an implementation cannot do its work, it must fail clearly and report the
failure, rather than returning something malformed, incomplete, or misleading. The library's handling
of trouble depends on being told plainly that trouble occurred; an implementation that disguises a
failure as a success defeats that handling.

**Immutable values.** Whatever an implementation hands to the library must be immutable, as everything
in the model is. A prepared document, once produced, does not change; a response, once delivered, does
not change. The library holds these values on the understanding that they are fixed.

**Respect for input contracts.** An implementation must accept its input in the form the library
provides and must not require the library to supply anything the contract does not define. It works with
what it is given, on the terms the contract sets.

**Respect for output contracts.** An implementation must produce its output in exactly the form the
contract defines, complete and well-formed. The library builds upon that output directly, and any
deviation from the expected form propagates into stages that assumed conformance.

**Never violating model invariants.** An implementation must preserve every invariant the model
defines — the stability of identities, the fixity of order, the presence of provenance, the immutability
of values. These invariants are the foundation the whole pipeline stands on, and an implementation that
violated one would undermine stages far removed from itself.

These contracts exist because an SPI implementation is admitted into the heart of the library's
process. Its output is consumed by internal stages that treat it as sound, on the strength of these
obligations. The contracts are what allow the library to extend trust across the boundary to code it did
not write: an implementation that honors them can be relied upon exactly as an internal stage is relied
upon, and the clean composition of the pipeline holds across the seam.

---

## 6. SPI Lifecycle

An SPI implementation participates in a chunking operation at a definite moment, does its work, and
returns control to the library. It does not drive the operation; it is called upon within an operation
the library is driving.

The extractor is called at the beginning of an operation, when a raw document must be brought into the
library's terms. The library invokes it, the extractor produces a prepared document, and control returns
to the library. From that point the extractor has no further part in the operation; it is not consulted
again.

The model implementation is called during the interior of an operation, each time the library needs a
boundary decision for a window. The library invokes it with a prepared request, the implementation
returns the model's response, and control returns to the library. This happens once per window that
requires a decision; each invocation is self-contained, and between invocations the model implementation
has no part in the operation. Earlier, while the library is planning windows and before any request is
executed, it also consults the same implementation for the model's context-window limit and for token
estimates; these consultations are read-only, carry no document judgment, and do not change what the
implementation is responsible for.

In both cases the pattern is the same: the library calls the implementation, the implementation performs
its single capability and returns, and the library resumes. The implementation is a provider of a
capability that the library reaches for when it needs it, not a participant that decides when its
capability is needed or what happens around it.

Orchestration always remains inside the library. The implementation is never responsible for sequencing,
for deciding what happens before or after its own work, or for coordinating with any other participant.
It does not know where in the operation it sits, how many times it will be called, or what its output will
be used for. It answers when called and is otherwise passive. The library owns the order of events, the
flow of representations, and every decision about what to do with what an implementation returns. An
implementer therefore does not design a process; they provide one capability that a process the library
owns will call upon.

---

## 7. Error Responsibilities

The division of responsibility for failure between an implementation and the library is precise, and
observing it is essential to the library behaving as designed.

**Reporting failures.** An implementation is responsible for detecting when it cannot do its own work and
for reporting that plainly to the library. If an extractor cannot interpret a source, or a model
implementation cannot reach its provider or obtain a response, that is a failure the implementation must
surface. Reporting is the implementation's duty; the library cannot respond to a failure it is not told
about.

**Not hiding errors.** An implementation must never conceal a failure — never return an empty, partial, or
fabricated result in place of an honest failure, and never quietly swallow a problem and proceed as if
nothing were wrong. A hidden failure is worse than a reported one, because it corrupts downstream stages
that trusted the output while denying the library the chance to respond.

**Clear failure boundaries.** When an implementation fails, the boundary of that failure should be clear:
what was being attempted, and that it did not succeed. The library uses this clarity to decide how to
respond — whether the failure is confined to a single window or fundamental to the operation — and a
failure reported without a clear boundary leaves that decision ill-informed.

**Never performing library recovery internally.** An implementation must not attempt the library's own
recovery on the library's behalf. It is not the implementation's place to retry across the pipeline's
logic, to fall back to a degraded result of the library's kind, or to compensate for a failure in a way
that hides it. Recovery — retry within the pipeline, fallback, the failure policy, the recording of
warnings — is the library's responsibility, and it can only exercise that responsibility if the
implementation reports failures honestly rather than absorbing them.

The division, stated plainly: the implementation is responsible for doing its capability and for reporting
truthfully when it cannot; the library is responsible for everything that happens in response. An
implementation that reports a failure has done its part correctly, even though its work did not succeed;
the library then decides whether to retry, to fall back, to record a warning, or to end the operation. An
implementation that tries to own that decision — by hiding the failure or recovering in the library's
stead — takes on responsibility that belongs to the pipeline and, in doing so, prevents the pipeline from
handling the failure as it was designed to. Honest reporting is the whole of what the library asks of an
implementation when things go wrong.

---

## 8. SPI Invariants

Every implementation must preserve a set of invariants without exception. These are the specific
promises on which stages elsewhere in the pipeline depend, and each protects a point of correctness that
an implementation is uniquely positioned to break.

**It must not modify library objects.** Whatever the library hands an implementation, and whatever an
implementation hands back, is immutable and must remain so. An implementation must not alter a value the
library owns or hand back a value it intends to change afterward. Downstream stages hold these values on
the understanding that they are fixed.

**It must not recreate identities.** The stable identities of units are established once, by the extractor,
at normalization. No implementation may reassign, regenerate, or duplicate them. A model implementation in
particular deals in requests and responses expressed against existing identities and must never invent new
ones. Recreating identities would sever the thread on which windowing, merging, and traceability all depend.

**It must not bypass validation.** A model implementation must deliver the model's raw response for the
library to validate; it must never present an unchecked answer as though it were already trustworthy, and
must never route its output around the validation stage. Validation is the library's guardian over model
output, and an implementation that bypassed it would let unverified conclusions into the result.

**It must not change ordering.** The order of units, established at normalization, is fixed. No
implementation may reorder units, and none may produce output that implies a different order than the one
the library established. Order is part of the document's meaning and is relied upon through to the final
chunks.

**It must not introduce provider-specific concepts.** Nothing peculiar to an extraction technology or a
model provider may enter the library through an implementation. What crosses the boundary must be expressed
purely in the library's own terms. A provider-specific concept admitted into the library would couple the
library to a technology it is designed to remain independent of, undoing the very isolation the SPI exists
to provide.

Violating any of these breaks the library, often far from the point of violation and often silently. An
implementation that recreated an identity would cause merging to reconcile the wrong things; one that
changed ordering would corrupt the final sequence of chunks; one that bypassed validation would let
malformed conclusions shape the output; one that leaked a provider concept would compromise the library's
independence. These invariants are not local courtesies but load-bearing guarantees, and the pipeline's
correctness rests on every implementation upholding them exactly.

---

## 9. What SPI Implementations Deliberately Do Not Do

An SPI implementation provides one capability and nothing more. Several responsibilities that belong to the
library must never be taken on by an implementation, and understanding this boundary is as important as
understanding the capability itself.

An implementation does not perform **window planning**. How the document is divided into windows, how large
they are, and how they overlap is the library's decision. A model implementation receives a request for one
window already formed; it does not decide what the windows are. It supplies the context-window limit and the
token estimates the planner consumes to make those decisions, but supplying those inputs is not planning:
the division itself remains the library's.

An implementation does not perform **prompt generation**. The framing of the request — the library's own
fixed instruction and the way a window is presented — is the library's work. A model implementation receives
a complete request; it does not compose it.

An implementation does not perform **boundary merging**. Reconciling the decisions from separate windows into
one coherent set is internal work of the most delicate kind. An implementation contributes individual
responses; it never reconciles them.

An implementation does not perform **chunk construction**. Building the chunks from the merged boundaries is
the library's work. No implementation produces chunks; the extractor produces a prepared document and the
model produces responses, and neither produces output.

An implementation does not perform **pipeline orchestration**. The sequence of stages, the flow of
representations, and the coordination of the whole operation belong to the library. An implementation is
called into a process it does not direct.

An implementation does not perform **validation**. Checking that a model response is well-formed and refers
to genuine units is the library's guardian responsibility. A model implementation delivers the raw response
for validation; it does not validate.

All of these belong to the library for one consistent reason: they are part of *how* semantic chunking is
performed, which is the library's own substance and the thing it does not delegate. An implementation
supplies a capability the library lacks — parsing, or model execution — and the library supplies everything
that constitutes semantic chunking itself. If an implementation took on any of these responsibilities, it
would be defining part of the library's behavior from outside, exposing internal invariants to violation, and
blurring the clean line between a supplied capability and the library's own method. The library owns the
pipeline; implementations provide capabilities; and the library always owns orchestration. That division is
the whole design of the SPI layer.

---

## 10. SPI Evolution

The SPIs are the most enduring contracts in the project, because they bind two parties — the library and every
implementation — for the life of the project. How they change is governed by the recognition that every SPI
method is a permanent commitment.

**Backward compatibility.** The first consideration in any change to an SPI is that existing implementations
continue to work. An implementation written against a contract must not be invalidated by a later version of
that contract. Because implementations are written by others, often outside the project, breaking them is a
particularly serious cost, and it is avoided wherever any compatible path exists.

**Minimal interfaces.** The SPIs are kept as small as they can be while still expressing the capability they
represent. A small contract is easier to implement correctly, easier to keep stable, and less likely to force
a breaking change later. Every method an SPI defines is a method every implementation must provide and the
library must support forever, so the contracts admit only what the capability genuinely requires.

**Rare additions.** Growth of an SPI is exceptional. An addition is made only when the capability itself is
found to require something the existing contract cannot express, and even then it is weighed against the burden
it places on every implementer. The default expectation is that the contracts are complete and remain as they
are.

**Stable contracts.** The meaning of what an SPI defines does not shift over time. An implementer who has
satisfied a contract should be able to rely on that contract continuing to mean what it meant, so that their
implementation remains correct without revision as the library evolves around it.

**Long-term compatibility.** Taken together, these principles aim at contracts that implementers can depend on
for the long life the project intends to have. An integration written once should keep working across the
library's versions, and the effort of building an adapter should be an investment that endures rather than one
that must be repeated with each release.

The governing truth is that every SPI method becomes a long-term commitment the moment it exists — a promise to
every implementer that it will remain, and a promise to every user that implementations of it will keep
working. The SPIs are therefore defined with restraint and changed with reluctance, so that the two seams along
which the library is extended remain, for as long as the project lives, exactly as dependable as the day an
implementer first built against them.
