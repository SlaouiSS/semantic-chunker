package io.github.semanticchunker.chunker;

import io.github.semanticchunker.model.TokenUsage;
import java.util.Objects;

/**
 * The record of what a run consumed and involved — the information a caller needs to understand the
 * cost and character of the process (MODEL.md, section 9).
 *
 * <p>The token usage aggregated here is the source of the cost and observability data the model
 * contract carries back (CONTRIBUTING_ARCHITECTURE.md, section 6.2.2).
 *
 * @param totalTokenUsage the total token usage across every model execution in the run; must not be
 *     {@code null}
 */
public record ExecutionMetadata(TokenUsage totalTokenUsage) {

    /** Validates the metadata. */
    public ExecutionMetadata {
        Objects.requireNonNull(totalTokenUsage, "totalTokenUsage");
    }
}
