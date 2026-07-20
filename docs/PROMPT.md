# PROMPT.md

*The prompt engineering philosophy.*

This document explains the principles that govern the prompts used by the
`semantic-chunker` library's chunking engine. Its purpose is not to document the
current prompt text, and it deliberately contains none of it. Prompt wording is an
internal implementation asset that changes over time; this document defines the
principles that every prompt must satisfy no matter how the wording evolves.

It is written for the engineers who maintain the library — the people who will
refine the prompts over the years and who need a fixed set of principles against
which to judge every change. It contains no prompt text, no templates, no
examples, and no provider-specific techniques. It concerns philosophy, not content:
why prompts exist, what they are responsible for, how they should evolve, and what
properties every production prompt must preserve.

The concepts it refers to — windows, units, boundary decisions, the pipeline
stages, the model abstraction — are defined in the model, pipeline, and SPI
documents. A prompt is the thing the pipeline's prompt-construction stage produces
and the model implementation carries to the model; this document describes the
principles behind that thing without describing the thing itself.

A maintainer who finishes this document should understand why prompts exist, what
they are responsible for, how they should evolve, and what properties every
production prompt must preserve — without ever seeing the actual prompts the
library uses.

---

## Table of Contents

1. [Prompt Philosophy](#1-prompt-philosophy)
2. [Prompt Responsibilities](#2-prompt-responsibilities)
3. [Prompt Design Principles](#3-prompt-design-principles)
4. [Prompt Lifecycle](#4-prompt-lifecycle)
5. [Provider Independence](#5-provider-independence)
6. [Prompt Stability](#6-prompt-stability)
7. [Prompt Validation](#7-prompt-validation)
8. [Prompt Invariants](#8-prompt-invariants)
9. [What Prompts Deliberately Do Not Do](#9-what-prompts-deliberately-do-not-do)
10. [Prompt Evolution](#10-prompt-evolution)

---

## 1. Prompt Philosophy

The prompt is treated as an internal component of the library — as much a part of
the library's own machinery as any stage of the pipeline, and no more exposed than
any of them. It is not something the caller supplies, sees, or influences. This is
a deliberate position, and it rests on what the prompt is: the precise expression of
how the library asks a model to make the one judgment the library cannot make
itself.

**Semantic consistency.** The quality of the library's output depends directly on
the prompt, because the prompt is what elicits the boundary decisions from which
chunks are built. For the library to produce consistent, high-quality chunks, the
way it asks for boundaries must itself be consistent. If the prompt were open to
variation from outside, the library's most important behavior would vary with it,
and the semantic consistency that is the whole point of the library would be lost.
Owning the prompt is how the library owns the consistency of its results.

**Predictability.** The library promises predictable behavior, and the prompt is
central to keeping that promise. A fixed, library-owned prompt means the library
asks the same question in the same way every time, so that the only variation in
outcome comes from the model's own answers and not from the question. A prompt that
callers could alter would introduce a second, uncontrolled source of variation and
make the library's behavior far harder to predict.

**Provider independence.** The prompt is written to express what the library wants,
in terms that are meaningful to any capable model, rather than to exploit the
peculiarities of any one provider. Keeping the prompt internal is what allows the
library to maintain this independence: a single owned prompt, expressed in
provider-neutral terms, serves every provider behind the model abstraction, whereas
a prompt shaped by callers or tuned to one provider would erode the independence the
architecture depends on.

**Stable behavior.** Because the prompt is internal, the library can refine it over
time without any caller's involvement and without disturbing the library's observable
behavior. The prompt improves as an implementation detail improves — quietly,
continuously, invisibly — while the library continues to do the same thing from the
outside.

Prompt engineering belongs to the library rather than the caller for the same reason
that the pipeline's other internal stages do: it is part of *how* the library performs
semantic chunking, which is the library's own substance. The caller chooses what to
chunk and which model to use; the library decides how to ask the model to chunk. The
prompt is the concentrated expression of that "how," and handing it to the caller
would be handing away the library's defining responsibility. As the API and pipeline
documents establish, the prompt is not an extension point and is not exposed; this
document explains the reasoning behind that decision from the prompt's own side.

---

## 2. Prompt Responsibilities

A prompt has a narrow and specific job: to obtain, from a model, a semantic boundary
decision about one window of a document. Everything a prompt does serves that single
end, and its responsibilities can be understood as the parts of accomplishing it.

**Describing the chunking task.** A prompt must make clear to the model what it is
being asked to do — that it is to identify where a portion of a document should be
divided into coherent, self-contained sections. The model must understand the nature
of the task before it can perform it well, and conveying that understanding is the
prompt's first responsibility.

**Providing document context.** A prompt must present the window's content to the
model in a form the model can reason about — the material over which the boundary
decision is to be made. The decision is only as good as the model's grasp of what it
is dividing, so the prompt is responsible for giving the model the content clearly and
in a form suited to the judgment being asked.

**Requesting boundary decisions.** A prompt must ask, specifically, for the boundaries
— for the model's determination of where one section ends and the next begins,
expressed in the terms the library expects. It is not enough for the model to
understand the content; the prompt must direct that understanding toward producing the
particular kind of decision the library needs.

**Constraining model output.** A prompt must shape what the model returns, so that the
response is something the library can consume — well-formed, in the expected shape, and
confined to the decision that was asked for. A model left to answer freely may produce
something the library cannot use; the prompt is responsible for constraining the
response to the form the pipeline's validation stage expects.

**Reducing ambiguity.** A prompt must minimize the room for the model to
misunderstand what is wanted — to leave as little as possible open to interpretation
about the task, the expected decision, and the form of the answer. Ambiguity in the
prompt becomes variability and error in the output, so removing ambiguity is a
responsibility woven through all the others.

These responsibilities are all in service of one outcome: a clear, usable boundary
decision for the window at hand. The prompt is not responsible for anything beyond
eliciting that decision, and its responsibilities end where the model's response
begins.

---

## 3. Prompt Design Principles

Every prompt is designed according to a set of principles. They are the standards a
maintainer applies when writing or refining a prompt, and each exists because
departing from it degrades the quality or consistency of the library's output.

**Clarity.** A prompt must be clear above all. The model should be able to grasp what
is being asked without effort, because a model that must guess at the intent of an
unclear prompt will guess inconsistently. Clarity is the foundation on which every
other quality of a good prompt rests.

**Determinism where possible.** A prompt should be written so as to elicit as
consistent a response as the model is capable of giving. While the model's output is
inherently variable, a well-designed prompt narrows that variability by leaving little
to chance in what is asked and how the answer is to be shaped. The prompt cannot make
the model deterministic, but it can avoid inviting unnecessary variation.

**Minimal ambiguity.** A prompt should leave as little as possible open to
interpretation. Every point at which the model could reasonably read the request in
more than one way is a point at which its behavior may diverge. Reducing ambiguity is
how a prompt turns a capable but unconstrained model toward a consistent, expected
kind of answer.

**Explicit instructions.** A prompt should state what it wants plainly rather than
implying it. What the task is, what decision is sought, and what form the answer should
take are all better stated outright than left for the model to infer. Explicitness
removes a class of misunderstanding that implication invites.

**Provider neutrality.** A prompt should express its intent in terms meaningful to any
capable model, not in terms that depend on the behavior of a particular provider. A
prompt written to a provider's quirks serves that provider and fails others, and ties
the library's behavior to a technology it is meant to remain independent of.

**Consistent structure.** Prompts should share a consistent structure, so that the way
the library asks for a boundary decision is uniform from one window to the next and one
document to the next. Consistency of structure supports consistency of outcome, and it
makes the prompts easier to reason about, maintain, and refine as a body rather than as
scattered individual artifacts.

These principles matter because the prompt is the single most direct influence the
library has over the model's judgment. A prompt that is unclear, ambiguous, implicit,
provider-dependent, or inconsistent introduces variation and error precisely at the
point where the library's output is determined. Adhering to these principles is how the
library exercises disciplined control over the one stage it cannot make deterministic,
and it is what keeps the quality of chunks high and stable across the enormous variety of
documents the library must handle.

---

## 4. Prompt Lifecycle

A prompt is a transient artifact of a single execution. It comes into being for one
purpose, serves that purpose, and is gone. Understanding this lifecycle is important to
understanding why a prompt is an internal detail rather than a durable part of the model.

**Constructed internally.** A prompt is built within the pipeline, at the
prompt-construction stage, for a specific window. It is assembled by the library from
the library's own fixed instruction and the content of the window at hand. No prompt
exists before this construction, and the caller has no part in it.

**Sent to the model.** The constructed prompt is carried to the model by the model
implementation and presented to the model for a response. This is the prompt's active
moment — the point at which it does its work, eliciting a boundary decision from the
model.

**Response returned.** The model answers, and its response is returned to the library.
At this moment the prompt has accomplished what it existed to accomplish. The value of
the prompt was entirely in obtaining this response; once the response is in hand, the
prompt has no further use.

**Discarded.** The prompt is then discarded. It is not retained, not part of the result,
and not visible anywhere in the library's output. It was scaffolding for a single
execution, and once that execution has yielded its response, the scaffolding falls away.

That prompts are transient execution artifacts has a consequence worth making explicit:
a prompt is never part of the library's durable model or its output, only of the passing
work of obtaining a decision. This is why prompt wording can change freely without
affecting anything the library persists or returns, and it is part of why the prompt is
properly regarded as an internal implementation detail. Nothing outside the moment of
execution depends on a prompt's existence, so nothing outside the library need ever know
what a prompt contained.

---

## 5. Provider Independence

Prompts are written to express semantic intent, not provider-specific behavior. A prompt
says what the library wants the model to understand and decide; it does not encode
assumptions about how any particular provider happens to behave. This is a direct
consequence of the library's provider independence, applied to the prompt.

The library is designed to work with any capable model behind its model abstraction —
among them the offerings of providers such as OpenAI, Anthropic, and Gemini, and the
providers that will exist in the future and are not yet known. A single owned prompt,
expressed in provider-neutral terms, must serve all of them. It cannot be tailored to the
idiosyncrasies of one without disadvantaging the others, and it must remain effective as
new providers and new model generations arrive.

Prompts should therefore avoid depending on provider quirks wherever possible. A quirk —
some particular way a specific model responds to a specific phrasing, some behavior unique
to one provider's implementation — is a fragile thing to build upon. It may differ across
providers, and it may change as a provider revises its models, so a prompt that relies on
it is a prompt whose effectiveness varies by provider and erodes over time. By expressing
intent in terms any capable model can be expected to understand, a prompt stays robust
across providers and across the changes each provider makes.

There is a pragmatic limit to acknowledge honestly: models do differ, and perfect
provider neutrality may not always be attainable. The principle is not that a prompt must
be indifferent to all differences among models, but that it should depend on provider
specifics as little as possible, prefer semantic intent over mechanical trick wherever a
choice exists, and never encode a dependency on one provider that would compromise its
effectiveness with others. The aim is a prompt that asks for what the library wants in a
way that travels well, so that the library's independence from any single provider is
real in practice and not merely nominal.

---

## 6. Prompt Stability

Prompts evolve, but the library's observable behavior remains stable. These two facts
coexist because the prompt is an internal asset whose refinement is meant to be invisible
from the outside.

**Continuous refinement.** A prompt is never regarded as finished. Understanding of how
best to elicit good boundary decisions improves over time, models change, and experience
accumulates; the prompt is refined in step with all of this. Refinement is expected and
ongoing, not a sign that anything was wrong before.

**Backward behavioral compatibility.** The goal of refinement is to improve the quality of
the library's output while keeping the *kind* of behavior the library exhibits stable. A
developer relying on the library should find that it continues to produce coherent,
well-formed chunks of the kind it always produced — better, perhaps, but not different in
character. Refinement improves results; it does not change what the library fundamentally
does or the shape of what it returns.

**Small improvements.** Prompts advance by small, considered changes rather than sweeping
rewrites. A modest refinement can be understood, evaluated, and adopted with confidence,
and its effect on output quality can be judged. Incremental change keeps the evolution of
the prompt controlled and its effects legible.

**Measurement-driven evolution.** Changes to a prompt are justified by evidence that they
improve the quality of the output, not by intuition alone. A prompt change is a change to
the library's most consequential internal behavior, and it earns adoption by demonstrating
that it makes chunks better. This keeps prompt evolution honest and directed at genuine
improvement.

The essential point is that prompt wording may change freely while the observable behavior
of the library remains stable. The wording is internal; the behavior is the contract.
Because callers depend on the behavior and never on the wording, the library is free to
improve the wording continuously, confident that doing so honors rather than disturbs what
callers rely upon. Prompt stability is not stability of text — the text is expected to
change — but stability of the library's observable character across every change to that
text.

---

## 7. Prompt Validation

Before a prompt is fit for production use, it must meet a set of standards. These are the
criteria a maintainer uses to judge whether a prompt — new or revised — is acceptable, and
they describe what a good prompt possesses rather than how it is written.

**A clear objective.** An acceptable prompt has one plain objective: to obtain a semantic
boundary decision for the window at hand. The objective must be evident and singular; a
prompt whose purpose is unclear or divided is not acceptable, because a model cannot serve
an objective it cannot discern.

**Unambiguous instructions.** An acceptable prompt instructs the model in a way that leaves
little room for misinterpretation. What is asked, and how the answer is to be given, should
be clear enough that a capable model is unlikely to mistake them. Ambiguity that would lead
the model to diverge is a defect that disqualifies a prompt.

**An expected response shape.** An acceptable prompt establishes what form the answer
should take, so that the response can be consumed by the library's validation stage. A
prompt that does not constrain the shape of the answer leaves the library unable to rely on
what comes back, and is not acceptable.

**Limited scope.** An acceptable prompt confines itself to the single decision it exists to
obtain. It does not ask the model to do more than determine boundaries for one window, and
it does not stray into tasks that belong to other stages. A prompt that overreaches its
scope is not acceptable, because it invites the model into responsibilities the library
does not delegate.

These criteria exist to ensure that every prompt in production reliably elicits a usable
decision. A prompt that has a clear objective, instructs unambiguously, establishes the
response shape, and stays within its scope is a prompt the library can depend on; one that
lacks any of these introduces risk precisely at the point where the library's output is
determined. Validation against these criteria is how a maintainer ensures that refinement
improves the prompt without compromising its reliability.

---

## 8. Prompt Invariants

Certain properties must hold of every production prompt, regardless of how its wording
evolves. They are the fixed points around which all refinement occurs, and violating any of
them damages the quality of the library's chunks.

**One responsibility.** Every prompt has the single responsibility of obtaining a boundary
decision for one window. It does not take on additional tasks. A prompt that acquired a
second responsibility would divide the model's attention and dilute the decision the library
actually needs, degrading the boundaries and therefore the chunks.

**Semantic neutrality.** Every prompt asks the model to identify boundaries according to the
meaning of the content, without biasing the model toward a particular substantive
interpretation of the document. The prompt is concerned with *where* the document divides,
not with steering *what* the document is taken to say. A prompt that lost this neutrality
would distort the boundaries with a bias that has nothing to do with the document's actual
structure.

**Provider independence.** Every prompt expresses its intent in provider-neutral terms and
avoids depending on the quirks of any one provider. A prompt that violated this would produce
good boundaries with one provider and poor ones with another, breaking the uniform quality the
library must offer across every model behind its abstraction.

**Deterministic structure.** Every prompt follows a consistent structure, so that the library
asks for boundaries in a uniform way across windows and documents. A prompt whose structure
varied unpredictably would introduce variation in the model's responses that owes nothing to
the content, undermining the consistency of the output.

**Clear output expectations.** Every prompt establishes what form the response must take, so
that the library can validate and consume it. A prompt that left the response shape unclear
would yield answers the validation stage could not reliably accept, causing recoverable
failures and degrading the smoothness and quality of the operation.

Each invariant protects chunk quality at a specific point. A prompt that took on a second
responsibility, lost neutrality, depended on a provider, varied its structure, or left its
output shape unclear would harm the boundaries the model produces, and since chunks are built
directly from those boundaries, the harm would flow straight through to the library's output.
These invariants are what allow the prompt to be refined freely in its wording while remaining,
in every version, a sound instrument for eliciting good boundaries.

---

## 9. What Prompts Deliberately Do Not Do

A prompt is responsible for obtaining a semantic boundary decision and nothing else. A number
of responsibilities that belong to the library's stages must never be pushed into the prompt,
and keeping them out is essential to the pipeline working as designed.

A prompt does not perform **document extraction**. Interpreting a file into content is the
extractor's work, done before any prompt exists. A prompt operates on content already extracted
and normalized; it never asks the model to parse a document.

A prompt does not perform **window planning**. How the document is divided into windows is the
library's decision, made before the prompt is constructed. A prompt concerns exactly one window
that has already been determined; it never asks the model to decide how the document should be
divided into windows.

A prompt does not perform **boundary merging**. Reconciling the decisions from separate windows
is internal work the library does after the model has answered. A prompt elicits a decision for
one window in isolation; it never asks the model to combine decisions across windows.

A prompt does not perform **validation**. Checking that a response is well-formed and refers to
genuine units is the library's guardian responsibility, exercised on the response after it
returns. A prompt shapes what is asked; it does not ask the model to validate its own answer.

A prompt does not perform **chunk construction**. Building chunks from merged boundaries is the
library's work. A prompt asks for boundaries, not chunks; it never asks the model to assemble the
output.

A prompt does not perform **pipeline orchestration**. The sequence and coordination of the whole
operation belong to the library. A prompt participates in one stage; it never directs the process
or reaches beyond its single window.

A prompt does not make **business decisions**. It does not judge what a document means for a
caller's purposes, decide how chunks will be used, or take on any concern beyond the structural
question of where meaning divides. Such decisions belong to the systems built on top of the
library, not to the prompt.

All of these belong to the library, and to specific stages of it, for the reason the pipeline and
SPI documents establish: they are parts of *how* semantic chunking is performed, distributed
deliberately across dedicated stages, each with its own guarantees and invariants. The prompt owns
exactly one of these responsibilities — eliciting a boundary decision — and pushing any other into
it would overload the model with work the library has assigned elsewhere, bypass the stages built
to do that work correctly, and undermine the invariants those stages preserve. The prompt is a
precise instrument for a single purpose, and its restraint is what keeps the rest of the pipeline
able to do its own work.

---

## 10. Prompt Evolution

Over the lifetime of the project, prompts are expected to improve continuously while remaining
invisible to the library's users. How that improvement is conducted is itself a matter of
discipline, because the prompt is the library's most consequential internal asset.

**Incremental improvements.** Prompts advance through small steps. Each change is modest enough to
be understood in full and evaluated on its own, so that its contribution to output quality is clear
and its risks are contained. The prompt is improved by accumulation of considered refinements, not
by periodic upheaval.

**Evaluation before adoption.** A prompt change is evaluated before it is adopted. Because the
prompt determines the boundaries from which chunks are built, a change to it is a change to the
library's core behavior, and it earns its place only by being assessed against the quality of the
output it produces. No change is adopted on the assumption that it must be better; it is adopted
because it was shown to be.

**Measurable quality improvements.** The purpose of prompt evolution is to make chunks better, and
improvement is judged by evidence of better chunks. A refinement that cannot be shown to improve
output quality has no claim to adoption, however plausible it seems. This keeps prompt evolution
directed at genuine gains rather than at change for its own sake.

**Avoiding prompt churn.** Frequent, poorly-justified changes to prompts are to be avoided. Churn
introduces instability, makes the effects of any single change hard to isolate, and risks trading a
known, sound prompt for an untested one. Prompts are changed deliberately and no more often than
genuine improvement warrants, so that each version is a stable base from which the next refinement
can be measured.

**Behavioral stability.** Through all of this, the observable behavior of the library remains
stable. Prompt evolution improves the quality of the library's output without changing the kind of
output it produces or the way it presents itself to callers. Users experience a library that gets
better at what it does, not one that behaves differently from release to release.

The governing understanding is that prompts are internal implementation assets that continuously
improve while remaining invisible to library users. Their wording is never a contract and never
exposed; their effect — good, consistent boundaries — is what callers depend upon. This separation
is what makes continuous improvement possible: because no one outside the library depends on the
prompt itself, the library is free to keep making it better for as long as the project lives,
confident that every improvement reaches users as better chunks and nothing else. The prompt is,
in the end, a private instrument that the library sharpens indefinitely while the hand that holds
it stays the same.
