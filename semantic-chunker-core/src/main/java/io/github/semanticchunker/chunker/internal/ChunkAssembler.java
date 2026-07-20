package io.github.semanticchunker.chunker.internal;

import io.github.semanticchunker.chunker.SemanticChunk;
import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.PreparedDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the semantic chunks from the merged boundaries and the document's units — each chunk the
 * contiguous run of units lying between one boundary and the next (ARCHITECTURE.md, section 3,
 * "Semantic chunks"; PIPELINE.md, section 3, "Chunk Construction"; CHUNK_CONSTRUCTION.md).
 *
 * <p>Chunks reference the prepared document's units directly, in document order: this stage copies
 * no content and creates no independent representation of a unit, so each unit's identity (global
 * ordinal), provenance, and metadata are carried through unchanged (§5–§7). Applying the boundaries
 * is a pure partition of the ordered units — it neither adds, removes, nor alters any unit (§3).
 *
 * <p>The set of positions at which a chunk begins is exactly the document's first unit together
 * with the merged boundaries (§3). Each chunk runs from one start position up to the unit
 * immediately before the next, and the final chunk runs to the last unit (§11). The result is a
 * true partition: every unit belongs to exactly one chunk, none is duplicated, and none is omitted
 * (§9).
 *
 * <p>Construction is a pure, total, deterministic function of the two inputs (§13, §14): the same
 * units and the same boundary set always yield the identical ordered list of chunks.
 */
class ChunkAssembler {

    ChunkAssembler() {}

    /**
     * Assembles the ordered semantic chunks by partitioning the document's units at the merged
     * boundaries.
     *
     * @param boundaries the reconciled boundaries over the document, naming by global ordinal the
     *     units at which a new chunk begins; never {@code null}
     * @param document the prepared document whose units the chunks are composed from; never {@code
     *     null}
     * @return the ordered semantic chunks partitioning all of the document's units, or the empty
     *     list for an empty document (§12)
     * @throws IllegalStateException if a boundary names no unit of the document or names the
     *     document's first unit — merge guarantees (BOUNDARY_MERGING.md §10) that this stage
     *     assumes, whose violation is an internal defect and a terminal failure (§14)
     */
    List<SemanticChunk> assemble(MergedBoundaries boundaries, PreparedDocument document) {
        Objects.requireNonNull(boundaries, "boundaries");
        Objects.requireNonNull(document, "document");

        List<DocumentUnit> units = document.units();
        Set<Integer> boundaryOrdinals = Set.copyOf(boundaries.boundaryOrdinals());
        verifyBoundariesNameRealNonFirstUnits(boundaryOrdinals, units);

        if (units.isEmpty()) {
            return List.of();
        }

        List<SemanticChunk> chunks = new ArrayList<>();
        int chunkStart = 0;
        // The first unit always begins the first chunk (§3), so a cut is only ever taken *before* a
        // later unit whose global ordinal is a boundary. Boundaries never name the first unit, so
        // no
        // zero-length segment can arise (§8).
        for (int index = 1; index < units.size(); index++) {
            if (boundaryOrdinals.contains(units.get(index).provenance().globalOrdinal())) {
                chunks.add(new SemanticChunk(units.subList(chunkStart, index)));
                chunkStart = index;
            }
        }
        chunks.add(new SemanticChunk(units.subList(chunkStart, units.size())));
        return List.copyOf(chunks);
    }

    /**
     * Enforces the merge guarantees this stage relies on (§14): every boundary names a real unit of
     * the document and none names the document's first unit. A violation is an upstream internal
     * defect, not a recoverable condition, and fails terminally.
     */
    private static void verifyBoundariesNameRealNonFirstUnits(
            Set<Integer> boundaryOrdinals, List<DocumentUnit> units) {
        Set<Integer> unitOrdinals =
                units.stream()
                        .map(unit -> unit.provenance().globalOrdinal())
                        .collect(Collectors.toSet());
        for (int boundary : boundaryOrdinals) {
            if (!unitOrdinals.contains(boundary)) {
                throw new IllegalStateException(
                        "merged boundary names no unit of the document: "
                                + boundary
                                + "; the merge guarantee (BOUNDARY_MERGING.md §10) that chunk"
                                + " construction assumes was violated upstream");
            }
        }
        if (!units.isEmpty()) {
            int firstUnitOrdinal = units.get(0).provenance().globalOrdinal();
            if (boundaryOrdinals.contains(firstUnitOrdinal)) {
                throw new IllegalStateException(
                        "merged boundary names the document's first unit: "
                                + firstUnitOrdinal
                                + "; the merge guarantee (BOUNDARY_MERGING.md §10) that chunk"
                                + " construction assumes was violated upstream");
            }
        }
    }
}
