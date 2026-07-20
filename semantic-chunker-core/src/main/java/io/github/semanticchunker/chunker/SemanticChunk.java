package io.github.semanticchunker.chunker;

import io.github.semanticchunker.document.DocumentUnit;
import java.util.List;

/**
 * A contiguous, self-contained section of the document — the material lying between one boundary
 * and the next, and the library's primary output (MODEL.md, section 8).
 *
 * <p>A chunk is defined by the units it comprises, not by an independent copy of their content: it
 * references a contiguous run of the prepared document's {@link DocumentUnit}s, in document order,
 * bearing their original identities and provenance. Through those units and their provenance a
 * chunk remains traceable to the exact material of the source.
 *
 * @param units the contiguous run of units, in document order, that compose this chunk; must not be
 *     {@code null}, defensively copied and exposed as an unmodifiable list
 */
public record SemanticChunk(List<DocumentUnit> units) {

    /** Validates and defensively copies. */
    public SemanticChunk {
        units = List.copyOf(units);
    }
}
