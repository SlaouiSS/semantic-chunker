package io.github.semanticchunker;

/**
 * Common supertype of every exception the {@code semantic-chunker} library raises.
 *
 * <p>The library's failure model is unchecked and typed (CONTRIBUTING_ARCHITECTURE.md, section 9):
 * this class extends {@link RuntimeException}, and the three concrete failures — {@link
 * io.github.semanticchunker.extraction.ExtractionException}, {@link
 * io.github.semanticchunker.chunker.ChunkingException}, and {@link
 * io.github.semanticchunker.model.ModelException} — extend it. Catching this type catches any
 * genuine, non-recoverable failure the library reports.
 *
 * <p>An exception from the library signals a real problem that prevented work from completing, not
 * a recoverable difficulty; recoverable, localized trouble is absorbed and surfaced as warnings in
 * the {@link io.github.semanticchunker.chunker.ChunkingResult} rather than thrown (API.md, section
 * 8).
 */
public abstract class SemanticChunkerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with the given message.
     *
     * @param message a description of what failed
     */
    protected SemanticChunkerException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and underlying cause.
     *
     * @param message a description of what failed
     * @param cause the underlying cause, may be {@code null}
     */
    protected SemanticChunkerException(String message, Throwable cause) {
        super(message, cause);
    }
}
