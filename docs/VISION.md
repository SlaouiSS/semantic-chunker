# VISION.md

*Why this library exists.*

This document explains the motivation, philosophy, and long-term vision of the
`semantic-chunker` project. It is not an architecture document, an API
reference, or a technical specification. It exists to answer a single question —
*why does this library exist?* — before any discussion of how it is built. The
engineering rules that govern the implementation live in the project's
engineering constitution and are out of scope here.

It is written for experienced Java developers who are evaluating the project:
deciding whether to depend on it, whether to contribute to it, and whether to
trust it with a part of their systems that matters.

---

## 1. Purpose

The mission of this project is narrow and deliberate: to make high-quality
semantic chunking of documents straightforward for Java developers, and to do
that one thing well enough that they never have to think about it again.

Modern AI systems that reason over private data — question answering over
internal documents, assistants grounded in a knowledge base, search that
understands meaning rather than keywords — depend on a preparatory step that
receives far less attention than it deserves. Before a document can be embedded,
indexed, or retrieved, it must be divided into pieces. The quality of those
pieces quietly determines the quality of everything built on top of them. A
system can use an excellent embedding model and a well-tuned retriever and still
return poor answers, simply because the underlying text was divided in a way
that severed ideas from their context.

This project addresses that preparatory step, and only that step. It takes an
arbitrary document, normalizes it into a consistent internal representation, and
divides it along boundaries that respect the meaning of the text rather than its
byte count or its markup. The result is a set of chunks that are individually
coherent and self-contained, ready to be handed to whatever embedding model,
vector store, or retrieval framework a team already uses.

The library is for the engineers who build those systems: the developers
assembling retrieval-augmented applications, semantic search, and grounded
assistants on the JVM, who need their documents prepared correctly and who would
rather delegate that concern to a focused, dependable tool than solve it
repeatedly and imperfectly by hand.

---

## 2. The Problem

Chunking is often treated as a trivial preprocessing detail. In practice it is
one of the most consequential decisions in a retrieval pipeline, because every
later stage inherits its choices.

A retrieval system works by comparing a query against stored fragments of text
and returning the fragments that appear most relevant. For that comparison to be
meaningful, each fragment must stand on its own. It must contain enough context
to be understood in isolation, and it must not blur several distinct ideas
together. When a fragment satisfies these conditions, retrieval is precise and
the model that consumes it has clean material to reason over. When it does not,
the failure is silent: the retriever returns something plausible, the model
answers from it, and the answer is subtly wrong in a way that is difficult to
trace back to its cause.

The most common approach divides text into fragments of a fixed size — a set
number of characters or tokens, sometimes with a fixed overlap. This is simple,
predictable, and entirely indifferent to meaning. A fixed-size cut lands wherever
the counter runs out, which may be in the middle of a sentence, midway through an
argument, or between a claim and the evidence that supports it. Two ideas that
belong together may be split across fragments; two unrelated ideas may be fused
into one. The method's virtue is its simplicity, and its cost is that it treats
prose as an undifferentiated stream of characters.

A more sophisticated approach follows the document's structure — dividing on
headings, paragraphs, list items, or other markup. This is a real improvement,
because structure often correlates with meaning. But the correlation is
imperfect. Structural boundaries are an artifact of how a document was authored
and formatted, not a map of where its ideas actually begin and end. A single
heading may cover several distinct topics; a single conceptual unit may run
across several paragraphs or span a heading break; many documents carry little
reliable structure at all. Structural chunking divides text where the formatting
changes, which is not always where the meaning changes.

Both approaches are useful, and both are sometimes sufficient. The difficulty is
that neither reads the text. They divide it by measuring it or by parsing its
markup, and in doing so they can only approximate the thing that actually
matters: where one idea ends and the next begins. Preserving those semantic
boundaries is what makes a chunk coherent, and coherent chunks are what make
downstream retrieval and generation reliable. When the boundaries are respected,
each fragment carries a complete thought, the retriever matches on substance, and
the model reasons over intact context. That is the outcome this library is built
to produce.

None of this is a criticism of existing methods or the libraries that implement
them. Fixed-size and structural chunking are well-understood, widely used, and
appropriate in many settings. This project simply addresses the cases they were
never designed to handle: dividing a document by its meaning.

---

## 3. Why This Library Exists

The central idea of this project is a change in what the language model is asked
to do.

In a conventional pipeline, the language model appears at the end. It receives a
query and some retrieved context, and it generates an answer. It is treated as
the engine of the final step and nothing more. Everything that happens before it
— including how the source documents were divided — is handled by mechanical
rules that never consult the model at all.

This library moves the language model earlier, and gives it a different job. Here
the model is not asked to generate an answer. It is asked to read a document and
identify where its semantic boundaries lie — to determine, from the text itself,
where one coherent unit of meaning ends and the next begins. The same capability
that lets a model understand a passage well enough to answer questions about it
is the capability that lets it recognize the natural seams in that passage. This
project applies that understanding to the one decision that fixed-size and
structural methods can only approximate.

This is a different philosophy from traditional chunking. Traditional methods
decide boundaries by measuring the text or by inspecting its markup — operations
that treat the document as data to be counted or parsed. This library decides
boundaries by having the text read and understood. The distinction is not one of
degree but of kind: the difference between dividing a document from the outside,
by its surface properties, and dividing it from the inside, by its meaning.

That single idea is the reason the project exists. Everything else it does exists
to serve it well and to keep it dependable.

---

## 4. Scope

The library exists to carry a document from its raw form to a set of
meaning-respecting chunks, and to do the parts of that journey that are unique to
semantic chunking. Its responsibilities are:

- to normalize documents of varying origins into a single consistent internal
  representation, so that everything downstream operates on a uniform input
  regardless of where the document came from;
- to prepare that representation for semantic analysis, so the meaning of the
  text is what drives the decision;
- to orchestrate the language model's involvement in identifying semantic
  boundaries, including the practical concerns that arise when a document is
  larger than a model can consider at once;
- to validate the boundaries that come back, ensuring the result is well-formed
  and trustworthy rather than accepted blindly;
- to reconcile boundaries across the separate views of a large document into a
  single coherent set; and
- to produce the final semantic chunks, each a self-contained unit of meaning
  ready for downstream use.

This is the whole of what the library does. It is a focused pipeline with a clear
beginning and a clear end, and it stops precisely where semantic chunking stops.

---

## 5. Non-Goals

A project's boundaries are defined as much by what it refuses to do as by what it
does. This library is deliberately not many things, and each exclusion is a
choice, not an omission.

It is **not a retrieval-augmented generation framework.** It prepares material
for such frameworks; it does not orchestrate the retrieval-and-generation loop.

It is **not an OCR engine.** It does not turn images or scanned pages into text.

It is **not a PDF parser**, nor a parser for any other document format. It does
not extract raw content from files.

It is **not an embeddings library.** It does not turn text into vectors.

It is **not a vector database.** It does not store, index, or manage embeddings.

It is **not a retrieval framework.** It does not search or rank stored content in
response to queries.

It is **not a reranker.** It does not reorder retrieved results.

It is **not a general document-processing framework.** It does not aim to be a
toolkit for arbitrary transformations of documents.

It is **not a general-purpose chunking framework.** It does not set out to offer
every chunking strategy that exists. Its concern is semantic chunking, performed
well.

These exclusions are principled. Each of these responsibilities is already
addressed by mature, well-maintained libraries that have earned the trust of
their communities and that solve their problems better than a project of this
scope could hope to. Parsing, optical character recognition, embeddings, storage,
retrieval, and reranking are solved problems with excellent existing solutions.
Reimplementing any of them would dilute this project's focus, expand its
maintenance burden, and add nothing the ecosystem lacks. The library is designed
to sit cleanly alongside those tools — to receive from them and hand off to them
— rather than to absorb their responsibilities. Doing one thing well requires
declining to do the others.

---

## 6. Design Philosophy

The product philosophy of this project can be stated in a few durable
commitments. These are not implementation rules; they are the convictions that
shape what the project chooses to be.

**Do one thing exceptionally well.** The project's value comes from the depth of
its focus, not the breadth of its features. It would rather be the best tool for
a single job than an adequate tool for many. Every proposed addition is measured
against whether it makes semantic chunking better, not against whether it is
useful in general.

**Delegate solved problems.** Where a capability is already provided well by an
established library, the project uses it rather than rebuilding it. This keeps the
project small, keeps its dependencies honest, and lets it benefit from the
continued improvement of the tools it relies on. Effort is reserved for the parts
that are genuinely the project's own.

**Keep the public surface small.** What the project exposes to the people who use
it is kept deliberately minimal, because a small, well-considered surface is
easier to learn, easier to use correctly, and possible to keep stable over many
years. Restraint here is a feature, not a limitation.

**Value correctness over feature count.** A smaller set of capabilities that work
correctly and predictably is worth more than a larger set that works most of the
time. The project grows slowly and on purpose, and it treats reliability as the
headline capability rather than an afterthought.

**Remain provider agnostic.** The project does not tie itself to any single
language-model provider. The choice of model belongs to the people who adopt the
library, and that choice should be theirs to make and to change without the
library standing in the way.

**Endure.** The project is built to remain useful and maintainable for many years.
It favors decisions that will still look reasonable a decade from now over
decisions that are merely convenient today, and it treats long-term stability as
more valuable than rapid growth. A tool that infrastructure depends on earns that
position by being dependable for a long time.

---

## 7. Success Criteria

Success for this project is not measured in numbers, and no metric here is a
target. It is described instead by the qualities the project hopes to earn.

The project succeeds if it becomes **trusted** within the Java ecosystem — if
experienced developers reach for it without hesitation when they need semantic
chunking, the way they reach for established, dependable tools in other domains.

It succeeds if it is **easy to integrate** — if adopting it is a small, obvious
step rather than a project in itself, and if it fits naturally into systems that
already have their own opinions about embeddings, storage, and retrieval.

It succeeds if its **public surface remains stable** over time — if code written
against an early version continues to work, and if developers can depend on it
without fearing that each release will ask them to relearn or rewrite.

It succeeds if it works dependably **across multiple language-model providers**,
so that the choice of model remains open to the people who use it and no adopter
feels locked in.

It succeeds if the **chunks it produces are genuinely good** — coherent,
self-contained, and faithful to the meaning of the source — so that the systems
built on top of it are measurably better for having used it.

And it succeeds if it keeps **complexity away from the people who use it** — if
the difficult parts of semantic chunking are handled inside the library so that,
from the outside, the hard thing looks simple.

---

## 8. Long-Term Vision

The ambition of this project is to become the reference Java library for semantic
chunking — the tool that a Java developer thinks of first, and often thinks of
only, when the task arises.

Some libraries come to define their corner of an ecosystem. In the Java world, a
particular library became the natural answer for working with JSON, and another
became the natural answer for managing database migrations. They achieved that
standing not by covering the widest possible surface, but by taking one
well-defined problem seriously, solving it thoroughly, and remaining stable and
trustworthy for long enough that the community stopped looking for alternatives.
They became reference points because they were focused, dependable, and patient.

This project holds the same kind of ambition for semantic chunking. It aims to be
the library that is dedicated exclusively to this problem, that treats it as a
first-class concern rather than a feature buried inside something larger, and that
does it well enough and stably enough to be relied upon for the long term. As the
tools around it evolve — as models improve, as extraction and retrieval and
storage continue to advance — the project intends to remain the steady, focused
component that prepares documents by their meaning and hands them cleanly to
whatever comes next.

This is not a roadmap, and it is not a promise of features. It is a statement of
the standard the project holds itself to: to earn, over years rather than
releases, the quiet trust that comes from doing one important thing exceptionally
well.
