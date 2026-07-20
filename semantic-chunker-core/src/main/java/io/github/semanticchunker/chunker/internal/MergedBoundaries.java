package io.github.semanticchunker.chunker.internal;

import java.util.List;

/**
 * One coherent set of boundaries over the whole document, produced by reconciling the validated
 * per-window decisions (MODEL.md, section 7; ARCHITECTURE.md, section 3, "Boundary merging").
 *
 * <p>Where a {@link ValidatedBoundaryDecision} carries the conclusions of a single window, this
 * type carries the reconciled conclusions over the entire document, with no artifact of the
 * windowing surviving. Its boundaries are expressed against the units' stable identities (global
 * ordinals), the same identities the merging depended on to recognize decisions from different
 * windows as referring to the same places.
 *
 * @param boundaryOrdinals the global ordinals naming the units at which the whole document should
 *     be divided; defensively copied and exposed as an unmodifiable list
 */
record MergedBoundaries(List<Integer> boundaryOrdinals) {

    /** Validates and defensively copies. */
    MergedBoundaries {
        boundaryOrdinals = List.copyOf(boundaryOrdinals);
        for (int ordinal : boundaryOrdinals) {
            if (ordinal < 0) {
                throw new IllegalArgumentException(
                        "boundary ordinal must be non-negative: " + ordinal);
            }
        }
    }
}
