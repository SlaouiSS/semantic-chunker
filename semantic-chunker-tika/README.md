# semantic-chunker-tika

An Apache Tika–backed `DocumentExtractor` for `semantic-chunker`. It parses a
document with Tika and folds Tika's XHTML output into the core's normalized
representation (`PreparedDocument`). It runs in-process, needs no external service
and no API key, and depends on the core; the core does not depend on it.

```java
DocumentExtractor extractor = TikaDocumentExtractor.builder().build();

SemanticChunker chunker = SemanticChunker.builder()
        .documentExtractor(extractor)
        .chunkingModel(model)
        .build();
```

## Supported formats

Format support comes from the Tika parser modules on the classpath. This module
bundles a targeted set covering the common document formats:

| Family | Examples |
| --- | --- |
| PDF | `application/pdf` |
| Microsoft Office | Word (`.doc`, `.docx`), Excel (`.xls`, `.xlsx`), PowerPoint (`.ppt`, `.pptx`), RTF |
| HTML / XHTML | `text/html` |
| Plain text | `text/plain` |

`supportedMediaTypes()` reports the exact set the configured parsers declare. A
source that Tika parses but recovers no text from yields an empty `PreparedDocument`
(an honest empty result); a source Tika cannot parse fails with `ExtractionException`,
its cause preserved.

## Fidelity limitations

Tika's XHTML is format-dependent, so some structure cannot be recovered. These
losses are deliberate and faithful to what Tika emits — the adapter does not guess:

- **List items.** Word and PowerPoint emit list items as paragraphs with the bullet
  in the text (never `<li>`), so they map to `Paragraph`, not `ListItem`. True list
  items are recovered only from HTML, RTF, EPUB, and legacy PowerPoint.
- **Images.** PDF emits no image markup, so PDF images are not represented. Word and
  HTML images become `Image` with the `alt` text as caption; PowerPoint and Excel
  embedded images are placeholders with no caption and are not represented.
- **Page breaks.** Recovered only for formats that emit page/section containers (PDF,
  Excel, PowerPoint). Word and HTML have no page markers, so their units carry
  page `0`.
- **Tables.** Rendered to Markdown with structured cells retained; merged cells,
  nested tables, and cell formatting are flattened.
- **Headings.** Recovered where Tika emits `<h1>`–`<h6>` (Word, Excel sheet names,
  HTML); other formats yield no headings.

Provenance offsets address the document's **normalized text** — the concatenation,
in order, of every unit's textual content — exactly as the frozen `Provenance`
contract defines. The invariant `normalizedText.substring(startOffset, endOffset)`
equals a unit's text holds by construction.

## OCR behaviour

OCR is **disabled by default**, and this module bundles no OCR engine. When disabled,
PDF extraction is forced to text-only (`NO_OCR`), so extraction never depends on a
native binary and is deterministic. Enabling OCR (`ocrEnabled(true)`) only lifts that
restriction; it has an effect only if an OCR-capable parser and a Tesseract binary
are separately present.

## Configuration

Everything is configured through the builder; all options have safe defaults.

| Option | Default | Purpose |
| --- | --- | --- |
| `parser(Parser)` | `AutoDetectParser` | The Tika parser; detects the format and delegates. |
| `writeLimit(int)` | `-1` (unlimited) | Maximum characters to accumulate. Exceeding a positive limit fails with `ExtractionException` rather than truncating silently. |
| `ocrEnabled(boolean)` | `false` | See OCR behaviour above. |

The extractor is immutable and safe for concurrent use: each `extract` call builds
its own parsing state, so one instance can be shared across threads.

## Dependency choices

This module depends on a **targeted set** of Tika parser modules — `tika-core` plus
the PDF, Microsoft, HTML, and text parser modules — rather than the full
`tika-parsers-standard-package`. The full package resolves to roughly 87 transitive
artifacts; the targeted set resolves to about 50, dropping every parser the library
does not need (mail, scientific, CAD, package, audio, …) and with them a large slice
of the dependency and CVE surface, while keeping `supportedMediaTypes()` honest about
what is actually handled. The Tika version is pinned via the `tika-bom` and declared
in this module only, so upstream churn never reaches the core.

To support a format outside the bundled set, add the corresponding Tika parser module
as a dependency; `AutoDetectParser` will discover it and `supportedMediaTypes()` will
report it.
