# semantic-chunker-core

The core of `semantic-chunker`: the semantic-chunking pipeline, the domain model, and
the two extension contracts. It depends on no extraction technology and no model
provider — those are supplied through the SPIs by adapter modules.

This module is always required. See the [root README](../README.md) for installation
and a quick start.

## What it contains

- **Entry point** — `SemanticChunker` and its builder; `chunk` accepts a
  `DocumentSource`, a `Path`, or a `PreparedDocument`.
- **Document model** — `PreparedDocument` and the sealed `DocumentUnit` hierarchy
  (`Heading`, `Paragraph`, `ListItem`, `Table`, `Image`, `PageBreak`), each carrying
  `Provenance`. `DocumentSource` is the honest input type for extraction.
- **Output model** — `ChunkingResult`, `SemanticChunk`, `Warning`, `ProcessingInfo`,
  `ExecutionMetadata`.
- **SPIs** — `DocumentExtractor` (the extraction seam) and `ChunkingModel` (the model
  seam), the only two extension points.
- **Exceptions** — the hierarchy rooted at `SemanticChunkerException`
  (`ExtractionException`, `ModelException`, `ChunkingException`).

## Provenance

Every `DocumentUnit` carries a `Provenance` with a stable global ordinal, a page, and a
character span. The span's offsets address the document's **normalized text** — the
concatenation, in global-ordinal order, of the units' textual content — measured in
Java `String` UTF-16 code units, not the original document's bytes. The invariant
`normalizedText.substring(startOffset, endOffset)` equals the unit's textual content
holds for every unit, and is the extractor's responsibility to establish.

## Extending the core

Implement one of the two SPIs; both are specified in [docs/SPI.md](../docs/SPI.md).
Implementations must be immutable, thread-safe, and must fail by throwing the typed
exception rather than returning a partial result. The provided adapter modules
(`semantic-chunker-spring-ai`, `semantic-chunker-tika`, `semantic-chunker-unstructured`)
are reference implementations.

## Design documentation

The design is specified in depth under [`docs/`](../docs): the
[architecture](../docs/ARCHITECTURE.md), the [model](../docs/MODEL.md), the
[public API](../docs/API.md), the [pipeline](../docs/PIPELINE.md), the
[SPIs](../docs/SPI.md), and the [prompt principles](../docs/PROMPT.md).
