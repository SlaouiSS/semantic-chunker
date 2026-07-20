package io.github.semanticchunker.extraction;

import io.github.semanticchunker.document.DocumentSource;
import io.github.semanticchunker.document.PreparedDocument;
import java.util.Set;

/**
 * SPI: the core-owned abstraction over an extraction technology. An adapter implements this
 * contract to turn a raw document into the library's normalized representation, and the core sees
 * only this interface — never an extraction-technology concept (SPI.md, section 3;
 * CONTRIBUTING_ARCHITECTURE.md, section 6.3).
 *
 * <p><strong>Responsibility.</strong> To transform a {@link DocumentSource} into a {@link
 * PreparedDocument}: an ordered sequence of {@link
 * io.github.semanticchunker.document.DocumentUnit}, each bearing a stable global ordinal,
 * provenance, and (where applicable) textual content. The extractor is the sole creator of prepared
 * documents and establishes the units, their order, and their identities correctly, because every
 * later stage builds on them without revisiting them. It is <em>not</em> responsible for anything
 * beyond extraction and normalization.
 *
 * <p><strong>Lifecycle.</strong> Instances are created by the caller and supplied to the {@code
 * SemanticChunker} builder. An instance is long-lived and reused across many documents. The library
 * calls {@link #extract(DocumentSource)} once at the start of an operation; from that point the
 * extractor has no further part in the operation.
 *
 * <p><strong>Thread safety.</strong> An implementation MUST be safe for concurrent use by multiple
 * threads (CONTRIBUTING_ARCHITECTURE.md, section 10.4).
 *
 * <p><strong>Ownership.</strong> The extractor owns validating that it can handle the source and
 * reading it; it throws a typed {@link ExtractionException} when it cannot, and the core does not
 * guess (CONTRIBUTING_ARCHITECTURE.md, section 6.3.4). The core owns everything after the prepared
 * document exists.
 *
 * <p><strong>Extension rules.</strong> Extractors differ in fidelity, and structural richness
 * (headings, structured tables) is optional and extractor-dependent: a flat sequence of paragraph
 * units with page breaks is a valid prepared document (CONTRIBUTING_ARCHITECTURE.md, section
 * 6.3.2). An implementation MUST express what it recovered in the library's terms, MUST assign unit
 * identities and order it intends to be final, MUST NOT leak any technology-specific concept, and
 * MUST NOT take on any responsibility beyond extraction and normalization.
 */
public interface DocumentExtractor {

    /**
     * Transforms the given source into the library's normalized representation.
     *
     * @param source the raw document to interpret; never {@code null}
     * @return the normalized prepared document; never {@code null}
     * @throws ExtractionException if the source cannot be interpreted
     */
    PreparedDocument extract(DocumentSource source);

    /**
     * The media types this extractor declares support for, so the orchestrator can route or reject
     * up front rather than failing deep inside a parser (CONTRIBUTING_ARCHITECTURE.md, section
     * 6.3.3).
     *
     * @return an unmodifiable set of supported media types; never {@code null}, possibly empty
     */
    Set<String> supportedMediaTypes();
}
