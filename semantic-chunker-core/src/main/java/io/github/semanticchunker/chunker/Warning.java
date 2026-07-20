package io.github.semanticchunker.chunker;

import java.util.Objects;

/**
 * A notice of something that occurred during a run which the caller ought to know about — most
 * importantly, a window that had to be recovered or handled under the failure policy rather than
 * resolved cleanly (MODEL.md, section 9; CONTRIBUTING_ARCHITECTURE.md, section 9.3).
 *
 * <p>Recovery is never silent: every degraded window, retry, and recoverable anomaly is surfaced as
 * a warning in the {@link ChunkingResult}, so a caller can learn whether a run was entirely clean
 * without the run throwing.
 *
 * @param message a human-readable description of the anomaly; must not be {@code null}
 */
public record Warning(String message) {

    /** Validates the warning. */
    public Warning {
        Objects.requireNonNull(message, "message");
    }
}
