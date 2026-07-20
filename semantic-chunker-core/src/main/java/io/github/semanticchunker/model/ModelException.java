package io.github.semanticchunker.model;

import io.github.semanticchunker.SemanticChunkerException;

/**
 * Signals that a {@link ChunkingModel} could not reach its provider or obtain a response.
 *
 * <p>It is the failure a model adapter surfaces when it cannot do its work; the library then
 * decides how to respond — retry, fall back and warn, or, for a fundamental problem such as an
 * unreachable model, end the operation (PIPELINE.md, section 7; CONTRIBUTING_ARCHITECTURE.md,
 * section 9.1).
 */
public final class ModelException extends SemanticChunkerException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with the given message.
     *
     * @param message a description of what failed
     */
    public ModelException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and underlying cause.
     *
     * @param message a description of what failed
     * @param cause the underlying cause, may be {@code null}
     */
    public ModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
