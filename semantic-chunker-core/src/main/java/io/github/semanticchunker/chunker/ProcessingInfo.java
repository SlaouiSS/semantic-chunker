package io.github.semanticchunker.chunker;

/**
 * An account of the work performed on the way from document to chunks — part of the {@link
 * ChunkingResult} beyond the chunks themselves (MODEL.md, section 9).
 *
 * <p>It records how many windows the document was processed as and how many of those had to be
 * degraded under the failure policy (CONTRIBUTING_ARCHITECTURE.md, sections 9.2–9.3), so a caller
 * can understand the shape of the run.
 *
 * @param windowsProcessed the number of windows the document was processed as; must be non-negative
 * @param windowsDegraded the number of windows degraded under the failure policy; must be
 *     non-negative and no greater than {@code windowsProcessed}
 */
public record ProcessingInfo(int windowsProcessed, int windowsDegraded) {

    /** Validates the counts. */
    public ProcessingInfo {
        if (windowsProcessed < 0) {
            throw new IllegalArgumentException(
                    "windowsProcessed must be non-negative: " + windowsProcessed);
        }
        if (windowsDegraded < 0) {
            throw new IllegalArgumentException(
                    "windowsDegraded must be non-negative: " + windowsDegraded);
        }
        if (windowsDegraded > windowsProcessed) {
            throw new IllegalArgumentException(
                    "windowsDegraded ("
                            + windowsDegraded
                            + ") must not exceed windowsProcessed ("
                            + windowsProcessed
                            + ")");
        }
    }
}
