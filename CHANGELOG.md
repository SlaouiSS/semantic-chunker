# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

_No unreleased changes._

## [1.0.0]

First public release.

### Added

- **semantic-chunker-core** — the semantic-chunking pipeline and domain model:
  - `SemanticChunker` entry point and builder, with `chunk` overloads for
    `DocumentSource`, `Path`, and `PreparedDocument`.
  - The normalized document model: `PreparedDocument` and the sealed `DocumentUnit`
    hierarchy (`Heading`, `Paragraph`, `ListItem`, `Table`, `Image`, `PageBreak`),
    with mandatory `Provenance`.
  - The output model: `ChunkingResult`, `SemanticChunk`, `Warning`, `ProcessingInfo`,
    `ExecutionMetadata`.
  - The two SPIs — `DocumentExtractor` and `ChunkingModel` — and the exception
    hierarchy rooted at `SemanticChunkerException`.
  - A provenance contract in which offsets address the extractor's normalized text.
- **semantic-chunker-spring-ai** — a `ChunkingModel` adapter over Spring AI 2.0.x,
  written against the provider-neutral `ChatModel` and `TokenCountEstimator`.
- **semantic-chunker-tika** — a `DocumentExtractor` over Apache Tika 3.3.1
  (in-process; PDF, Word, Excel, PowerPoint, HTML, plain text, and more).
- **semantic-chunker-unstructured** — a `DocumentExtractor` over the Unstructured
  partition API, using the JDK HTTP client, for the hosted or a self-hosted service.

[Unreleased]: https://github.com/semanticchunker/semantic-chunker/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/semanticchunker/semantic-chunker/releases/tag/v1.0.0
