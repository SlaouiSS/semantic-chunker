# CONTRIBUTING_ARCHITECTURE.md

**The engineering constitution of the project.**

This document is binding. Every design document, every RFC, every production
class, and every pull request MUST comply with it. When a proposed change and
this document disagree, this document wins until it is amended. Amending it is a
deliberate act (see [§14 Amending This Constitution](#14-amending-this-constitution)),
not something a feature PR does in passing.

The words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are
used in the sense of RFC 2119. A rule written with **MUST** is not a
suggestion; a reviewer is expected to block a change that violates it.

The target quality bar is explicit: **the code should be understandable and
acceptable to a maintainer of the Spring Framework.** If a Spring maintainer
would raise an eyebrow at a class, that class is not ready.

---

## Table of Contents

1. [Purpose and Scope Discipline](#1-purpose-and-scope-discipline)
2. [Engineering Philosophy](#2-engineering-philosophy)
3. [Architectural Principles](#3-architectural-principles)
4. [Module and Package Design](#4-module-and-package-design)
5. [Public API: The API Is the Product](#5-public-api-the-api-is-the-product)
6. [SPI Contracts](#6-spi-contracts)
7. [Domain Model and Immutability](#7-domain-model-and-immutability)
8. [Code Style](#8-code-style)
9. [Error Handling and the Failure Model](#9-error-handling-and-the-failure-model)
10. [Concurrency and Thread Safety](#10-concurrency-and-thread-safety)
11. [Documentation](#11-documentation)
12. [Testing](#12-testing)
13. [Backward Compatibility and Longevity](#13-backward-compatibility-and-longevity)
14. [Amending This Constitution](#14-amending-this-constitution)
15. [Performance](#15-performance)
16. [Definition of Done](#16-definition-of-done)

---

## 1. Purpose and Scope Discipline

This library has exactly one responsibility: **high-quality semantic chunking
of documents using an LLM.** The scope is fixed and is not open for
reinterpretation by any individual change.

The library **MUST NOT** grow into a RAG framework, a document-processing
framework, a parser, an OCR engine, an embedding or retrieval or reranking
library, or a general-purpose chunking toolkit. These are declared non-goals.
A change that pulls the project toward any of them is out of scope regardless of
its technical merit.

The library **MUST** delegate everything that a mature OSS project already
solves — parsing, OCR, LLM transport, authentication, retries at the wire
level, structured-output plumbing. Custom code exists **only** for the parts
that are genuinely unique to semantic chunking: normalization into a common
representation, window planning, prompt orchestration, response validation, and
boundary merging.

**Scope rules**

1.1. Any new dependency **MUST** be justified against the delegation principle:
we take the dependency because reimplementing it would be reinventing a solved
problem, not because it is convenient.

1.2. Custom code that duplicates a capability already provided by an accepted
dependency **MUST** be rejected.

1.3. "This would be a useful feature" is **not** sufficient justification. The
question is always: *does this belong to semantic chunking, or to something we
have declared out of scope?*

---

## 2. Engineering Philosophy

The philosophy in one sentence: **the smallest possible amount of our own code,
behind the smallest possible public API, doing the one thing we do, in a way a
Spring maintainer would sign off on.**

Concretely, the project values, in priority order:

1. **Correctness of the public contract.** The public API and the two SPIs are
   ten-year commitments. Getting them right outranks everything else.
2. **Maintainability over cleverness.** A junior engineer joining in year seven
   should be able to read a class and understand it without archaeology.
3. **Minimalism.** Every public type, every dependency, every abstraction is a
   liability that must earn its place. The default answer to "should we add
   this?" is *no*.
4. **Testability.** If a design cannot be tested deterministically without the
   network, the design is wrong, not the test.

The project explicitly rejects two failure modes with equal force:

- **Over-engineering.** Speculative abstraction, extension points nobody asked
  for, configuration for its own sake, and pattern-application for the sake of
  patterns are all forbidden. We do not build for imagined futures.
- **Under-engineering.** Shortcuts that leak implementation into the public
  surface, God classes, hidden coupling, and untested critical paths are
  forbidden with equal force.

The line between the two is drawn by a single test: **does this abstraction
have a concrete, present, named reason to exist?** If yes, it may exist. If the
reason is "we might need it later," it may not.

---

## 3. Architectural Principles

The project follows Clean Architecture, SOLID, *Clean Code* (Martin), and
*Effective Java* (Bloch) as its foundational references. The rules below are the
project-specific application of those references; they are not a summary of the
books.

### 3.1 Dependency direction

3.1.1. Dependencies **MUST** point inward, toward the domain. The core
semantic-chunking logic **MUST NOT** depend on any transport framework, any LLM
SDK, or any extraction library.

3.1.2. The core **MUST** depend only on abstractions it owns. The two such
abstractions are the `ChunkingModel` SPI and the `DocumentExtractor` SPI
(see [§6](#6-spi-contracts)). Every concrete adapter — Spring AI, LangChain4j,
Unstructured, Tika — depends on the core, never the reverse.

3.1.3. It is a **MUST-fix** review violation for any type in the core module to
import a Spring AI type, a LangChain4j type, an OpenAI SDK type, a Tika type, or
any other adapter-specific type.

### 3.2 SOLID, applied

3.2.1. **Single Responsibility.** No God classes. The orchestrator
(`SemanticChunker`) is a thin facade; it delegates to focused internal
collaborators (window planning, prompt rendering, response parsing, retry
policy, boundary merging), each of which changes for exactly one reason. A class
that would change for two unrelated reasons **MUST** be split.

3.2.2. **Open/Closed.** Behavior that must vary by deployment (which LLM, which
extractor) varies through the SPIs, not through editing core classes. Behavior
that is deliberately fixed (the chunking prompt, the chunking algorithm) is
**not** an extension point and **MUST NOT** be made configurable to
pre-empt hypothetical needs.

3.2.3. **Liskov.** Every `ChunkingModel` and every `DocumentExtractor`
implementation **MUST** honor the full documented contract of its SPI,
including the degradation and failure clauses. An extractor that silently
returns partial data where the contract requires an exception is a defect.

3.2.4. **Interface Segregation.** SPIs are minimal. A method is added to an SPI
only when the core genuinely calls it. No "convenience" methods on public
interfaces.

3.2.5. **Dependency Inversion.** High-level policy (how to chunk) depends on
abstractions (the SPIs), never on low-level detail (how OpenAI's HTTP works).

### 3.3 Composition and immutability

3.3.1. **Composition over inheritance.** Public classes **MUST NOT** be designed
for extension by subclassing. Concrete public classes **SHOULD** be `final`.
Reuse is achieved by composing collaborators, not by inheriting from them.

3.3.2. **Immutable by default.** Domain objects, value objects, configuration
objects, and results **MUST** be immutable. Mutability is permitted only inside
a single method's local scope or inside a clearly-owned internal builder.

3.3.3. **High cohesion, low coupling.** A package's types belong together and
know as little as possible about types in other packages. Cross-package
knowledge flows only through published contracts.

---

## 4. Module and Package Design

### 4.1 Modules

The project is multi-module. The core module carries the domain and the SPIs and
has no adapter dependencies. Each integration lives in its own module and is
optional for the consumer. The current modules are:

- **semantic-chunker-core** — `SemanticChunker`, the domain model
  (`PreparedDocument` and its `DocumentUnit` types, `SemanticChunk`,
  `ChunkingResult`), the two SPIs (`ChunkingModel`, `DocumentExtractor`),
  `DocumentSource`, the exception hierarchy, and all internal orchestration
  collaborators. Depends on no adapter.
- **semantic-chunker-spring-ai** — a `ChunkingModel` implementation over Spring
  AI, targeting Spring AI 2.0.x.
- **semantic-chunker-unstructured** — a `DocumentExtractor` over Unstructured.

Everything beyond these three is future work, added only when a maintainer
commits to owning it. Each future integration is a `ChunkingModel` or
`DocumentExtractor` implementation in its own module and none of them alters the
core. Anticipated examples include a LangChain4j `ChunkingModel` adapter, an
Apache Tika `DocumentExtractor`, and further extractors (Azure Document
Intelligence, Google Document AI, and the like).

4.1.1. A consumer who wants only Spring AI + Unstructured **MUST NOT** be forced
to pull Tika or LangChain4j onto the classpath. Optional means optional.

4.1.2. An adapter **MUST** depend only on the provider-neutral abstractions of the
technology it adapts, and **MUST NOT** declare a dependency on any provider
artifact. The Spring AI adapter is written against `ChatModel` and the neutral
chat types alone; it does not use the higher-level `ChatClient`, whose advisors,
memory, templating, and structured-output conversion are the interpretation and
recovery that [§6](#6-spi-contracts) reserves to the core. Keeping every provider
artifact off the module's compile classpath makes a provider-specific reference a
compile error rather than a review finding.

4.1.3. An adapter **SHOULD** call the types of its upstream technology and
**SHOULD NOT** implement its interfaces. Calling a narrow subset survives an
upstream major version; implementing an interface breaks whenever that interface
gains or changes a method. This is the difference between an adapter that absorbs
churn ([§13](#13-backward-compatibility-and-longevity)) and one that transmits it.

4.1.4. Where an adapter cannot obtain a value the SPI requires — because the
neutral abstraction does not expose it — that value **MUST** be supplied
explicitly by the caller at construction and **MUST NOT** be guessed. No
discovery, no reflection, no provider lookup, no heuristic, no default. The Spring
AI adapter takes the model's context-window limit this way, because no neutral
Spring AI type reports it. The adapter's documentation **MUST** state that the
caller owns the correctness of such a value, since an incorrect one silently
breaks window planning ([§6.2.3](#62-chunkingmodel)).

### 4.2 Package by feature, not by layer

4.2.1. Packages **MUST** be organized by feature/capability, not by technical
layer. There is no top-level `dto`, `service`, `util`, or `impl` package.

4.2.2. Within a module, the public API and the implementation **MUST** live in
separate packages so that implementation packages can be hidden. The convention
is: public types in the feature package (e.g. `…chunker`), implementation in a
nested `…chunker.internal` package.

4.2.3. Every type under an `internal` package is non-public API and **MAY**
change or be deleted in any release without notice. This is stated once here and
**MUST** be restated in the package documentation of every `internal` package.

4.2.4. Where the module system is in use, `internal` packages **MUST NOT** be
exported. Where it is not, package-private visibility and the naming convention
are the enforcement mechanism, and reviewers enforce it by hand.

4.2.5. The Java baseline is **Java 21**. This is a deliberate, documented floor
chosen so that sealed interfaces and records are available to the domain model.
Raising or lowering the baseline is a constitution amendment, not a routine
change.

---

## 5. Public API: The API Is the Product

**The public API is the product. Everything else is replaceable.** This section
has the highest enforcement priority in the document.

### 5.1 The surface is deliberately tiny

5.1.1. The complete intended public surface is small and enumerable:

- `SemanticChunker` and its builder — the single entry point.
- `ChunkingResult`, `SemanticChunk` — outputs.
- `PreparedDocument` and its sealed `DocumentUnit` hierarchy — the normalized
  input model, public because it appears in the advanced API.
- `DocumentSource` — the honest input type for extraction.
- `ChunkingModel` and `DocumentExtractor` — the two SPIs.
- The exception hierarchy.

5.1.2. Anything not on a deliberately-maintained public-surface list is
internal. Adding a type to that list is an API decision requiring maintainer
sign-off, never a side effect of a feature.

### 5.2 Rules for public types

5.2.1. **Every public class and interface MUST justify its existence.** The PR
that introduces it states, in the description, why it must be public rather than
internal. "It was easier" is not a justification.

5.2.2. **Under-exposing beats over-exposing.** When in doubt, a type is
internal. Promoting an internal type to public later is a cheap, backward-
compatible act. Demoting a public type is a breaking change we may never be able
to make. The asymmetry decides every close call.

5.2.3. Implementation detail **MUST NOT** leak through the public API. No
adapter type, no SDK type, no parser type ever appears in a public signature.
The internal pipeline collaborators (prompt renderer, response parser, retry
policy, window planner, boundary merger) are **internal** and **MUST NOT** be
published as extension points. They are refactorable at will precisely because
they are not public.

5.2.4. Extension points are added **only** when a concrete, present need
requires deployment-time variation. The two SPIs meet that bar. Nothing else
currently does. New extension points require a constitution-level justification,
not a feature-level one.

### 5.3 Builder and entry-point conventions

5.3.1. `SemanticChunker` is constructed through a builder. The builder
**MUST** fail fast: `build()` throws if a required collaborator (the model, the
extractor) is missing, with a message that names what is missing.

5.3.2. The entry point accepts convenience overloads for the caller's benefit
(`Path`, `DocumentSource`, `PreparedDocument`). New `java.io.File`-based
signatures **MUST NOT** be added; `Path` is the modern idiom. A bare
`InputStream` **MUST NOT** be accepted without an accompanying media type,
because extraction cannot route without it — this is why `DocumentSource`
exists.

5.3.3. Any public method that performs network I/O, blocks, or incurs monetary
cost **MUST** say so in its Javadoc, in plain language, in the first paragraph.
`chunk(...)` is such a method.

---

## 6. SPI Contracts

The project has exactly two SPIs, and they are its most valuable and most
expensive artifacts. A mistake in either is a ten-year mistake.

### 6.1 Documentation requirement for every SPI

6.1.1. Every SPI **MUST** document, in its type-level Javadoc, all five of the
following. An SPI missing any of these is incomplete and **MUST NOT** ship:

- **Responsibility** — the one thing an implementation is for, and the things it
  is explicitly *not* responsible for.
- **Lifecycle** — how instances are created, how long they live, whether they
  are reused across calls, and who owns creation and disposal.
- **Thread safety** — the exact concurrency contract (see
  [§10](#10-concurrency-and-thread-safety)). The default expectation is that
  an implementation **MUST** be safe for concurrent use by multiple threads;
  any deviation **MUST** be stated loudly.
- **Ownership** — what the core guarantees to the implementation, and what the
  implementation guarantees back. In particular, which side owns input
  validation and which side owns resource cleanup.
- **Extension rules** — what an implementer may assume, what they **MUST NOT**
  assume, and what will break them if they violate the contract.

### 6.2 `ChunkingModel`

6.2.1. `ChunkingModel` is the core-owned abstraction over "run this chunking
request against an LLM and give me the raw result." It **MUST NOT** expose any
provider concept. Adapters translate provider specifics; the core sees only this
contract.

6.2.2. The contract **MUST** be expressed with core-owned request and response
types, not `String` in and `String` out. Specifically:

- The **request** carries the rendered prompt, an optional response-shape hint
  (so adapters that support provider-native structured output can use it, and
  adapters that cannot may ignore it and fall back to prompt-only), and
  normalized generation hints the core actually cares about (low temperature, an
  output-token budget).
- The **response** carries the raw model text **and token usage**
  (input/output). Usage is not optional decoration: it is the source of the
  cost and observability data surfaced in `ChunkingResult`, and it can only
  travel back through this contract.

6.2.3. The model **MUST** expose its context window (`maxInputTokens`, or an
equivalent) and a token-estimation capability. These are properties of the
underlying model, and the adapter is the only component that knows which model
it wraps. Placing them on the builder instead would let them drift from the real
model and silently break window planning. A documented heuristic fallback is
permitted for adapters that cannot count precisely; under-counting risks context
overflow (a hard failure) and over-counting wastes money, so the fallback's
behavior **MUST** be documented.

6.2.4. This contract is minimal by design. Every method on it is forever.
Nothing is added to `ChunkingModel` that the core does not itself call.

### 6.3 `DocumentExtractor`

6.3.1. `DocumentExtractor` transforms a `DocumentSource` into a
`PreparedDocument`. It **MUST** take exactly one honest input type
(`DocumentSource`: content + media type + optional filename), not a family of
ambiguous overloads and not a bare stream.

6.3.2. Extractors differ in fidelity, and the contract **MUST** embrace this.
A low-fidelity extractor (e.g. Tika) producing a flat sequence of paragraph
units with page breaks and no detected headings or tables is a **valid**
`PreparedDocument`. Structural richness (headings, structured tables) is
therefore **optional and extractor-dependent**. The contract **MUST NOT** make
any structural richness mandatory, because doing so would make the abstraction
leak the moment a low-fidelity backend cannot satisfy it.

6.3.3. Extractors **SHOULD** declare the media types they support, so the
orchestrator can route or reject up front rather than failing deep inside a
parser. This is the seam a future routing/composite extractor will need.

6.3.4. Ownership of validation is explicit: the extractor validates that it can
handle the source and throws a typed extraction exception if it cannot; the core
does not guess.

---

## 7. Domain Model and Immutability

### 7.1 General rules

7.1.1. Domain types **MUST** be immutable and **SHOULD** be Java `record`s where
a record fits. Mutable DTOs are forbidden in the domain.

7.1.2. Collections held by domain objects **MUST** be defensively copied on the
way in and exposed as unmodifiable views. A caller **MUST NOT** be able to mutate
a `PreparedDocument` or a `ChunkingResult` after construction.

7.1.3. `Optional` **MUST NOT** be used as a field or as a constructor/record
component. `Optional` is a return type. Absence in a field is modeled by the
type design, not by wrapping.

7.1.4. Boolean parameters in public signatures are forbidden where they encode a
mode; use a small named type or two methods instead. A parameter list with two
or more booleans is an automatic redesign.

### 7.2 `PreparedDocument` — the boundary index space

`PreparedDocument` is one of the most important types in the project. Its design
is governed by a single reframing that **MUST** guide every decision about it:

> **`PreparedDocument` defines the atomic, addressable units that chunk
> boundaries refer to. It is the index space over which boundaries are
> expressed.**

7.2.1. It **MUST** be a **flat, ordered sequence of typed `DocumentUnit`s**, not
a nested tree and not a record of parallel field-arrays. Order is the one property
boundary detection needs; a flat ordered list expresses it directly, a
field-array cannot, and a hierarchy adds structure the chunker does not use and
low-fidelity extractors cannot produce.

7.2.2. `DocumentUnit` **MUST** be modeled as a **sealed hierarchy** whose
subtypes are `Heading`, `Paragraph`, `ListItem`, `Table`, `Image`, and
`PageBreak`, so that consumers can switch over a closed, exhaustively-known set.

7.2.3. Every `DocumentUnit` **MUST** carry mandatory **provenance**, and the
mandatory provenance is exactly three fields: a stable global ordinal, a page
reference, and character offsets into the normalized text. Provenance is cheap to
include now and a breaking change to add later, and citation-dependent consumers
walk away without it. It is not optional.

The **normalized text** of a `PreparedDocument` is the concatenation, in
global-ordinal order and with **no separator** between units, of the textual content
of every unit it contains — each unit contributing its own text where it has text and
the empty string where it has none. The offsets **MUST** address that normalized
text: zero-based, half-open `[startOffset, endOffset)`, measured in Java `String`
UTF-16 code units, exactly as `String.substring(int, int)` interprets them. They
**MUST NOT** address the bytes of the original document. Every extractor **MUST**
follow this rule identically, whatever technology stands behind it; a `String`
character span into a PDF, a scan, or a photograph is undefined, and the normalized
text is the only coordinate space all extractors share.

The governing invariant, for every unit of a `PreparedDocument`, is
`normalizedText.substring(startOffset, endOffset)` equals that unit's textual
content. Three consequences are normative: a unit carrying no text **MUST** have a
zero-length span (`startOffset == endOffset`); spans **MUST** be contiguous and
non-overlapping, each unit's `startOffset` being the sum of the text lengths of all
preceding units; and a `Table` contributes its Markdown serialization
([§7.2.5](#72-preparation-and-the-document-model)), that being its textual content.

Enforcement is the extractor's, not the core's. `Provenance` validates only what a
single value can see — non-negativity and ordering — because the invariant spans a
whole prepared document. The core **MUST NOT** be extended to police it; an
extractor that violates it has broken the SPI contract ([§6.3](#63-documentextractor)).

7.2.4. The global ordinal is load-bearing (see [§7.3](#73-the-global-ordinal-invariant)).
It is the concrete form of the **stable identity** the model document defines:
the single value that identifies a `DocumentUnit`, that boundary decisions are
expressed against, and that — because it is assigned in document order — also
fixes the unit's position in the sequence. It **MUST** be assigned once, at
normalization time, and **MUST** be stable for the life of the
`PreparedDocument`.

7.2.5. **Tables MUST have a decided representation.** A table is one
`DocumentUnit`, but its serialized form is simultaneously what the LLM sees and
what lands in the
resulting chunk, which couples the domain model to the prompt and to the output.
The canonical serialization **MUST** be decided (Markdown is the pragmatic
default for LLM consumption) **and** the structured cell data **MUST** be
retained so downstream can re-render. This decision **MUST NOT** be deferred
past the first design document.

7.2.6. Non-text `DocumentUnit`s (e.g. `Image`) **MUST** carry optional textual
content (caption / alt text / OCR text), because a boundary decision is purely
textual; a unit with no text cannot influence chunking and the type **MUST** make
that explicit.

7.2.7. The typed core of the model **MUST** stay small. Extension happens through
a per-unit metadata map and document-level metadata, **not** through adding
typed fields, because `PreparedDocument` is public and every added field is a
breaking change. The metadata map **MUST** remain genuinely peripheral;
load-bearing logic **MUST NOT** read from it, because untyped map access defeats
testability and the type system.

### 7.3 The global-ordinal invariant

This is the single most dangerous coupling in the system and is called out as a
first-class rule.

7.3.1. When the window planner splits a document into overlapping windows, the
`DocumentUnit` ordinals presented to the model **MUST** remain globally stable
and unambiguously mappable back to the original `PreparedDocument`.

7.3.2. The boundary merger reconciles boundaries across the overlap region. If
the same ordinal means different things in different windows, the merger will
silently produce wrong boundaries — a defect that does not appear in single-
window tests and surfaces only on large documents in production.

7.3.3. Global-ordinal stability across windows is therefore an **explicit
invariant** of the window planner and the boundary merger. It **MUST** be stated
in the code, and it **MUST** be tested adversarially: overlapping windows that
disagree, a boundary that falls inside the overlap, and a window that returns
zero boundaries are all required test cases.

7.3.4. Window sizing **MUST** reserve budget for output. A window's input size is
`maxInputTokens − promptOverhead − expectedOutputTokens`, and the output budget
scales with the number of boundaries the window can produce. A planner that sizes
windows to the full context window will cause the model to truncate its JSON,
which no amount of retry can fix.

---

## 8. Code Style

The reference style is the Spring Framework's. The rules below are the ones we
enforce most actively.

8.1. **No clever code.** Readability outranks brevity and outranks cleverness
every time. If a reviewer has to pause to decode a line, the line is rewritten.

8.2. **Small methods, one responsibility per class.** A method that does not fit
on a screen is suspect. A class with more than one reason to change is split.

8.3. **No magic numbers, no magic strings.** Every literal with meaning is a
named constant. Prompt fragments, JSON keys, token limits, and retry counts are
named, not inlined.

8.4. **Meaningful names.** Names describe intent, not type or implementation.
`WindowPlanner`, not `Helper`; `boundaryOrdinal`, not `idx`.

8.5. **Immutable collections and defensive copies** at every boundary, per
[§7.1.2](#71-general-rules).

8.6. **Records over mutable DTOs**, per [§7.1.1](#71-general-rules).

8.7. **Defensive programming where appropriate**, meaning at trust boundaries:
validate public method arguments and SPI inputs, fail fast with a precise
message, and do **not** litter internal private methods with re-validation of
invariants already guaranteed by construction. Fail fast at the edge; trust the
core.

8.8. **Fail fast.** Invalid state is rejected at construction or at the public
boundary, not discovered three layers deep. Constructors and builders validate
and throw immediately.

---

## 9. Error Handling and the Failure Model

Because the library performs LLM calls, failure is a normal operating condition,
not an exceptional one. The failure model is part of the architecture, not an
afterthought.

9.1. The exception hierarchy is unchecked and typed: at minimum
`ExtractionException`, `ChunkingException`, and `ModelException`, each carrying
enough context (which document, which window) to diagnose a production incident.

9.2. **A single bad window MUST NOT fail an entire job by default.** An LLM may
fail to return valid JSON for one window no matter how many times it is retried.
The default policy is: bounded retry with reprompt, then **degrade that one
window to a single chunk** and record a warning. Degrading a window to one chunk
is a no-op boundary decision and an error state — it is **not** a second chunking
algorithm and does **not** violate the single-algorithm scope rule.

9.3. Every degraded window, every retry, and every recoverable anomaly **MUST**
be surfaced in `ChunkingResult` (e.g. as warnings and per-window diagnostics). A
caller ingesting a 900-page document **MUST** be able to learn that three
windows degraded without the job throwing.

9.4. A hard, non-recoverable failure (e.g. the model is unreachable) **MAY**
throw. A recoverable, localized failure **MUST NOT** throw by default. The
distinction is documented on the entry point.

---

## 10. Concurrency and Thread Safety

10.1. The library **MUST NOT** hold static mutable state. Ever. Static mutable
state is untestable and unsafe, and it is grounds for immediate rejection.

10.2. Dependencies are supplied by constructor injection. There are no hidden
dependencies, no service locators, and no global registries.

10.3. `SemanticChunker` **SHOULD** be safe to reuse across threads once built,
because it is composed of immutable configuration and stateless collaborators.
Any per-call mutable state lives on the stack of that call.

10.4. Every SPI **MUST** state its thread-safety contract explicitly
(see [§6.1](#61-documentation-requirement-for-every-spi)). The default
expectation communicated to implementers is that their implementation will be
called concurrently and **MUST** be safe for it.

---

## 11. Documentation

11.1. Every public type **MUST** have type-level Javadoc explaining its purpose
and its place in the pipeline. A public type with no Javadoc does not ship.

11.2. Every SPI **MUST** carry the five-part contract from
[§6.1](#61-documentation-requirement-for-every-spi).

11.3. Every `internal` package **MUST** carry `package-info.java` stating that it
is non-public API subject to change without notice.

11.4. **Architecture decisions MUST be recorded.** Significant or contested
decisions (the `DocumentUnit` granularity, the table serialization, the failure policy,
the Java baseline, adding a public type, adding an SPI method) are captured as
short Architecture Decision Records so that a maintainer in year seven can
recover *why*, not just *what*.

11.5. Documentation describes the contract, not the current implementation.
Javadoc that pins callers to an implementation detail is a defect, because it
turns a private decision into a public promise.

---

## 12. Testing

12.1. **Design for testability first.** If a class cannot be unit-tested without
the network, the class is redesigned, not mocked around. The SPIs exist in part
so that the entire pipeline can be tested against a fake `ChunkingModel` and a
fake `DocumentExtractor`.

12.2. **Tests MUST be deterministic.** No test depends on a live LLM, on wall-
clock timing, or on network availability. LLM behavior is simulated through fake
`ChunkingModel` implementations that return controlled responses.

12.3. **Unit tests first, integration tests second.** Core logic — window
planning, ordinal stability, boundary merging, response validation, retry and
degradation — is covered by fast unit tests. Adapter modules additionally carry
integration tests, which **MAY** exercise real backends but **MUST NOT** be
required for the core build to pass.

12.4. **The boundary merger and the window planner MUST be tested
adversarially**, per [§7.3.3](#73-the-global-ordinal-invariant). These are the
highest-bug-density components and receive the most hostile test cases in the
project.

12.5. No test relies on static state, execution order, or shared mutable
fixtures.

---

## 13. Backward Compatibility and Longevity

The project is built to live **ten years or more.** Decisions are made on that
horizon, not on the horizon of the next release.

13.1. Public APIs and SPIs are expensive to change and **MUST** be treated as
long-term contracts. Changing them is a deliberate, versioned, documented act.

13.2. **Prefer adding new API over breaking existing API.** When a capability is
missing, the first design instinct is an additive, backward-compatible
extension. Breaking changes are a last resort, batched into a major version, and
accompanied by a migration path.

13.3. Short-term decisions that trade future flexibility for present convenience
**MUST** be flagged as such in review and **SHOULD** be rejected. "We'll fix it
in 2.0" is not a plan.

13.4. Adapter and extractor modules absorb the churn of their upstream
dependencies (a young or fast-moving upstream is exactly why the adapter exists).
The core **MUST NOT** inherit that churn.

---

## 14. Amending This Constitution

14.1. This document changes only through a dedicated change whose sole purpose is
the amendment, reviewed by a maintainer, with the rationale recorded as an
Architecture Decision Record.

14.2. A feature PR **MUST NOT** quietly relax a rule here to make itself pass. If
a rule genuinely blocks good work, the correct move is to propose amending the
rule openly, not to route around it.

---

## 15. Performance

15.1. **No premature optimization.** Clarity is the default; performance work is
justified by measurement, never by intuition.

15.2. Optimization targets **MUST** be measured bottlenecks, not suspected ones.
A performance PR includes the measurement that motivated it.

15.3. That said, avoid gratuitous waste: unnecessary object allocation in hot
paths (window planning over large documents, repeated tokenization) is avoided
by construction, not because it was profiled, but because allocating needlessly
is simply not writing the code well. Reasonable allocation discipline is table
stakes; micro-optimization is not.

15.4. The dominant cost in this library is the LLM call, not CPU. Performance
attention **SHOULD** concentrate on reducing unnecessary model calls (correct
window sizing, avoiding needless retries) far more than on shaving local CPU.

---

## 16. Definition of Done

A change is **not** done — and **MUST NOT** be merged — until every item below is
true. Reviewers use this list directly.

- [ ] It stays inside the fixed scope ([§1](#1-purpose-and-scope-discipline)); it
      does not drift toward a non-goal.
- [ ] The core module imports no adapter, SDK, or parser type
      ([§3.1.3](#31-dependency-direction)).
- [ ] No new public type or SPI method exists without a written justification and
      maintainer sign-off ([§5.2.1](#52-rules-for-public-types)).
- [ ] Implementation detail does not leak through a public signature
      ([§5.2.3](#52-rules-for-public-types)); internal pipeline collaborators
      stay internal.
- [ ] Domain objects are immutable, defensively copied, and use records where
      appropriate; no `Optional` fields, no boolean-mode parameters
      ([§7.1](#71-general-rules)).
- [ ] Any touched SPI carries the full five-part contract
      ([§6.1](#61-documentation-requirement-for-every-spi)).
- [ ] Global-ordinal stability is preserved, and any change to windowing or
      merging is covered by adversarial tests
      ([§7.3](#73-the-global-ordinal-invariant)).
- [ ] The failure model holds: a single bad window degrades and warns rather than
      throwing by default ([§9](#9-error-handling-and-the-failure-model)).
- [ ] No static mutable state; dependencies are constructor-injected
      ([§10](#10-concurrency-and-thread-safety)).
- [ ] Tests are deterministic and require no live LLM
      ([§12](#12-testing)).
- [ ] Every new public type has Javadoc; significant decisions have an ADR
      ([§11](#11-documentation)).
- [ ] A Spring Framework maintainer, reading this change cold, would not object
      to it.

---

*This document is the engineering constitution of the project. Every future
design document and every future production class follows it. When something
here proves wrong, we amend the constitution — we do not ignore it.*
