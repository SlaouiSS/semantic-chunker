# PIPELINE.md

*The execution flow of a chunking operation.*

This document explains how the `semantic-chunker` library processes a document
from input to output — the lifecycle of a single chunking operation, stage by
stage, from the moment a document enters until a result leaves. A reader who
finishes it should understand exactly how the library works internally, and yet
know nothing about how it is implemented.

It is not architecture documentation, API documentation, or an implementation
guide. It describes no classes, no algorithms, and no code. Where the architecture
document describes the *structure* of the system and the model document describes
the *concepts* that flow through it, this document describes the *execution* — the
ordered process by which those concepts are produced and transformed. It assumes
both of those documents and adds the dimension of time: what happens, in what
order, with what handed from each step to the next.

The concepts named here — prepared document, units, windows, boundary decisions,
chunks, result — are defined in the model document and are used here without
redefinition. The stages named here correspond to the pipeline described in the
architecture document and are examined here from the standpoint of what each does
when a document runs through it.

---

## Table of Contents

1. [Pipeline Philosophy](#1-pipeline-philosophy)
2. [Pipeline Overview](#2-pipeline-overview)
3. [Stage Responsibilities](#3-stage-responsibilities)
4. [Data Transformations](#4-data-transformations)
5. [Pipeline Guarantees](#5-pipeline-guarantees)
6. [External Boundaries](#6-external-boundaries)
7. [Error Flow](#7-error-flow)
8. [Pipeline Determinism](#8-pipeline-determinism)
9. [Pipeline Invariants](#9-pipeline-invariants)
10. [What the Pipeline Deliberately Does Not Do](#10-what-the-pipeline-deliberately-does-not-do)
11. [Pipeline Evolution](#11-pipeline-evolution)

---

## 1. Pipeline Philosophy

The library performs its work as a pipeline: an ordered sequence of stages, each
with a single responsibility, each consuming the output of the stage before it and
producing the input of the stage after it. This is not an incidental way of
organizing the code; it is the correct shape for the problem, and it is worth being
explicit about why.

**Single responsibility.** Turning a document into semantic chunks is not one task
but several distinct ones: interpreting a file, normalizing its content, dividing
it to fit the model, asking the model where it should be cut, checking the answers,
reconciling them, and assembling the result. These are genuinely different concerns,
and a pipeline gives each its own stage. Every stage does one thing, which makes
each one simple to understand on its own and simple to reason about in isolation.
The complexity of the whole is managed by never requiring any single stage to hold
more than its own concern.

**Ordered stages.** The stages run in a fixed order because the problem itself is
ordered. Each step depends on something the previous step established: normalization
needs extracted content, planning needs a normalized document, the model needs
windows, merging needs validated decisions, chunks need merged boundaries. The order
is not a convenience of arrangement; it is a chain of dependency, and expressing the
work as an ordered pipeline makes that chain explicit and enforceable.

**Deterministic orchestration.** The sequence of steps is fixed and repeatable.
Faced with the same document and the same configuration, the pipeline does the same
things in the same order every time. The one part of the work whose outcome is not
fixed — the language model's answer — is confined to a single stage, so that the
process surrounding it is entirely predictable even though that one stage is not.
This gives the library a stable backbone: a known procedure, with variability
quarantined to the one place it genuinely lives.

**Immutable transitions.** Information passes from stage to stage as immutable
values. A stage receives a representation, produces a new one, and never alters what
it was handed. There is no shared, mutable workspace that several stages read and
write. This makes the flow of information visible — the only way a stage influences
a later one is through the value it returns — and it removes the possibility that one
stage silently disturbs what another is relying upon.

**Explicit processing.** Nothing happens implicitly. Each stage's work is a
deliberate step with a defined input and a defined output, and the handoffs between
stages are definite rather than diffuse. There is no hidden coordination and no
action taken out of sight; the process is exactly the sequence of stages, and the
sequence of stages is the whole process.

A pipeline is the correct architecture because it matches the problem's own
structure — ordered, separable, dependent stages — and because it turns a
complicated transformation into a series of simple, verifiable steps whose
correctness can be reasoned about one at a time and whose composition is
predictable.

---

## 2. Pipeline Overview

A single chunking operation proceeds through the following lifecycle. Each arrow is
a handoff of an immutable representation from one stage to the next.

```
Document
   ↓   Extraction          — interpret the raw file into structured content
   ↓   Normalization       — resolve that content into one uniform representation
Prepared Document
   ↓   Window Planning      — divide the document into overlapping windows
   ↓   Prompt Construction  — turn each window into a request for a boundary decision
   ↓   LLM Execution        — obtain the model's response for each window
   ↓   Validation           — confirm each response is well-formed and usable
   ↓   Boundary Merging     — reconcile per-window decisions into one coherent set
   ↓   Chunk Construction   — build the chunks from the merged boundaries
Chunking Result
```

The lifecycle begins with a raw document supplied by the caller and ends with a
result handed back to the caller. In between, the document is first brought into
the library's own terms — extracted and normalized into a prepared document — and
then chunked: divided into windows, reasoned about window by window, reconciled, and
assembled. The prepared document is the pivot of the lifecycle: before it, the work
is concerned with the outside world and the file that came from it; after it, the
work is concerned only with the library's own concepts.

The role of each stage, in brief: extraction interprets the file; normalization
makes it uniform; window planning makes it fit; prompt construction frames the
question; execution asks it; validation checks the answer; merging reconciles the
answers; chunk construction produces the output; and the result gathers that output
with the account of how it was made. The next section examines each in turn.

---

## 3. Stage Responsibilities

Each stage is described below by what it is responsible for, what it receives, what
it produces, why it exists, and what it guarantees to the stage that follows. The
guarantees are the important part: they are what allow each stage to trust its
input and to concern itself only with its own work.

### Extraction

**Responsibility.** To interpret the raw document, turning an opaque file into
structured content — the text it contains together with whatever structural signals
the extraction technology can recover.

**Input.** The raw document supplied by the caller.

**Output.** Structured content, as recovered by the chosen extraction technology.

**Why it exists.** The library does not parse files itself; it delegates that solved
problem to a mature external technology. Extraction is the stage where that
delegation happens, and it is one of the two points where the pipeline reaches
outside the library.

**Guarantees.** That what it hands onward is the content the extraction technology
was able to recover, obtained through the library's own extraction abstraction so
that the specific technology remains invisible to everything downstream.

### Normalization

**Responsibility.** To convert the extracted content — whose shape and richness vary
by technology — into the single uniform representation the rest of the pipeline
consumes, and to establish the ordered units of the document and their stable
identities.

**Input.** The structured content produced by extraction.

**Output.** The prepared document: an ordered collection of units, each with a
stable identity and its provenance.

**Why it exists.** It is the stage that makes the pipeline self-contained. It absorbs
the differences between extraction technologies so that no later stage must account
for them, and it establishes the identities and order on which every later stage
depends.

**Guarantees.** That downstream stages receive one uniform representation regardless
of how the document was extracted; that every unit carries a stable identity and a
fixed position; and that provenance has been recorded for every unit. After this
stage, nothing reaches back to the file or the extraction technology.

### Window Planning

**Responsibility.** To divide the prepared document into windows — contiguous views
sized to fit what the model can consider at once — with deliberate overlap between
adjacent windows, reserving room within each for the model's own response. The
context-window limit it sizes against, and the token estimates it uses to do so,
are obtained from the core's model abstraction, which reports them for the model in
use.

**Input.** The prepared document.

**Output.** A set of overlapping windows over the document's units.

**Why it exists.** A document may be larger than the model can handle in one pass.
This stage makes documents of any size processable without asking the caller to
divide them.

**Guarantees.** That every part of the document is covered by at least one window;
that adjacent windows overlap so that regions near an edge are also seen with
context on both sides; and that the units a window presents keep the identities they
were given at normalization, so that windows are faithful views and never new
content.

### Prompt Construction

**Responsibility.** To turn each window into a well-defined request for a boundary
decision, presenting the window's content in the form the model is asked to reason
about, using the library's own fixed instruction.

**Input.** A window over the document's units.

**Output.** A request suitable for execution against the language model.

**Why it exists.** The model must be asked a precise question about a precise
portion of the document. This stage frames that question, and it does so using the
library's fixed instruction so that the library's behavior is defined by itself
rather than by the caller.

**Guarantees.** That each request is well-formed and expresses the boundary question
over the units of exactly one window, framed identically for every window.

### LLM Execution

**Responsibility.** To send each request to the language model and obtain its
response, through the library's own model abstraction.

**Input.** A request produced by prompt construction.

**Output.** The model's raw response for that request.

**Why it exists.** This is where the semantic judgment happens — where the model,
having read a window, indicates where the document should be divided. It is the
second and last point where the pipeline reaches outside the library.

**Guarantees.** That the response returned is the model's answer to the request it
was given, obtained through the model abstraction so that no provider-specific
knowledge exists here. This is the only stage whose output is not deterministic, and
that non-determinism is confined to it.

### Validation

**Responsibility.** To confirm that each raw response is well-formed and refers to
genuine units of the document, turning a raw response into a validated boundary
decision — or, where a response cannot be made valid, invoking recovery and, if
necessary, the failure policy.

**Input.** The model's raw response for a window.

**Output.** A validated boundary decision for that window.

**Why it exists.** The model's output cannot be trusted merely because it was
returned. This stage is the guardian that ensures only trustworthy decisions proceed,
so that everything downstream can rely on the integrity of what it receives.

**Guarantees.** That every decision passed onward is well-formed and references only
real units; and that a response which cannot be validated is handled by recovery or
by the failure policy rather than being allowed to corrupt the result.

### Boundary Merging

**Responsibility.** To reconcile the validated per-window decisions into a single
coherent set of boundaries over the whole document, resolving disagreements within
the overlapping regions where two windows both had an opinion.

**Input.** The validated boundary decisions from all windows.

**Output.** One coherent set of boundaries over the entire document.

**Why it exists.** Because the document was processed as several overlapping windows,
the decisions arrive in separate, overlapping pieces. This stage restores the
document to a single whole, so that the fact of windowing leaves no trace in the
result. It is the most delicate stage in the pipeline.

**Guarantees.** That the merged boundaries form one consistent account of where the
whole document should be divided, expressed against the units' stable identities;
and that no artifact of the windowing survives into what it produces. This stage
depends absolutely on the stable identity of units, which is what lets it recognize
decisions from different windows as referring to the same places.

### Chunk Construction

**Responsibility.** To build the semantic chunks from the merged boundaries and the
document's units — each chunk the contiguous run of units lying between two
boundaries.

**Input.** The merged set of boundaries and the prepared document's units.

**Output.** The ordered semantic chunks.

**Why it exists.** The merged boundaries describe where the document should be
divided; this stage carries out that division, producing the coherent,
self-contained, ordered, traceable chunks that are the library's primary output.

**Guarantees.** That each chunk is composed of a contiguous run of units, in
document order, carrying the provenance of those units; and that the chunks together
cover the document in sequence.

### Result Assembly

**Responsibility.** To gather the finished chunks together with the account of how
they were produced into the chunking result handed back to the caller.

**Input.** The semantic chunks and the record of the run, including any recoveries or
fallbacks that occurred.

**Output.** The chunking result.

**Why it exists.** A library that reaches outside itself and can recover from
difficulty must report not only what it produced but how. This stage produces the
complete, honest record of the run.

**Guarantees.** That the result carries the chunks in order together with the
warnings and processing information describing the run, so that the caller can learn
whether the run was clean and what it involved.

---

## 4. Data Transformations

Seen from the standpoint of the data rather than the stages, a chunking operation is
a succession of immutable representations, each derived from the one before:

```
Document            — an opaque file
   ↓
Prepared Document   — one uniform, ordered representation with stable unit identities
   ↓
Windows             — overlapping views over the units (no new content)
   ↓
Boundary Decisions  — validated conclusions expressed against unit identities
   ↓
Chunks              — contiguous sections composed of units, in order
   ↓
Result              — the chunks together with the record of the run
```

Every arrow is a transformation of one immutable representation into another. A stage
never modifies the representation it receives; it reads that representation and
produces a new one. The prepared document is not altered when it is divided into
windows — the windows are new views over its unchanged units. The units are not
altered when decisions are made about them — the decisions are new values that refer
to them. The chunks do not alter the units they are composed from — they are new
values that gather them. And the result does not alter the chunks — it contains them.

This chain of immutable transformations is what makes the operation traceable from
end to end. Because nothing is overwritten, each representation remains a faithful
record of its stage, and the path from the final chunks back to the original units,
and through their provenance to the source document, is preserved unbroken. The
data does not travel through the pipeline being reshaped in place; it is rebuilt at
each step into a new form, with the earlier forms intact behind it.

---

## 5. Pipeline Guarantees

Each stage establishes certain guarantees before handing work to the next, and each
stage in turn relies on the guarantees of the one before it. These guarantees are
the contract of the pipeline — the reason each stage can concern itself only with
its own work and trust that its input is sound.

**Validated input.** By the time a stage receives its input, the input has been made
fit for that stage by the stages before it. Normalization delivers a uniform
representation, not raw extraction output; merging receives validated decisions, not
raw responses; chunk construction receives reconciled boundaries, not scattered
per-window opinions. No stage must defend against malformed input from within the
pipeline, because each stage guarantees the soundness of what it produces.

**Stable identities.** From normalization onward, every unit carries an identity that
does not change. Every stage that refers to a unit refers to it by this identity, and
every stage can rely on an identity meaning the same thing wherever it appears. This
guarantee, established once at normalization, is depended upon most heavily by
merging, which could not reconcile overlapping windows without it.

**Ordered units.** The order of the units, fixed at normalization, holds through
every stage. Windows present units in order, decisions respect it, chunks reproduce
it. A stage can rely on the sequence it observes being the true sequence of the
document.

**Well-defined transitions.** The handoff from each stage to the next is a definite
transition from one representation to another, with a clear meaning. A stage knows
exactly what form its input takes and exactly what form its output must take. There
are no ambiguous or partial handoffs in which a stage must guess at the state of what
it received.

Downstream stages rely on these guarantees because they are what make the pipeline
composable. Each stage is written to a simple contract: given sound input of a known
form, produce sound output of a known form. Because every stage honors that contract,
the stages can be assembled into a whole that is correct by construction, with each
step trusting the last. Remove any guarantee and the stage that relied on it would
have to compensate for its absence, and the clean separation that makes the pipeline
tractable would begin to dissolve.

---

## 6. External Boundaries

The pipeline touches the outside world in exactly two places, and nowhere else.

The first is **document extraction**, where the pipeline relies on an external
technology to interpret a raw file into structured content. The second is **language
model execution**, where the pipeline relies on an external model to make the
boundary decisions. These are the only two stages that reach beyond the library;
every other stage — normalization, window planning, prompt construction, validation,
merging, chunk construction, result assembly — is carried out entirely within the
library, using nothing outside itself.

Both external boundaries are crossed only through abstractions the library owns. The
pipeline does not reach a particular extraction technology or a particular model
provider directly; it reaches an abstraction, and an adapter satisfies that
abstraction with a concrete technology. This is what keeps the two external boundaries
isolated: the specifics of any extraction technology and any model provider live
behind the abstraction, in the adapter, and never enter the pipeline itself. The
pipeline knows that it obtains structured content and that it obtains boundary
responses; it does not know, and is written so that it cannot know, which technology
or provider furnished them.

Keeping these boundaries isolated matters for the same reason it matters throughout
the project: the external technologies are the parts most likely to change, and the
pipeline is insulated from their change by never depending on any of them directly. A
new extraction technology or a new provider is a new adapter behind an existing
boundary, not a change to the pipeline. The pipeline has exactly two doors to the
outside, both narrow, both guarded by abstractions, and everything of value in
between belongs wholly to the library.

---

## 7. Error Flow

Because the pipeline reaches outside itself to a language model, difficulty is a
normal part of its operation rather than an exceptional event. Failures move through
the pipeline along a defined path, and understanding that path is part of
understanding how the library behaves.

**Validation.** The first line is validation, where each raw model response is
checked before it is allowed to proceed. A response that is well-formed and refers to
genuine units passes and becomes a validated decision. A response that is not enters
the recovery path rather than continuing as if it were sound. Validation is the point
at which trouble with the model's output is detected and contained, so that malformed
output never reaches merging.

**Recoverable failures.** A failure affecting a single window — a response the model
did not return cleanly — is treated as recoverable and localized. It does not
propagate to the rest of the document. The other windows continue to be processed; the
difficulty is confined to the window where it arose.

**Retry.** The first response to a recoverable failure is to ask again. The window is
re-presented to the model within defined limits, on the expectation that a fresh
attempt may yield a well-formed response where the first did not. Retry is bounded; it
does not continue indefinitely.

**Fallback.** When retry is exhausted and a window still cannot be resolved, the
pipeline falls back rather than failing. The unresolved window is handled by the
library's failure policy — treated in a defined, safe way that lets the operation
continue — instead of abandoning the whole document for the sake of one window. This
fallback is a defined error state, not an alternative method of chunking; it is how the
pipeline copes with a part it could not resolve.

**Warnings.** Whenever recovery or fallback occurs, the fact is recorded and carried
forward into the result. Recovery is never silent. The result that reaches the caller
carries an honest account of which parts of the document were resolved cleanly and
which were handled by fallback, so that a caller who needs to know the cleanliness of a
run can find out.

**Terminal failures.** Some failures are not recoverable and not local — a fundamental
inability to proceed, such as the model being unreachable or an input that cannot be
processed at all. These are terminal: the operation cannot complete, and the pipeline
stops and reports a genuine failure rather than returning a result. The pipeline
distinguishes clearly between such terminal failures, which end the operation, and
recoverable ones, which it absorbs and records.

The shape of the error flow is therefore a graduated response: detect at validation,
retry within limits, fall back and record when retry is exhausted, and reserve
termination for failures that genuinely prevent the work from completing. A single
troublesome window degrades and is noted; only a fundamental problem ends the run.

---

## 8. Pipeline Determinism

The pipeline draws a deliberate line between what is deterministic and what is not,
and the location of that line is important.

The **orchestration is deterministic**. Given the same prepared document and the same
configuration, the pipeline performs the same stages in the same order, plans the same
windows, frames the same requests, applies the same validation, and reconciles results
by the same rules. Every part of the process that the library itself controls is a
fixed, repeatable procedure. Presented with the same inputs, the library does the same
things.

The **model's output is not deterministic**. The one element the library does not
control is the language model's answer. The same request may yield different responses
on different occasions, and different models will answer differently still. This
variability is inherent to the model and cannot be legislated away by the library.

The significance of the distinction is that the non-determinism is confined to a single
stage. Everything around the model — how the document is prepared, how it is divided,
how the question is framed, how the answer is checked, how the answers are combined — is
a stable procedure. The model's variability enters at one point and is then subjected to
the same deterministic validation, recovery, and merging as any other response. The
library is therefore predictable in its behavior even though it depends on an
unpredictable component: its procedure is fixed, and only the content flowing through
one stage of that procedure varies.

This matters because it tells a developer exactly what they can and cannot rely upon.
They can rely on the library to behave the same way every time — to take the same steps,
apply the same checks, and handle trouble the same way. They cannot rely on the model to
return the same boundaries every time, because that is a property of the model, not of
the library around it. By confining non-determinism to the one stage where it genuinely
lives, the pipeline makes the boundary between "what the library guarantees" and "what
the model contributes" clear and honest.

---

## 9. Pipeline Invariants

Certain operational properties must hold every time the pipeline runs. They are the
conditions of its correctness; violating any one produces wrong results, sometimes
silently. These are invariants of the pipeline's operation; the invariants of the
architecture and of the data model are defined in their own documents.

**Stages are never reordered.** The pipeline runs its stages in the fixed sequence, and
no stage runs before the stage whose guarantees it depends upon. Because each stage is
built to assume the soundness of what the previous stage produced, reordering would feed
a stage input it is not prepared for, and the chain of guarantees would break.

**Transitions are immutable.** Every representation passed between stages is immutable,
and no stage alters what it received. If a stage were to mutate a shared representation,
another stage relying on that representation could observe it in an unexpected state, and
the traceable, predictable flow of information would be lost.

**Identities are stable.** The identity each unit is given at normalization holds
unchanged through every stage. That identity is the unit's global ordinal — a
stable, document-order value against which boundary decisions are expressed. If an
identity were to change between stages, references
made in one stage would no longer match the units in another, and — most damagingly —
merging would reconcile decisions that only appeared to refer to the same place.

**Units are never recreated.** The units established at normalization are the units used
throughout; no stage manufactures new units or replaces existing ones. Windows view the
existing units, decisions refer to them, chunks are composed from them. Recreating units
would sever the continuity of identity and provenance on which the whole operation
depends.

**Windows disappear.** Windows exist only for the duration of the work of obtaining and
validating decisions. Once merging has reconciled the decisions, the windows have served
their purpose and leave no trace in what follows. If any artifact of windowing survived
into the chunks or the result, the internal mechanism of division would have leaked into
the output, which must reflect the whole document as if it had never been divided.

**Boundaries are always validated.** Only validated boundary decisions proceed to
merging. A raw, unchecked response never reaches the stages that build the result. Were
an unvalidated decision to slip through, merging and chunk construction would build the
output on conclusions that might be malformed or refer to units that do not exist.

**Chunks are always built from validated boundaries.** The chunks are constructed from
the merged set of validated boundaries and from nothing else. A chunk never arises from a
decision that was not validated and reconciled. This is what guarantees that the output —
the thing the caller ultimately consumes — rests entirely on conclusions the library has
vouched for.

Each of these invariants protects a specific point of correctness, and together they are
what make the pipeline trustworthy: an ordered, immutable, identity-preserving process
that builds its output only from checked conclusions and leaves none of its internal
machinery behind.

---

## 10. What the Pipeline Deliberately Does Not Do

The pipeline's scope is defined as tightly by what it excludes as by what it performs.
Several things that often sit near semantic chunking in a larger system are deliberately
outside the pipeline, and their exclusion is a design decision rather than an omission.

The pipeline does not perform **embedding generation**. It produces chunks; it does not
turn them into vectors. Turning text into embeddings is a separate, well-served concern
that begins where the pipeline ends.

It does not perform **vector storage**. It neither stores nor manages the chunks it
produces or any representation of them. Where and how chunks are kept is the concern of
whatever consumes them.

It does not perform **retrieval**. It does not search over chunks or return them in
response to queries. Retrieval operates on stored, embedded chunks and belongs to a
later part of a larger system.

It does not perform **reranking**. It does not order or reorder results by relevance to a
query, because it does not deal in queries or results at all.

It does not perform **OCR**. It does not turn images or scanned pages into text. Where a
document requires such interpretation, that is the responsibility of the extraction
technology behind the extraction boundary, not of the pipeline.

It does not perform **search** or **indexing**. It does not build indexes over content or
answer searches against them. These operate on the output of the broader system, well
downstream of chunk production.

All of these belong outside the pipeline for one consistent reason: none of them is part
of turning a document into semantic chunks. Each is a distinct concern with its own
mature solutions, and each begins where semantic chunking leaves off. The pipeline is
built to do one thing and to hand its output cleanly to whatever comes next. Absorbing
any of these concerns would blur that boundary, enlarge the pipeline beyond its purpose,
and duplicate work that other tools already do well. The pipeline produces chunks and
stops; what happens to those chunks afterward is, by design, someone else's stage.

---

## 11. Pipeline Evolution

The pipeline is meant to last, and how it changes over time is part of its design. The
governing principle is that the pipeline improves from within rather than by growing
outward.

**New stages are exceptional.** The set of stages reflects the genuine structure of the
problem, and that structure is stable. Adding a stage is a rare and significant act,
undertaken only if the problem itself is found to require a step that none of the existing
stages can properly own. The default expectation is that the pipeline's sequence of stages
is complete and stays as it is.

**Existing stages evolve internally.** Improvement happens inside the stages. A stage may
become better at what it does — more capable, more robust, more refined — while remaining
the same stage with the same responsibility and the same place in the sequence. This is
where the pipeline's growth is expected to occur: in the depth of each stage's work, not in
the number of stages. Because a stage's contract to its neighbors is defined by the
representation it consumes and the one it produces, a stage can be improved freely so long
as it continues to honor that contract.

**Pipeline order remains stable.** The sequence of stages does not change as the library
evolves. The order is a chain of dependency and a matter of correctness, not a detail to be
rearranged, and it is treated as fixed. Developers and adapters can rely on the shape of the
process being the same over the long life of the project.

**The public API remains unaffected.** Because the stages are internal, their evolution is
invisible from the outside. A stage may be reworked entirely without any change to what a
developer sees or depends upon. The pipeline can be improved release after release while the
public surface stays exactly as it was, which is precisely what the separation between the
internal pipeline and the public API is for.

The intent is a pipeline whose shape is settled and whose quality is not. Its sequence of
stages, its external boundaries, and its invariants are meant to hold for the life of the
project; its stages, within those fixed bounds, are meant to keep getting better. Progress
is made by deepening the stages that exist, not by multiplying them — so that the library
can improve continuously while remaining, from the outside and in its overall shape, the
same coherent, deterministic process it has always been.
