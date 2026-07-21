# semantic-chunker

A Java library for turning documents into high-quality semantic chunks, ready for
embeddings and retrieval. It takes an arbitrary document, normalizes it into a
consistent representation, and divides it along boundaries that respect the meaning
of the text rather than its size or its markup. It does this one thing, and
delegates everything else — parsing, model transport, embeddings, storage,
retrieval — to the mature libraries that already solve those problems.

---

## Why semantic-chunker?

Systems that reason over documents — retrieval-augmented generation, semantic
search, grounded assistants — must first divide those documents into pieces small
enough to embed and retrieve. The quality of those pieces quietly determines the
quality of everything built on top of them.

Most chunking divides text by size: a fixed number of characters or tokens, cut
wherever the counter runs out. This is simple and predictable, but it is indifferent
to meaning. A size-based cut can land in the middle of a sentence or split an idea
from the context that makes it comprehensible. Structural chunking — dividing on
headings or paragraphs — is better, but structure is an artifact of formatting, and
formatting boundaries are not always meaning boundaries.

Semantic chunking divides a document by its meaning. It uses a language model to
identify where one coherent section ends and the next begins, producing chunks that
are individually coherent and self-contained. When chunks respect semantic
boundaries, retrieval matches on substance and the model that consumes them reasons
over intact context.

---

## Features

- Semantic chunking driven by a language model
- Technology-independent architecture: parsing and model transport are delegated,
  not reimplemented
- Provider-independent model abstraction — bring your own model behind a small
  contract
- Spring AI integration provided as an adapter
- Apache Tika integration provided as an extractor, covering common document formats
- Unstructured integration provided as an extractor, hosted or self-hosted
- A normalized document model (`PreparedDocument`) shared by every extractor
- Provenance carried through to every chunk, so output can be traced to its source
- An immutable processing pipeline with predictable orchestration
- A small public API and a production-oriented result that reports what happened

## Non-goals

The library is deliberately narrow. It is **not**, and will not become:

- a RAG framework, or an embedding, retrieval, or reranking library;
- a document parser or an OCR engine — extraction is delegated to an extractor;
- a model client or transport — model access is delegated to a `ChunkingModel`;
- a general-purpose, size-based chunking toolkit.

Changes that pull the library toward any of these are out of scope regardless of
technical merit.

---

## Requirements

- **Java 21** or later.
- A `DocumentExtractor` (an adapter is provided for Apache Tika and for Unstructured).
- A `ChunkingModel` (an adapter is provided for Spring AI).

## Installation

Artifacts are published to Maven Central under the group `io.github.slaouiss`.
Add the core plus the adapters you need. The first release is `1.0.0`; use the latest
release version.

```xml
<!-- Always required. -->
<dependency>
    <groupId>io.github.slaouiss</groupId>
    <artifactId>semantic-chunker-core</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- A model adapter (choose one, or supply your own ChunkingModel). -->
<dependency>
    <groupId>io.github.slaouiss</groupId>
    <artifactId>semantic-chunker-spring-ai</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- An extractor adapter (choose one, or supply your own DocumentExtractor). -->
<dependency>
    <groupId>io.github.slaouiss</groupId>
    <artifactId>semantic-chunker-tika</artifactId>
    <version>1.0.0</version>
</dependency>
```

Gradle:

```groovy
implementation "io.github.slaouiss:semantic-chunker-core:1.0.0"
implementation "io.github.slaouiss:semantic-chunker-spring-ai:1.0.0"
implementation "io.github.slaouiss:semantic-chunker-tika:1.0.0"
```

Each module pulls only its own technology: adding the Tika extractor does not put
Spring AI or Unstructured on your classpath, and vice versa.

---

## Quick start

Wire an extractor and a model into a chunker, then chunk documents. Build the chunker
once and reuse it; it is immutable and thread-safe.

```java
import io.github.semanticchunker.chunker.*;
import io.github.semanticchunker.model.ChunkingModel;
import io.github.semanticchunker.extraction.DocumentExtractor;
import io.github.semanticchunker.tika.TikaDocumentExtractor;
import io.github.semanticchunker.springai.SpringAiChunkingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import java.nio.file.Path;
import java.util.stream.Collectors;

// 1. An extractor turns a document into the normalized representation.
DocumentExtractor extractor = TikaDocumentExtractor.builder().build();

// 2. A model decides the boundaries. Supply a configured Spring AI ChatModel,
//    the model's context-window size, and a token estimator.
ChatModel chatModel = /* your configured Spring AI ChatModel */;
ChunkingModel model = SpringAiChunkingModel.builder()
        .chatModel(chatModel)
        .tokenCountEstimator(new JTokkitTokenCountEstimator())
        .maxInputTokens(128_000)
        .build();

// 3. Build the chunker once; reuse it across documents and threads.
SemanticChunker chunker = SemanticChunker.builder()
        .documentExtractor(extractor)
        .chunkingModel(model)
        .build();

// 4. Chunk a document. This reads the file and calls the model — it blocks and costs money.
ChunkingResult result = chunker.chunk(Path.of("report.pdf"));

for (SemanticChunk chunk : result.chunks()) {
    String text = chunk.units().stream()
            .map(unit -> unit.text().orElse(""))
            .collect(Collectors.joining("\n"));
    System.out.println(text);
}
```

`chunk` is also overloaded for a `DocumentSource` (raw bytes plus a media type) and a
`PreparedDocument` (an already-extracted document, which skips extraction). The
`ChunkingResult` carries the chunks in order, any `warnings`, and `processingInfo` /
`executionMetadata` describing what the run consumed.

---

## Architecture

The library is a small core with two extension points. The core owns the pipeline —
normalization, window planning, prompt construction, response validation, boundary
merging, and chunk assembly — and everything it considers its own. It reaches the
outside world through exactly two Service Provider Interfaces (SPIs):

- **`DocumentExtractor`** turns a raw document into a `PreparedDocument` — an ordered,
  normalized sequence of typed `DocumentUnit`s (headings, paragraphs, list items,
  tables, images, page breaks), each carrying provenance.
- **`ChunkingModel`** executes a framed request against a language model and reports
  the model's context-window limit and token estimates.

The core depends on neither an extraction technology nor a model provider; adapters
depend on the core, never the reverse. This is the whole of the extensibility surface
— see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/SPI.md](docs/SPI.md).

### Modules

The library is split into independent modules so that you depend only on what you use.

- **semantic-chunker-core** — the pipeline and the domain model, plus the two
  extension contracts. Depends on no extraction technology and no model provider.
- **semantic-chunker-spring-ai** — a `ChunkingModel` adapter that lets a model be
  supplied by way of Spring AI. Targets Spring AI 2.0.x, which requires Spring
  Framework 7 and, for consumers using Spring Boot, Spring Boot 4. The adapter itself
  takes no dependency on Spring Boot; you supply a configured Spring AI `ChatModel`.
  See its [module README](semantic-chunker-spring-ai/README.md).
- **semantic-chunker-tika** — a `DocumentExtractor` over [Apache Tika](https://tika.apache.org/)
  3.3.1, in-process and with no external service. Handles PDF, Word, Excel,
  PowerPoint, HTML, and plain text out of the box. See its
  [module README](semantic-chunker-tika/README.md).
- **semantic-chunker-unstructured** — a `DocumentExtractor` over the
  [Unstructured](https://unstructured.io/) partition API, using the JDK HTTP client
  and no HTTP framework. Works against the hosted service or a self-hosted instance.
  See its [module README](semantic-chunker-unstructured/README.md).

Additional extractors and model adapters are expected to arrive as their own
dedicated modules. The core never depends on any of them.

---

## Supported document formats

Format support comes from the extractor you use; the adapter READMEs hold the
authoritative lists.

| Extractor | Formats | Notes |
| --- | --- | --- |
| Apache Tika | PDF, Word, Excel, PowerPoint, HTML, RTF, plain text, and more | In-process, no external service, no API key. |
| Unstructured | PDF, Office formats, HTML, text, email, images, and more | Hosted or self-hosted HTTP service. |

A `DocumentExtractor` declares the media types it handles through
`supportedMediaTypes()`. Fidelity varies by extractor and format; each adapter README
documents its known limitations.

## Supported model providers

The library depends on no provider directly. A language model is supplied through the
`ChunkingModel` contract, and providers are reached through adapters that satisfy it.
The Spring AI adapter, for instance, gives access to the providers Spring AI supports
— among them OpenAI, Anthropic, and Gemini. Which providers are available to you
depends on the adapters on your classpath; you can also supply your own adapter for a
provider not yet covered. No exhaustive or guaranteed list is implied.

---

## Extension points

The library is extended by implementing one of the two SPIs. Both are documented in
[docs/SPI.md](docs/SPI.md); implementations must be immutable, thread-safe, and fail
by throwing the typed exception rather than returning a partial result.

### A custom `DocumentExtractor`

Turn a document into the normalized representation. Offsets in `Provenance` address
the *normalized text* — the concatenation, in order, of the units' textual content —
not the original bytes.

```java
public final class MyExtractor implements DocumentExtractor {

    @Override
    public PreparedDocument extract(DocumentSource source) {
        // Parse source.content() (source.mediaType() tells you how), then build an
        // ordered List<DocumentUnit> with provenance, and return a PreparedDocument.
        // Throw ExtractionException if the source cannot be interpreted.
    }

    @Override
    public Set<String> supportedMediaTypes() {
        return Set.of("text/plain");
    }
}
```

### A custom `ChunkingModel`

Execute a framed request against a model and report the model's limits.

```java
public final class MyModel implements ChunkingModel {

    @Override
    public ModelResponse execute(ChunkingRequest request) {
        // Send request.prompt() to the model; return the raw answer and its token
        // usage as a ModelResponse. Throw ModelException on failure — do not retry.
    }

    @Override
    public int maxInputTokens() {
        return 128_000; // the model's context-window size
    }

    @Override
    public int estimateTokens(String text) {
        // A documented heuristic is acceptable; under-counting risks context overflow.
    }
}
```

---

## Thread safety

`SemanticChunker` and every provided adapter (`SpringAiChunkingModel`,
`TikaDocumentExtractor`, `UnstructuredDocumentExtractor`) are immutable and safe for
concurrent use. Build a chunker once and share it across threads; each `chunk` call
holds its own per-call state. Custom SPI implementations are required to be
thread-safe as well.

## Error handling

Failures surface as a small, typed exception hierarchy rooted at
`SemanticChunkerException` (unchecked):

- **`ExtractionException`** — an extractor could not interpret the source.
- **`ModelException`** — a model could not be reached or did not return a response.
- **`ChunkingException`** — the operation could not be completed.

Adapters wrap the underlying technology's failures (HTTP, IO, parser, provider errors)
into these types with the cause preserved; nothing provider-specific leaks out. An
extractor or model that cannot do its work fails clearly rather than returning a
partial or fabricated result.

## Performance considerations

- `chunk` performs I/O, **blocks**, and **calls a language model one or more times**,
  so it incurs latency and monetary cost. Treat it accordingly.
- The chunker is stateless across calls and reusable; construction is cheap, so build
  once and reuse.
- Extraction cost depends on the extractor: Tika parses in-process; Unstructured makes
  a network call per document.
- Model cost scales with document size, since larger documents are planned into more
  windows. `maxInputTokens` and the token estimator govern how windows are sized.

---

## FAQ

**Does the library call a language model itself?** No. It defines the `ChunkingModel`
contract and you supply an implementation. The Spring AI adapter is one such
implementation.

**Can I use it without a language model?** No — semantic boundary decisions are made
by a model. Without one, use a size- or structure-based chunker instead.

**Do I have to use the provided adapters?** No. Implement `DocumentExtractor` and/or
`ChunkingModel` yourself; the core depends only on those contracts.

**Are results deterministic?** The library's own behavior is; the model's answers are
not, being inherent to the model. Provenance and ordering are always preserved.

**Which JDK?** Java 21 or later.

---

## Documentation

The design is documented in depth. Start with the vision, then read as far as your
interest goes.

| Document | What it covers |
| --- | --- |
| [VISION](docs/VISION.md) | Why the project exists — its motivation and long-term goal |
| [ARCHITECTURE](docs/ARCHITECTURE.md) | How the system is organized, conceptually |
| [MODEL](docs/MODEL.md) | The domain model: every concept and how they relate |
| [API](docs/API.md) | The public API and the reasoning behind its shape |
| [PIPELINE](docs/PIPELINE.md) | How a document is processed from input to output |
| [SPI](docs/SPI.md) | The two extension contracts and their responsibilities |
| [PROMPT](docs/PROMPT.md) | The principles governing the chunking prompts |

Engineering standards for contributors are defined in
[CONTRIBUTING_ARCHITECTURE.md](docs/CONTRIBUTING_ARCHITECTURE.md).

---

## Design principles

- A small, stable public API
- A strong, immutable domain model
- Independence from any particular extraction technology or model provider
- A predictable, immutable processing pipeline
- A minimal extension surface: exactly two extension points, and no more

These are summarized here and explained in the design documents above.

## Roadmap

The pipeline's shape is settled; work concentrates on depth and breadth of integration
rather than on new stages.

- Additional document extractors
- Additional model adapters
- Performance improvements
- Broader test coverage

---

## Contributing

Contributions are welcome. The project holds itself to a defined set of engineering
standards, and every contribution is expected to follow them; please read
[CONTRIBUTING.md](CONTRIBUTING.md) and the
[engineering constitution](docs/CONTRIBUTING_ARCHITECTURE.md) before opening a pull
request. Security issues should be reported privately — see [SECURITY.md](SECURITY.md).

Changes between releases are recorded in [CHANGELOG.md](CHANGELOG.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
