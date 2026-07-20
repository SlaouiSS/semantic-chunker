package io.github.semanticchunker.model;

import java.util.Objects;

/**
 * A language model's raw response to a {@link ChunkingRequest}, delivered back to the library in
 * its own terms.
 *
 * <p>The response carries the raw model text and the {@link TokenUsage} for the execution
 * (CONTRIBUTING_ARCHITECTURE.md, section 6.2.2). The adapter delivers it faithfully; it does not
 * interpret, judge, or validate the content — checking the answer is the library's work (SPI.md,
 * section 4).
 *
 * @param rawText the raw text the model returned; must not be {@code null}
 * @param tokenUsage the token usage of the execution; must not be {@code null}
 */
public record ModelResponse(String rawText, TokenUsage tokenUsage) {

    /** Validates the response. */
    public ModelResponse {
        Objects.requireNonNull(rawText, "rawText");
        Objects.requireNonNull(tokenUsage, "tokenUsage");
    }
}
