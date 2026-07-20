package io.github.semanticchunker.chunker;

import io.github.semanticchunker.SemanticChunkerException;

/**
 * Signals a genuine, non-recoverable failure of the chunking operation itself — as opposed to a
 * localized, recoverable difficulty, which the library absorbs and reports as a {@link Warning}
 * rather than throwing (API.md, section 8; CONTRIBUTING_ARCHITECTURE.md, section 9).
 */
public final class ChunkingException extends SemanticChunkerException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with the given message.
     *
     * @param message a description of what failed
     */
    public ChunkingException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and underlying cause.
     *
     * @param message a description of what failed
     * @param cause the underlying cause, may be {@code null}
     */
    public ChunkingException(String message, Throwable cause) {
        super(message, cause);
    }
}
