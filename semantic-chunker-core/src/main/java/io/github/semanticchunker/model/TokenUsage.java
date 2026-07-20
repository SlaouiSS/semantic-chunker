package io.github.semanticchunker.model;

/**
 * The token usage of a single model execution: the input and output token counts.
 *
 * <p>Usage is not optional decoration — it is the source of the cost and observability data
 * surfaced in the {@link io.github.semanticchunker.chunker.ChunkingResult}, and it can only travel
 * back to the core through the {@link ChunkingModel} contract (CONTRIBUTING_ARCHITECTURE.md,
 * section 6.2.2).
 *
 * @param inputTokens the number of input (prompt) tokens; must be non-negative
 * @param outputTokens the number of output (completion) tokens; must be non-negative
 */
public record TokenUsage(int inputTokens, int outputTokens) {

    /** Validates the counts. */
    public TokenUsage {
        if (inputTokens < 0) {
            throw new IllegalArgumentException("inputTokens must be non-negative: " + inputTokens);
        }
        if (outputTokens < 0) {
            throw new IllegalArgumentException(
                    "outputTokens must be non-negative: " + outputTokens);
        }
    }
}
