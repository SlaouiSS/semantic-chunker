# semantic-chunker-unstructured

An Unstructured-backed `DocumentExtractor` for `semantic-chunker`. It sends a document to the
Unstructured [partition endpoint](https://docs.unstructured.io/api-reference/partition/overview),
parses the JSON elements it returns, and folds them into the core's normalized representation
(`PreparedDocument`). It depends on the core; the core does not depend on it.

```java
DocumentExtractor extractor = UnstructuredDocumentExtractor.builder()
        .apiUrl("https://api.unstructuredapp.io")   // or a self-hosted URL
        .apiKey(System.getenv("UNSTRUCTURED_API_KEY"))
        .build();

SemanticChunker chunker = SemanticChunker.builder()
        .documentExtractor(extractor)
        .chunkingModel(model)
        .build();
```

## Deployment

The adapter talks to the synchronous partition endpoint (`POST {apiUrl}/general/v0/general`), one
document per call. Two deployments are supported, chosen purely by configuration.

- **Hosted service** — the default `https://api.unstructuredapp.io`. Requires an API key. Set it with
  `apiKey(...)`.
- **Self-hosted** — the open-source `unstructured-api` Docker image, for example
  `docker run -p 8000:8000 downloads.unstructured.io/unstructured-io/unstructured-api:latest`. Point
  the adapter at it with `apiUrl("http://localhost:8000")` and leave `apiKey` unset. Self-hosting
  removes the per-document cost and the network dependency on the hosted service.

## Authentication

The API key travels in the `unstructured-api-key` header. It is nullable: when absent (or blank), the
header is omitted, which is what a keyless self-hosted instance expects.

## Configuration

Everything is configured through the builder; all options have safe defaults.

| Option | Default | Purpose |
| --- | --- | --- |
| `apiUrl(String)` | `https://api.unstructuredapp.io` | Base URL of the service; the partition path is appended automatically. |
| `apiKey(String)` | `null` | Hosted-service key; leave unset for self-hosting. |
| `strategy(String)` | `auto` | Partition strategy: `auto`, `fast`, `hi_res`, `ocr_only`, `vlm`. Structure-aware strategies produce table HTML but are slower. |
| `timeout(Duration)` | 2 minutes | Per-request timeout. |
| `httpClient(HttpClient)` | JDK default | Test-only override; production callers should not set it. |

The request always sends `strategy`, `include_page_breaks=true`, and `pdf_infer_table_structure=true`,
so page breaks and PDF table structure are recovered. The extractor is immutable and safe for
concurrent use: it holds one shared `java.net.http.HttpClient` and builds per-call request state.

## Supported formats

Unstructured handles a broad set of formats. The adapter declares a curated list (PDF; Word, Excel,
PowerPoint, and OpenDocument; HTML, plain text, Markdown, CSV; RTF, EPUB, XML; email `.eml`/`.msg`;
PNG/JPEG/TIFF images). Because the partition API does not report its accepted types, this list is
maintained by hand — unlike the Tika adapter, which introspects its parsers.

## Fidelity limitations

The adapter faithfully represents Unstructured's output. Element types collapse into the six canonical
unit kinds:

- **Title** → `Heading` (its `category_depth` recorded as the `level` metadata, 0-based — the Tika
  adapter instead records the 1–6 HTML heading number).
- **ListItem** → `ListItem` — preserved exactly. This is a fidelity gain over the Tika adapter, which
  loses Word and PowerPoint list structure.
- **Table** → `Table`. Cells and Markdown come from `text_as_html` when present (structure-aware
  strategies); otherwise the table falls back to its plain text with empty cells. Merged and nested
  cells are flattened.
- **Image** → `Image`, its text the caption. The `image_base64` binary is intentionally ignored — the
  pipeline reasons over text.
- **PageBreak** → `PageBreak`.
- **Everything else** (`NarrativeText`, `Header`, `Footer`, `PageNumber`, `Address`, `Formula`,
  `CodeSnippet`, and the rest) → `Paragraph`. The sub-category is lost.

Page numbers come from each element's `page_number`, or `0` for unpaged formats (HTML, text, email).
Provenance offsets address the normalized text (the concatenation of the units' textual content), per
the frozen contract; the invariant `normalizedText.substring(startOffset, endOffset)` equals a unit's
text holds by construction.

## Failure handling

Every failure becomes an `ExtractionException` with the cause preserved: transport errors, timeouts,
interruptions, malformed JSON, and non-200 statuses. HTTP statuses map to clear messages — `400` bad
request, `401` authentication failure, `422` encrypted/corrupted/unprocessable document, `500`
processing failure, `503` service unavailable. The adapter does not retry; extraction runs once and
the library owns recovery.

## Dependency choices

The HTTP transport is the JDK `java.net.http.HttpClient`, so no HTTP library is pulled in. Two
dependencies are added and no more: **Jackson** (`jackson-databind`) to parse the partition response,
and **jsoup** to turn a table's `text_as_html` into cells and Markdown. The Unstructured API version is
a deployment concern, not a compile dependency, so nothing else is needed.
