package io.github.semanticchunker.extraction;

import io.github.semanticchunker.SemanticChunkerException;

/**
 * Signals that a {@link DocumentExtractor} could not interpret a source into a prepared document.
 *
 * <p>The extractor validates that it can handle the source and throws this typed exception when it
 * cannot, rather than returning partial or fabricated data (CONTRIBUTING_ARCHITECTURE.md, sections
 * 6.3.4 and 9.1).
 */
public final class ExtractionException extends SemanticChunkerException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with the given message.
     *
     * @param message a description of what failed
     */
    public ExtractionException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and underlying cause.
     *
     * @param message a description of what failed
     * @param cause the underlying cause, may be {@code null}
     */
    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
