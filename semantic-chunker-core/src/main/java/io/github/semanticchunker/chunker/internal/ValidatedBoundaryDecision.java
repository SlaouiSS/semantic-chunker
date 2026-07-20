package io.github.semanticchunker.chunker.internal;

import java.util.List;

/**
 * A boundary decision for one window that has been confirmed well-formed and to name real units of
 * the document (MODEL.md, section 7).
 *
 * <p>A boundary decision is expressed in terms of stable identities: it names, by global ordinal,
 * the units at which the document should be divided. Because it is anchored to identities rather
 * than to positions within a transient window, it remains meaningful independently of the window
 * that produced it, which is what allows the boundary merger to reconcile decisions across windows.
 *
 * <p>The model distinguishes raw from validated decisions. The raw form is the model's unchecked
 * answer, carried by {@link io.github.semanticchunker.model.ModelResponse}; only a decision that
 * has been validated is represented by this type, and only validated decisions proceed to merging.
 *
 * @param boundaryOrdinals the global ordinals naming the units at which the document should be
 *     divided within this window; defensively copied and exposed as an unmodifiable list
 */
record ValidatedBoundaryDecision(List<Integer> boundaryOrdinals) {

    /** Validates and defensively copies. */
    ValidatedBoundaryDecision {
        boundaryOrdinals = List.copyOf(boundaryOrdinals);
        for (int ordinal : boundaryOrdinals) {
            if (ordinal < 0) {
                throw new IllegalArgumentException(
                        "boundary ordinal must be non-negative: " + ordinal);
            }
        }
    }
}
