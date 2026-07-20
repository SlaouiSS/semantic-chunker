package io.github.semanticchunker.model;

import java.util.Objects;
import java.util.Optional;

/**
 * A framed request for a boundary decision about one window, ready to be executed against a
 * language model (CONTRIBUTING_ARCHITECTURE.md, section 6.2.2).
 *
 * <p>The request is composed by the library, not by the adapter; a {@link ChunkingModel} receives a
 * complete, well-formed request and does not decide what is asked (SPI.md, section 4). It carries
 * the rendered prompt, an optional response-shape hint (so adapters that support provider-native
 * structured output can use it, and adapters that cannot may ignore it), and the normalized
 * generation hints the core cares about: a low temperature and an output-token budget.
 *
 * @param prompt the rendered prompt to present to the model; must not be {@code null}
 * @param responseShapeHint an optional hint describing the shape the response should take; may be
 *     {@code null} when no hint is provided
 * @param temperature the requested sampling temperature; must be non-negative
 * @param maxOutputTokens the output-token budget reserved for the response; must be positive
 */
public record ChunkingRequest(
        String prompt, String responseShapeHint, double temperature, int maxOutputTokens) {

    /** Validates the request. */
    public ChunkingRequest {
        Objects.requireNonNull(prompt, "prompt");
        if (temperature < 0.0d) {
            throw new IllegalArgumentException("temperature must be non-negative: " + temperature);
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException(
                    "maxOutputTokens must be positive: " + maxOutputTokens);
        }
    }

    /**
     * The optional response-shape hint.
     *
     * @return the hint, or an empty optional when none was provided
     */
    public Optional<String> responseShape() {
        return Optional.ofNullable(responseShapeHint);
    }
}
