package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.github.semanticchunker.chunker.SemanticChunk;
import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.PreparedDocument;
import io.github.semanticchunker.document.Provenance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Behavioral tests for the chunk-construction stage (CHUNK_CONSTRUCTION.md). */
class ChunkAssemblerTest {

    private final ChunkAssembler assembler = new ChunkAssembler();

    /**
     * A unit whose stable identity is {@code ordinal}, with distinctive provenance and metadata.
     */
    private static DocumentUnit unit(int ordinal) {
        return new Paragraph(
                new Provenance(ordinal, ordinal / 3 + 1, ordinal * 10, ordinal * 10 + 5),
                "content-" + ordinal,
                Map.of("source", "u" + ordinal));
    }

    private static PreparedDocument document(DocumentUnit... units) {
        return new PreparedDocument(List.of(units), Map.of());
    }

    private static MergedBoundaries boundaries(int... ordinals) {
        List<Integer> values = new ArrayList<>();
        for (int ordinal : ordinals) {
            values.add(ordinal);
        }
        return new MergedBoundaries(values);
    }

    private List<SemanticChunk> assemble(PreparedDocument document, MergedBoundaries boundaries) {
        return assembler.assemble(boundaries, document);
    }

    /** The global ordinals of a chunk's units, in order — the observable shape of a chunk. */
    private static List<Integer> ordinalsOf(SemanticChunk chunk) {
        return chunk.units().stream().map(unit -> unit.provenance().globalOrdinal()).toList();
    }

    private static List<List<Integer>> ordinalsOf(List<SemanticChunk> chunks) {
        return chunks.stream().map(ChunkAssemblerTest::ordinalsOf).toList();
    }

    // ---- inputs / null guards ----

    @Test
    void rejectsNullBoundaries() {
        assertThatNullPointerException()
                .isThrownBy(() -> assembler.assemble(null, document(unit(0))));
    }

    @Test
    void rejectsNullDocument() {
        assertThatNullPointerException().isThrownBy(() -> assembler.assemble(boundaries(), null));
    }

    // ---- empty document (§12) ----

    @Test
    void emptyDocumentProducesNoChunks() {
        assertThat(assemble(document(), boundaries())).isEmpty();
    }

    // ---- single chunk (§10) ----

    @Test
    void aDocumentWithNoBoundariesBecomesOneChunkOfAllUnits() {
        List<SemanticChunk> chunks = assemble(document(unit(0), unit(1), unit(2)), boundaries());

        assertThat(ordinalsOf(chunks)).containsExactly(List.of(0, 1, 2));
    }

    @Test
    void aSingleUnitDocumentBecomesOneSingleUnitChunk() {
        List<SemanticChunk> chunks = assemble(document(unit(0)), boundaries());

        assertThat(ordinalsOf(chunks)).containsExactly(List.of(0));
    }

    // ---- multiple chunks (§3) ----

    @Test
    void aSingleBoundarySplitsTheDocumentIntoTwoChunks() {
        List<SemanticChunk> chunks =
                assemble(document(unit(0), unit(1), unit(2), unit(3)), boundaries(2));

        assertThat(ordinalsOf(chunks)).containsExactly(List.of(0, 1), List.of(2, 3));
    }

    @Test
    void multipleBoundariesSplitTheDocumentIntoContiguousRuns() {
        List<SemanticChunk> chunks =
                assemble(
                        document(unit(0), unit(1), unit(2), unit(3), unit(4), unit(5)),
                        boundaries(2, 4));

        assertThat(ordinalsOf(chunks)).containsExactly(List.of(0, 1), List.of(2, 3), List.of(4, 5));
    }

    // ---- boundary at the extremes (§3, §11) ----

    @Test
    void aBoundaryAtTheFirstPossiblePositionIsolatesTheFirstUnit() {
        // The earliest a boundary may fall is the second unit (the first can never be a boundary).
        List<SemanticChunk> chunks =
                assemble(document(unit(0), unit(1), unit(2), unit(3)), boundaries(1));

        assertThat(ordinalsOf(chunks)).containsExactly(List.of(0), List.of(1, 2, 3));
    }

    @Test
    void aBoundaryAtTheLastPossiblePositionIsolatesTheLastUnit() {
        List<SemanticChunk> chunks =
                assemble(document(unit(0), unit(1), unit(2), unit(3)), boundaries(3));

        assertThat(ordinalsOf(chunks)).containsExactly(List.of(0, 1, 2), List.of(3));
    }

    @Test
    void theFinalChunkAlwaysExtendsToTheLastUnit() {
        List<SemanticChunk> chunks =
                assemble(document(unit(0), unit(1), unit(2), unit(3), unit(4)), boundaries(2));

        List<SemanticChunk> finalChunk = List.of(chunks.get(chunks.size() - 1));
        assertThat(ordinalsOf(finalChunk)).containsExactly(List.of(2, 3, 4));
    }

    // ---- adjacent boundaries (§8) ----

    @Test
    void adjacentBoundariesProduceASingleUnitChunkBetweenThem() {
        List<SemanticChunk> chunks =
                assemble(document(unit(0), unit(1), unit(2), unit(3), unit(4)), boundaries(2, 3));

        assertThat(ordinalsOf(chunks)).containsExactly(List.of(0, 1), List.of(2), List.of(3, 4));
    }

    @Test
    void aBoundaryAtEveryPossiblePositionProducesAllSingleUnitChunks() {
        List<SemanticChunk> chunks =
                assemble(document(unit(0), unit(1), unit(2)), boundaries(1, 2));

        assertThat(ordinalsOf(chunks)).containsExactly(List.of(0), List.of(1), List.of(2));
    }

    // ---- boundaries key on global ordinal, not list index (§1, §3) ----

    @Test
    void partitionsByGlobalOrdinalEvenWhenOrdinalsAreNonContiguous() {
        // Units at ordinals 0,5,10,15 (indices 0..3). The boundary names ordinal 10, not index 10.
        List<SemanticChunk> chunks =
                assemble(document(unit(0), unit(5), unit(10), unit(15)), boundaries(10));

        assertThat(ordinalsOf(chunks)).containsExactly(List.of(0, 5), List.of(10, 15));
    }

    // ---- partition guarantees (§8, §9) ----

    @Test
    void everyChunkContainsAtLeastOneUnit() {
        List<SemanticChunk> chunks =
                assemble(
                        document(unit(0), unit(1), unit(2), unit(3), unit(4)), boundaries(1, 2, 4));

        assertThat(chunks).isNotEmpty().allSatisfy(chunk -> assertThat(chunk.units()).isNotEmpty());
    }

    @Test
    void theChunksAreACompletePartitionWithNoMissingOrDuplicatedUnits() {
        PreparedDocument document =
                document(unit(0), unit(1), unit(2), unit(3), unit(4), unit(5), unit(6));

        List<SemanticChunk> chunks = assemble(document, boundaries(2, 3, 6));

        List<DocumentUnit> flattened =
                chunks.stream().flatMap(chunk -> chunk.units().stream()).toList();
        // Total coverage and no overlap: the concatenated chunk units are exactly the document's
        // units, once each, in document order (§9).
        assertThat(flattened).containsExactlyElementsOf(document.units());
    }

    @Test
    void preservesDocumentOrderAcrossAndWithinChunks() {
        List<SemanticChunk> chunks =
                assemble(document(unit(0), unit(1), unit(2), unit(3), unit(4)), boundaries(2, 4));

        List<Integer> flattenedOrdinals =
                chunks.stream()
                        .flatMap(chunk -> chunk.units().stream())
                        .map(unit -> unit.provenance().globalOrdinal())
                        .toList();
        assertThat(flattenedOrdinals).containsExactly(0, 1, 2, 3, 4).isSorted();
    }

    // ---- preservation (§5, §6, §7) ----

    @Test
    void referencesTheSameUnitInstancesTheDocumentEstablished() {
        DocumentUnit first = unit(0);
        DocumentUnit second = unit(1);
        DocumentUnit third = unit(2);
        PreparedDocument document = document(first, second, third);

        List<SemanticChunk> chunks = assemble(document, boundaries(2));

        // Identity preserved: chunks reference the very same instances, not copies (§5).
        assertThat(chunks.get(0).units()).containsExactly(first, second);
        assertThat(chunks.get(0).units().get(0)).isSameAs(first);
        assertThat(chunks.get(0).units().get(1)).isSameAs(second);
        assertThat(chunks.get(1).units().get(0)).isSameAs(third);
    }

    @Test
    void carriesEachUnitsProvenanceUnchangedIntoItsChunk() {
        DocumentUnit onlyUnit = unit(7);
        PreparedDocument document = document(unit(0), unit(1), onlyUnit);

        List<SemanticChunk> chunks = assemble(document, boundaries(7));

        DocumentUnit chunked = chunks.get(1).units().get(0);
        assertThat(chunked.provenance()).isEqualTo(onlyUnit.provenance());
        assertThat(chunked.provenance().globalOrdinal()).isEqualTo(7);
        assertThat(chunked.provenance().page()).isEqualTo(onlyUnit.provenance().page());
        assertThat(chunked.provenance().startOffset())
                .isEqualTo(onlyUnit.provenance().startOffset());
        assertThat(chunked.provenance().endOffset()).isEqualTo(onlyUnit.provenance().endOffset());
    }

    @Test
    void carriesEachUnitsMetadataUnchangedIntoItsChunk() {
        DocumentUnit onlyUnit = unit(4);
        PreparedDocument document = document(unit(0), unit(1), unit(2), unit(3), onlyUnit);

        List<SemanticChunk> chunks = assemble(document, boundaries(4));

        DocumentUnit chunked = chunks.get(1).units().get(0);
        assertThat(chunked.metadata()).isEqualTo(Map.of("source", "u4"));
    }

    // ---- determinism (§13) ----

    @Test
    void isDeterministicForIdenticalInputs() {
        PreparedDocument document = document(unit(0), unit(1), unit(2), unit(3), unit(4), unit(5));
        MergedBoundaries boundaries = boundaries(2, 5);

        assertThat(assembler.assemble(boundaries, document))
                .isEqualTo(assembler.assemble(boundaries, document));
    }

    // ---- large document ----

    @Test
    void partitionsALargeDocumentIntoTheExpectedContiguousChunks() {
        int unitCount = 1_000;
        int stride = 10;
        List<DocumentUnit> units = new ArrayList<>();
        List<Integer> boundaryOrdinals = new ArrayList<>();
        for (int ordinal = 0; ordinal < unitCount; ordinal++) {
            units.add(unit(ordinal));
            if (ordinal != 0 && ordinal % stride == 0) {
                boundaryOrdinals.add(ordinal);
            }
        }
        PreparedDocument document = new PreparedDocument(units, Map.of());

        List<SemanticChunk> chunks =
                assembler.assemble(new MergedBoundaries(boundaryOrdinals), document);

        assertThat(chunks).hasSize(unitCount / stride);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.units()).hasSize(stride));
        // Complete partition: concatenation is exactly the document, once each, in order.
        List<DocumentUnit> flattened =
                chunks.stream().flatMap(chunk -> chunk.units().stream()).toList();
        assertThat(flattened).containsExactlyElementsOf(units);
    }

    // ---- terminal failures (§14) ----

    @Test
    void failsTerminallyWhenABoundaryNamesNoUnitOfTheDocument() {
        assertThatIllegalStateException()
                .isThrownBy(() -> assemble(document(unit(0), unit(1), unit(2)), boundaries(9)))
                .withMessageContaining("9");
    }

    @Test
    void failsTerminallyWhenABoundaryNamesTheDocumentsFirstUnit() {
        assertThatIllegalStateException()
                .isThrownBy(() -> assemble(document(unit(0), unit(1), unit(2)), boundaries(0)))
                .withMessageContaining("first unit");
    }

    @Test
    void failsTerminallyWhenAnEmptyDocumentCarriesABoundary() {
        assertThatIllegalStateException().isThrownBy(() -> assemble(document(), boundaries(0)));
    }
}
