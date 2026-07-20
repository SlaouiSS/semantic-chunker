# API.md

*The public API design reference.*

This document explains the public API of the `semantic-chunker` library: what the
library exposes to the developers who use it, and — more importantly — *why* the
public surface looks the way it does. It is the canonical reference for the
design of the API, intended for developers evaluating whether that API is well
made before they depend on it.

It is not an implementation guide, a user manual, or a reference listing. It does
not enumerate every member of every type, and it names no packages and no
internal components. It concerns only the public surface: the small set of things
a developer touches, and the reasoning behind keeping that set small. The
concepts referred to here are defined in the model document; the architecture
that sits beneath them is defined in the architecture document; the engineering
rules that constrain the whole are defined in the engineering constitution. This
document assumes all of them and speaks only about the public face of the
library.

The few code fragments below are illustrative. They exist to make the shape of
the public surface concrete and are deliberately minimal; they are not a
specification of exact signatures.

---

## Table of Contents

1. [API Philosophy](#1-api-philosophy)
2. [Primary Entry Point](#2-primary-entry-point)
3. [Builder Philosophy](#3-builder-philosophy)
4. [Public Concepts](#4-public-concepts)
5. [Typical Usage](#5-typical-usage)
6. [Advanced Usage](#6-advanced-usage)
7. [Configuration Philosophy](#7-configuration-philosophy)
8. [Error Handling](#8-error-handling)
9. [Extension Points](#9-extension-points)
10. [API Invariants](#10-api-invariants)
11. [What the Public API Deliberately Does Not Expose](#11-what-the-public-api-deliberately-does-not-expose)
12. [API Evolution](#12-api-evolution)

---

## 1. API Philosophy

The public API is the product. Everything a developer knows about the library,
they know through its public surface, and everything the library promises to keep
stable, it promises there. The API is therefore designed with more care than any
other part of the project, and around a small number of goals.

**Simplicity.** The public surface is small enough to be understood in a single
sitting. A developer should be able to learn the whole of it in well under an
hour, because there is little to learn: one entry point, a handful of concepts
that flow through it, and two points of extension. Simplicity here is not a
matter of hiding complexity behind a friendly face; it is a matter of there being
genuinely few things to know.

**Discoverability.** The obvious way to use the library is the correct way. A
developer who follows the most natural path — construct the one entry point,
supply what it plainly requires, ask it to do the one thing it does — arrives at
correct usage without having to study documentation to find it. The API is shaped
so that its intended use is the use that suggests itself.

**Explicitness.** What the library needs, it asks for plainly, and what it does,
it does visibly. There are no hidden requirements satisfied by ambient state, no
behavior that depends on configuration a developer cannot see. The dependencies a
developer must supply are named and required; the call that performs work is a
call the developer makes deliberately.

**Immutability.** Everything the API hands back is immutable. A result, a chunk, a
prepared document — once received, none of them changes, and none of them can be
changed by the developer who holds them. This makes values safe to pass around,
safe to share across threads, and safe to reason about, because a value observed
at one moment is the same value at every later moment.

**Stability.** The public surface is a long-term commitment. Code written against
an early version is expected to keep working, and the API is designed so that it
can grow without breaking what already exists. Stability is treated as a feature
of the API in its own right, because a library that infrastructure depends on
earns that trust only by not disturbing its dependents.

**Minimal public surface.** Every public type earns its place by being something a
developer genuinely needs to touch. The default is that a concept is internal;
becoming public is the exception, made only when developers must interact with the
concept directly. A small surface is easier to learn, harder to misuse, and — 
because everything public is a permanent contract — cheaper to maintain honestly
over many years.

**Composition over configuration.** A developer assembles the library from a few
explicit pieces — an extractor and a model — rather than steering it through a
wide field of settings. Behavior is determined by which pieces are supplied, not
by tuning knobs, and the pieces themselves are the units of variation. This keeps
the API small and keeps its behavior predictable, because there are few
combinations to reason about and each is explicit.

**Predictable behavior.** The same inputs and the same configuration produce the
same behavior from the API. There is no dependence on hidden state, on the order
in which unrelated calls happen to be made, or on the environment the library
finds itself in. A developer can reason about what a call will do from the call
itself.

These principles matter because they compound. A small, explicit, immutable,
stable API is one a developer can trust with a part of their system and then stop
thinking about, which is precisely the relationship the project wants its users to
have with it.

---

## 2. Primary Entry Point

The library has one obvious entry point: a single type through which all ordinary
use flows. A developer who wants to chunk a document constructs this one thing,
supplies it with what it needs, and asks it to chunk. There is no second way in
and no alternative front door to weigh against it.

```
SemanticChunker chunker = SemanticChunker.builder()
        .documentExtractor(extractor)
        .chunkingModel(model)
        .build();

ChunkingResult result = chunker.chunk(document);
```

That is the whole of the entry point in its usual form: build the chunker, chunk
the document, receive the result.

The API avoids multiple competing entry points on purpose. When a library offers
several ways to begin, a developer's first task is not to use it but to choose
among the ways to use it — to work out which entry point is intended for their
situation, how the entry points differ, and whether they have picked the right
one. That choice is a cost, and it is a cost paid before any value is received.
By providing exactly one entry point, the library removes that decision entirely.
The question "how do I start?" has a single answer, and the developer spends their
attention on their task rather than on navigating the library's front matter.

The single entry point is also what makes the rest of the API cohere. Because
everything flows through one type, that type is the place a developer looks, the
place the documentation centers on, and the anchor from which the handful of other
public concepts are reached. One entry point is not merely a convenience; it is
the organizing principle that keeps a small API feeling like a whole rather than a
collection.

---

## 3. Builder Philosophy

The entry point is constructed through a builder, and the choice of a builder is
deliberate.

Construction has to distinguish between what the library requires and what a
developer may optionally provide. The library cannot function without an extractor
and a model; these are required, and construction must not succeed without them.
Other choices, where they exist, are optional refinements with sensible behavior
when left unspecified. A builder expresses this distinction naturally: the
required pieces are supplied as named steps, the optional ones are supplied only
when wanted, and the moment of completion is an explicit act that can check that
everything necessary is present.

```
SemanticChunker chunker = SemanticChunker.builder()
        .documentExtractor(extractor)   // required
        .chunkingModel(model)           // required
        .build();                       // fails fast if anything required is missing
```

The builder is chosen for four reasons beyond this basic fitness.

**Readability.** A builder reads as a sequence of named intentions. Each piece
supplied is labeled by the step that supplies it, so a developer reading
construction code sees plainly what the chunker is being given, without having to
recall the meaning of positional arguments or decode a dense constructor.

**Required dependencies are enforced at construction.** Completion is where the
library confirms that everything it needs is present, and it does so immediately
and clearly. A chunker cannot come into existence lacking an extractor or a model;
the attempt fails at the point of construction, with a plain indication of what
was missing, rather than surfacing later as a confusing failure during use. This
is the fail-fast discipline the project holds itself to, expressed at the
API's front door.

**Future evolution.** A builder can accommodate new optional configuration by
gaining a new optional step, without disturbing any existing use. Developers who
never touch the new step are entirely unaffected. This gives the API room to grow
in the one direction growth is acceptable — additively — while keeping the common
case exactly as it was.

**Backward compatibility.** Because optional refinements are supplied through
optional steps rather than through the shape of a constructor, adding one does not
change any existing call. Construction code written against an earlier version
continues to compile and behave identically. The builder is the mechanism by which
the API can evolve while honoring its promise of stability.

---

## 4. Public Concepts

A developer interacts with a small, enumerable set of concepts. They fall into
three groups: the entry point, the values that flow through it, and the two
extension points.

**SemanticChunker.** The entry point described above — the one type a developer
constructs and calls. It is what a developer holds and uses; everything else is
either something they hand to it or something they receive from it.

**PreparedDocument.** The normalized representation of a document, defined in the
model. It is public because a developer may wish to work with it directly in
advanced scenarios — to obtain it from an extractor and hand it to the chunker
separately — rather than always letting the chunker produce it internally. For
ordinary use it need never be handled; it is available for those who want the
finer-grained path.

**DocumentUnit.** The atomic, addressable element of a `PreparedDocument` and the
member of a closed, sealed hierarchy — `Heading`, `Paragraph`, `ListItem`, `Table`,
`Image`, and `PageBreak`. It is public as part of `PreparedDocument`: a developer
inspecting a prepared document reads its units. Ordinary use never requires handling
it directly, but it is on the public surface because the prepared document is.

**DocumentSource.** The honest input type for extraction — the raw content together
with its media type and an optional filename. It is what identifies and carries a raw
document into the library, and it exists because extraction cannot route an input
without knowing its media type. A developer may supply it directly or rely on a
convenience overload that builds it from a `Path`.

**ChunkingResult.** The value returned from chunking: the finished chunks together
with the account of how they were produced. It is what a developer receives and
inspects. It is richer than a bare list because, as the model establishes, a
developer operating the library in a real system needs to know not only what was
produced but how.

**SemanticChunk.** The individual unit of output — one coherent, self-contained,
traceable section of the document. A developer reads chunks out of the result and
passes them on to whatever consumes them.

**DocumentExtractor.** One of the two extension points: the contract a developer
implements, or an existing implementation a developer supplies, to determine how
documents are turned into structured content. A developer interacts with it by
choosing which extractor to give the chunker.

**ChunkingModel.** The other extension point: the contract that determines how the
language model is reached. A developer interacts with it by choosing which model
to give the chunker.

**The exception hierarchy.** The typed, unchecked exceptions the library raises when
work genuinely cannot complete — extraction failure, model failure, and chunking
failure. They are public because a developer catches them to tell a real,
non-recoverable failure apart from the recoverable difficulties the library absorbs
and records in the result rather than raising (see [§8](#8-error-handling)).

Everything else remains internal. The stages that plan windows, construct prompts,
execute the model, validate responses, and merge boundaries are not part of the
public surface; a developer neither sees nor touches them. What is public is what a
developer needs to hold, hand over, or receive — and nothing more. The complete
list above is genuinely complete, which is what allows the whole public surface to
be understood quickly.

---

## 5. Typical Usage

The common workflow is short, and it follows one path:

- **create the chunker** through its builder;
- **provide an extractor**, choosing how documents become structured content;
- **provide a model**, choosing how the language model is reached;
- **chunk a document**, handing it to the chunker;
- **receive a result**, and read the chunks from it.

In practice this is a few lines:

```
SemanticChunker chunker = SemanticChunker.builder()
        .documentExtractor(extractor)
        .chunkingModel(model)
        .build();

ChunkingResult result = chunker.chunk(document);

for (SemanticChunk chunk : result.chunks()) {
    // hand each chunk to whatever consumes it:
    // an embedding step, a store, an index
}
```

The chunker, once built, is a settled thing. It carries the extractor and model it
was given and can be used to chunk one document after another; it need not be
rebuilt for each document. A developer constructs it once, keeps it, and calls it
whenever a document needs chunking. The workflow is deliberately unremarkable:
build, chunk, read. That there is little more to say about ordinary use is the
intended outcome.

---

## 6. Advanced Usage

Beyond the common path, the API supports a few advanced scenarios. They exist for
developers whose needs are more specific, and they are reached through the same
small set of concepts rather than through a separate advanced interface.

**Working with prepared documents directly.** Ordinarily the chunker extracts and
normalizes a document internally, and the developer never handles the intermediate
representation. A developer who wants finer control can instead obtain the prepared
document themselves — extracting it in one step — and hand it to the chunker in a
second step:

```
PreparedDocument prepared = extractor.extract(source);

// inspect or retain the prepared document, then:
ChunkingResult result = chunker.chunk(prepared);
```

This is useful when a developer wants to inspect the normalized document before
chunking, to chunk the same prepared document more than once, or to separate the
cost of extraction from the cost of chunking in their own pipeline. The chunker
accepts a prepared document as readily as it accepts a raw one; the difference is
only where the boundary between extraction and chunking falls in the developer's
own code.

**Custom extractors.** A developer whose documents come from a source the provided
extractors do not cover can supply their own extractor. They implement the
extraction contract — turning their kind of source into the normalized
representation the model defines — and hand their implementation to the chunker
exactly as they would hand it a provided one. Nothing else about their usage
changes; the custom extractor takes the place of a provided one in the same slot.

**Custom model adapters.** Likewise, a developer who wishes to reach a language
model that the provided adapters do not cover can supply their own. They implement
the model contract and give their implementation to the chunker in the model slot.
This is how the library remains open to providers it does not ship support for: the
model slot accepts any implementation of its contract, whether provided or written
by the developer.

These scenarios are advanced only in that most developers will not need them. They
require no additional machinery and introduce no additional concepts; they are the
same building blocks, composed with a little more deliberateness.

---

## 7. Configuration Philosophy

The library's approach to configuration is to have very little of it, and to make
what little there is explicit.

Configuration is **explicit** where it exists. A developer chooses the extractor
and the model by supplying them; these are the principal decisions, and they are
made plainly at construction rather than inferred or defaulted from the
environment. Where further options exist, they too are supplied deliberately, so
that the behavior of a chunker follows from what the developer visibly gave it.

Configuration comes with **sensible defaults**. The choices a developer does not
make are made for them in a reasonable way, so that the common case requires
supplying only what genuinely must be supplied. A developer is not obliged to
express an opinion about matters they have no opinion about; silence yields
sensible behavior.

There are deliberately **few options**. The API does not present a broad field of
settings to be surveyed and tuned. Few options mean few decisions, few
combinations to understand, and few ways to configure the library into a state its
author did not anticipate. The narrowness is intentional; it is part of what keeps
the API small and its behavior predictable.

Configuration is offered **without complexity**. What options exist are simple and
independent, not a web of interacting switches whose combined effect is hard to
foresee. A developer can understand each option on its own and can predict what
setting it will do.

Above all, the API does not expose the internal settings of the pipeline. The
sizes of windows, the degree of overlap, the wording of prompts, the details of
validation and retry — these are not configuration a developer supplies. They are
the library's own responsibility, decisions it makes as part of doing its job
well, and exposing them would be exposing the library's method rather than
configuring its use. As the architecture establishes, the library is configurable
in *what* it works with — the extractor and the model — and fixed in *how* it
performs semantic chunking. Configuration honors that line. To open the pipeline's
internal settings to developers would be to turn implementation decisions into
public contracts, to invite configurations the library was never designed to
handle, and to make the API large in exactly the way it is trying not to be.

---

## 8. Error Handling

Because the library reaches outside itself to a language model, failure is a
normal condition rather than an exceptional one, and the API's error philosophy is
built around that fact. A developer should be able to predict how the library
behaves when things go wrong, because how it behaves when things go wrong is part
of what they are depending on.

**Validation happens early and plainly.** Where a developer supplies something the
library cannot use — an incomplete construction, an input it cannot make sense of —
the library rejects it promptly and with a clear indication of what was wrong,
rather than proceeding and failing obscurely later. Problems a developer can fix
are surfaced at the point where the developer can still fix them.

**Recoverable failures are recovered, not raised.** The library distinguishes
between failures that are localized and recoverable and failures that are
fundamental. A difficulty affecting one part of a document — a portion the model
could not resolve cleanly — is handled by the library's own recovery and fallback,
so that a single troublesome part does not fail the processing of an entire
document. A developer chunking a large document does not have the whole run
abandoned because one section proved difficult.

**Exceptions are clear and reserved for genuine failure.** When the library does
raise an error, it does so because something genuinely prevented the work from
completing — not as a routine signal, and not for a difficulty it could have
recovered from. The errors it raises carry enough context for a developer to
understand what failed and where. A developer can therefore treat an exception from
the library as meaningful: it indicates a real, non-recoverable problem, not a
recoverable hiccup dressed as a failure.

**Behavior is predictable.** The distinction between what is recovered and what is
raised is stable and knowable in advance. A developer can rely on the library to
handle recoverable trouble the same way every time and to raise only when the same
kinds of genuine failure occur. There are no surprises in which category a given
situation falls into.

**The result carries warnings.** Recovery is not silent. When the library handles a
difficulty by falling back rather than resolving it cleanly, it records the fact in
the result it returns. A developer who wishes to know whether a run was entirely
clean can inspect the result and find out — which windows, if any, were recovered,
and what anomalies arose. This is why the result is richer than a bare list of
chunks: it is how the library reports what happened without forcing a failure the
developer did not need. A developer should therefore expect to find, in the result,
an honest account of the run alongside its output, and should consult it when the
cleanliness of a run matters to them.

---

## 9. Extension Points

The library has exactly two public extension points, and this number is a
deliberate and load-bearing part of the API's design.

The first is **DocumentExtractor**, through which a developer determines how
documents are turned into structured content. The second is **ChunkingModel**,
through which a developer determines how the language model is reached. These two
are extensible because they must be: the library does not itself parse documents
or run models, and it cannot anticipate every source a document might come from or
every provider a developer might use. Making these two points open is what allows
the library to remain independent of any particular extraction technology or model
provider, and what lets a developer bring their own where the provided
implementations do not suffice.

Everything else remains internal, and this is equally deliberate. The stages by
which the library performs semantic chunking are not extension points; a developer
cannot replace how windows are planned, how prompts are formed, how responses are
validated, or how boundaries are merged. These are the substance of what the
library does — its understanding of how the work should be performed — and they
depend on one another through invariants that outside code could not be trusted to
preserve. Opening them would mean the library no longer defines its own behavior,
would expose invariants to violation from outside, and would turn every internal
stage into a permanent public contract. The API stays open exactly where openness
serves the developer and closed everywhere else, because under-exposing the
internals is what lets the library both improve its methods over time and keep the
promises it has made.

Two extension points are enough because they are the only two places where the
variation genuinely belongs to the developer. What document source to use and what
model to use are the developer's decisions; how to chunk is the library's. The
extension model draws the line exactly there.

---

## 10. API Invariants

The API makes a set of guarantees that hold across every use and every version.
These are the promises a developer may rely upon, and the library is designed so
that they remain true.

**The same input yields the same API behavior.** Given the same document, the same
extractor, and the same model, the library behaves the same way each time it is
used. Its behavior follows from what it is given, not from hidden state or from the
environment. The one element that is not itself deterministic — the language
model's own output — is the model the developer supplied, and its variability
belongs to it, not to the API around it.

**Results are immutable.** Every value the API returns is fixed once returned. A
result and the chunks within it do not change and cannot be changed. A developer
may hold them, share them, and examine them freely, confident that what they
observed remains what they hold.

**Contracts are stable.** The public types and the two extension contracts are
long-term commitments. Code written against them is expected to keep working, and
the meaning of what is public does not shift beneath a developer who has come to
rely on it.

**Evolution is forward-compatible.** The API grows by addition, in ways that do not
disturb existing use. A developer who does not reach for a newly added capability
is unaffected by its arrival. New ability arrives without asking existing code to
change.

**The public surface stays minimal.** The API remains small. New public types are
added only when developers genuinely need them, and the surface does not swell over
time with concepts that exist for the library's convenience rather than the
developer's need. The promise that the whole API can be understood quickly is a
promise the library intends to keep as it evolves.

---

## 11. What the Public API Deliberately Does Not Expose

The API is defined as much by what it withholds as by what it offers. Several parts
of the library that a developer might imagine reaching are deliberately not part of
the public surface, and their absence is a design decision.

**Window planning** is not exposed. How a document is divided into windows, how
large those windows are, and how much they overlap are decisions the library makes
for itself. A developer neither sets nor sees them.

**Prompt generation** is not exposed. The instruction given to the language model
is the library's own, fixed by the library so that its behavior is defined by
itself rather than by whatever a caller might supply. A developer does not compose
or alter it.

**Boundary merging** is not exposed. How the conclusions drawn over separate
windows are reconciled into a single coherent set is internal machinery of the most
delicate kind, and it is not something a developer configures or replaces.

**Response validation** is not exposed. How the model's output is checked and how
recovery proceeds when it is malformed are the library's responsibility, carried
out on the developer's behalf without being surfaced as things to configure.

**The pipeline stages** in general are not exposed. The ordered sequence of
internal work by which a prepared document becomes chunks is not a public
structure; a developer sees the entry point and the values, not the stages between
them.

**Provider-specific behavior** is not exposed. The API behaves the same regardless
of which model provider or extraction technology stands behind its extension
points. Nothing peculiar to a provider surfaces in the public API; whatever is
specific to a provider stays behind the extension point that provider satisfies.

These remain implementation details for a consistent reason. Each is part of *how*
the library performs semantic chunking rather than *what* the developer works with,
and the API's guiding line is to expose the latter and conceal the former. Making
any of them public would convert a private decision the library is free to improve
into a permanent contract it must preserve, would enlarge the surface a developer
must learn, and would expose the invariants these stages depend upon to
interference from outside. By keeping them internal, the library retains the
freedom to refine its methods over the years without disturbing anyone, which is
exactly the freedom a long-lived project needs and exactly what a stable public API
is meant to protect.

---

## 12. API Evolution

The API is meant to last, and how it changes over time is therefore as much a part
of its design as how it looks today. The governing attitude is that every public
type is a long-term commitment, and that the surface should remain something a
developer can understand quickly no matter how long the project lives.

**Compatibility is the default.** The first consideration in any change is whether
existing code continues to work. Changes that would break code written against the
API are avoided wherever an additive alternative exists, and the additive
alternative is nearly always preferred. A developer who adopted the library should
be able to move forward through its versions without their code being invalidated.

**Additions are small and deliberate.** When the API grows, it grows by small,
considered additions that serve a genuine developer need, not by accumulation. Each
addition is weighed against the cost it imposes — a larger surface to learn and a
new contract to honor forever — and admitted only when that cost is justified. The
bar for adding to the public surface is high precisely because the surface is the
product.

**Deprecation is gentle and honest.** Where something must eventually be
withdrawn, it is marked clearly and well in advance, so that developers have time to
move away from it before it goes. Nothing public disappears abruptly beneath a
developer who was relying on it; the path away from a deprecated thing is signposted
before the thing is removed.

**Breaking changes are a last resort.** The API is designed to make breaking changes
rarely necessary, and when one is genuinely unavoidable it is reserved for a
deliberate, well-communicated moment rather than introduced quietly. The library
treats breaking its dependents as a serious cost, not a routine step.

**Understandability is preserved.** Through all of this, the API is kept small
enough to be learned quickly. Evolution that would swell the surface beyond what a
developer can hold in mind is resisted, because the smallness of the API is one of
its most valuable properties and one the project intends to preserve for as long as
it exists.

The through-line is that every new public type becomes a commitment the project
must keep for years. The API evolves in full awareness of that weight: it prefers
to add little, to add carefully, to break almost nothing, and to remain, release
after release, an API a developer can understand in an afternoon and depend on for a
decade.
