package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.semanticchunker.chunker.ChunkingException;
import io.github.semanticchunker.chunker.ChunkingResult;
import io.github.semanticchunker.chunker.ProcessingInfo;
import io.github.semanticchunker.chunker.SemanticChunk;
import io.github.semanticchunker.chunker.SemanticChunker;
import io.github.semanticchunker.chunker.Warning;
import io.github.semanticchunker.document.DocumentSource;
import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.PreparedDocument;
import io.github.semanticchunker.document.Provenance;
import io.github.semanticchunker.extraction.DocumentExtractor;
import io.github.semanticchunker.extraction.ExtractionException;
import io.github.semanticchunker.model.ChunkingModel;
import io.github.semanticchunker.model.ChunkingRequest;
import io.github.semanticchunker.model.ModelException;
import io.github.semanticchunker.model.ModelResponse;
import io.github.semanticchunker.model.TokenUsage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration tests exercising the whole pipeline through the public {@link
 * SemanticChunker} facade with the real internal stages wired together (only the model and, where
 * relevant, the extractor are test doubles). Every behavioral specification is exercised here by at
 * least one full-pipeline run.
 */
class PipelineIntegrationTest {

    // Window planning at these settings mirrors the documented arithmetic: with a per-unit content
    // cost of 20 and a budget of 100, the safety margin is ceil(100/20)=5 and a maximal-response
    // reservation of 1, so at most four units fit per window (80+5+1<=100; 100+5+1>100) and
    // adjacent
    // windows overlap by OVERLAP_UNITS (2).
    private static final int MULTI_WINDOW_PER_UNIT_COST = 20;
    private static final int MULTI_WINDOW_BUDGET = 100;

    // A per-unit cost of 1 against a very large budget places the whole document in one window.
    private static final int SINGLE_WINDOW_PER_UNIT_COST = 1;
    private static final int SINGLE_WINDOW_BUDGET = 100_000;

    // ---- helpers ----

    private static DocumentUnit unit(int ordinal) {
        return new Paragraph(
                new Provenance(ordinal, 1, ordinal, ordinal + 1), "text-" + ordinal, Map.of());
    }

    private static List<DocumentUnit> units(int count) {
        List<DocumentUnit> units = new ArrayList<>();
        for (int ordinal = 0; ordinal < count; ordinal++) {
            units.add(unit(ordinal));
        }
        return units;
    }

    private static ModelResponse response(String rawText, int inputTokens, int outputTokens) {
        return new ModelResponse(rawText, new TokenUsage(inputTokens, outputTokens));
    }

    /**
     * A token estimator that returns {@code perUnitCost * run.size()} for each content rendering
     * and a fixed positive cost for each maximal-response rendering, keyed on the exact strings the
     * real {@link RequestRepresentation} produces, so window formation is fully determined.
     */
    private static ToIntFunction<String> estimator(List<DocumentUnit> allUnits, int perUnitCost) {
        RequestRepresentation representation = new RequestRepresentation();
        Map<String, Integer> costByRendering = new HashMap<>();
        int size = allUnits.size();
        for (int i = 0; i < size; i++) {
            for (int j = i; j < size; j++) {
                List<DocumentUnit> run = allUnits.subList(i, j + 1);
                costByRendering.put(representation.renderContent(run), perUnitCost * run.size());
                costByRendering.put(representation.renderMaximalResponse(run), 1);
            }
        }
        costByRendering.put(representation.renderMaximalResponse(List.of()), 1);
        return text -> costByRendering.getOrDefault(text, 0);
    }

    private static SemanticChunker chunkerWith(ChunkingModel model) {
        return SemanticChunker.builder()
                .documentExtractor(mock(DocumentExtractor.class))
                .chunkingModel(model)
                .build();
    }

    private static ChunkingResult chunk(
            List<DocumentUnit> units, int perUnitCost, int budget, List<ModelResponse> script) {
        ScriptedModel model = new ScriptedModel(budget, estimator(units, perUnitCost), script);
        return chunkerWith(model).chunk(new PreparedDocument(units, Map.of()));
    }

    private static List<List<Integer>> chunkOrdinals(ChunkingResult result) {
        return result.chunks().stream().map(PipelineIntegrationTest::ordinalsOf).toList();
    }

    private static List<Integer> ordinalsOf(SemanticChunk chunk) {
        return chunk.units().stream().map(u -> u.provenance().globalOrdinal()).toList();
    }

    // ---- complete successful pipeline (all stages) ----

    @Test
    void aSingleWindowDocumentIsChunkedAtTheReportedBoundary() {
        List<DocumentUnit> units = units(4);

        ChunkingResult result =
                chunk(
                        units,
                        SINGLE_WINDOW_PER_UNIT_COST,
                        SINGLE_WINDOW_BUDGET,
                        List.of(response("[2]", 5, 3)));

        assertThat(chunkOrdinals(result)).containsExactly(List.of(0, 1), List.of(2, 3));
        assertThat(result.warnings()).isEmpty();
        assertThat(result.processingInfo().windowsProcessed()).isEqualTo(1);
        assertThat(result.processingInfo().windowsDegraded()).isZero();
        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(5, 3));
    }

    @Test
    void aDocumentTheModelFindsCoherentBecomesASingleChunk() {
        List<DocumentUnit> units = units(3);

        ChunkingResult result =
                chunk(
                        units,
                        SINGLE_WINDOW_PER_UNIT_COST,
                        SINGLE_WINDOW_BUDGET,
                        List.of(response("[]", 4, 1)));

        assertThat(chunkOrdinals(result)).containsExactly(List.of(0, 1, 2));
        assertThat(result.warnings()).isEmpty();
        assertThat(result.processingInfo()).isEqualTo(new ProcessingInfo(1, 0));
    }

    // ---- empty document (CHUNK_CONSTRUCTION §12, WINDOW_PLANNING §11) ----

    @Test
    void anEmptyDocumentProducesNoChunksAndTouchesNoModel() {
        ScriptedModel model = new ScriptedModel(SINGLE_WINDOW_BUDGET, text -> 0, List.of());

        ChunkingResult result = chunkerWith(model).chunk(new PreparedDocument(List.of(), Map.of()));

        assertThat(result.chunks()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.processingInfo()).isEqualTo(new ProcessingInfo(0, 0));
        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(0, 0));
        assertThat(model.callCount()).isZero();
    }

    // ---- single-unit document (RECOVERY_POLICY §7) ----

    @Test
    void aSingleUnitDocumentBecomesOneChunkWithoutAnyModelCall() {
        List<DocumentUnit> units = units(1);
        ScriptedModel model =
                new ScriptedModel(
                        SINGLE_WINDOW_BUDGET,
                        estimator(units, SINGLE_WINDOW_PER_UNIT_COST),
                        List.of());

        ChunkingResult result = chunkerWith(model).chunk(new PreparedDocument(units, Map.of()));

        assertThat(chunkOrdinals(result)).containsExactly(List.of(0));
        assertThat(result.warnings()).isEmpty();
        assertThat(result.processingInfo()).isEqualTo(new ProcessingInfo(1, 0));
        assertThat(model.callCount()).isZero();
    }

    // ---- large multi-window document: planning, overlap, merge, chunking ----

    @Test
    void aLargeDocumentIsPlannedIntoOverlappingWindowsAndMergedIntoCorrectChunks() {
        List<DocumentUnit> units = units(8);
        // Windows [0-3],[2-5],[4-7]; each reports its own central boundary, which it owns.
        List<ModelResponse> script =
                List.of(response("[2]", 1, 1), response("[4]", 1, 1), response("[6]", 1, 1));

        ChunkingResult result =
                chunk(units, MULTI_WINDOW_PER_UNIT_COST, MULTI_WINDOW_BUDGET, script);

        assertThat(chunkOrdinals(result))
                .containsExactly(List.of(0, 1), List.of(2, 3), List.of(4, 5), List.of(6, 7));
        assertThat(result.warnings()).isEmpty();
        assertThat(result.processingInfo().windowsProcessed()).isEqualTo(3);
        assertThat(result.processingInfo().windowsDegraded()).isZero();
        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(3, 3));
    }

    // ---- retry success (RECOVERY_POLICY §3–§5) ----

    @Test
    void aWindowWhoseFirstResponseIsRejectedSucceedsOnRetryWithoutWarning() {
        List<DocumentUnit> units = units(4);
        List<ModelResponse> script = List.of(response("not-a-list", 2, 1), response("[2]", 3, 2));

        ChunkingResult result =
                chunk(units, SINGLE_WINDOW_PER_UNIT_COST, SINGLE_WINDOW_BUDGET, script);

        assertThat(chunkOrdinals(result)).containsExactly(List.of(0, 1), List.of(2, 3));
        assertThat(result.warnings()).isEmpty();
        assertThat(result.processingInfo().windowsDegraded()).isZero();
        // Both attempts, including the rejected one, are accounted for (RECOVERY_POLICY §11).
        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(5, 3));
    }

    // ---- retry exhaustion / degradation (RECOVERY_POLICY §4, §6, §7) ----

    @Test
    void aWindowRejectedOnEveryAttemptIsDegradedToASingleChunkWithAWarning() {
        List<DocumentUnit> units = units(4);
        List<ModelResponse> script =
                List.of(response("bad", 2, 2), response("bad", 2, 2), response("bad", 2, 2));

        ChunkingResult result =
                chunk(units, SINGLE_WINDOW_PER_UNIT_COST, SINGLE_WINDOW_BUDGET, script);

        // A degraded window declines to divide: the whole document is one chunk.
        assertThat(chunkOrdinals(result)).containsExactly(List.of(0, 1, 2, 3));
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0).message()).contains("0-3", "retry exhausted");
        assertThat(result.processingInfo()).isEqualTo(new ProcessingInfo(1, 1));
        // Every rejected attempt contributed tokens (RECOVERY_POLICY §11).
        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(6, 6));
    }

    // ---- over-budget degradation (WINDOW_PLANNING §12, RECOVERY_POLICY §6) ----

    @Test
    void anOverBudgetWindowIsDegradedWithAWarningAndWithoutAModelCall() {
        List<DocumentUnit> units = units(1);
        // A single unit whose content alone exceeds the budget: an over-budget window.
        ScriptedModel model = new ScriptedModel(50, estimator(units, 1_000), List.of());

        ChunkingResult result = chunkerWith(model).chunk(new PreparedDocument(units, Map.of()));

        assertThat(chunkOrdinals(result)).containsExactly(List.of(0));
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0).message()).contains("0-0", "over-budget");
        assertThat(result.processingInfo()).isEqualTo(new ProcessingInfo(1, 1));
        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(0, 0));
        assertThat(model.callCount()).isZero();
    }

    // ---- multiple degraded windows: warning ordering (RECOVERY_POLICY §9) ----

    @Test
    void multipleDegradedWindowsRecordOrderedWarningsAndCollapseToOneChunk() {
        List<DocumentUnit> units = units(8);
        // Three windows [0-3],[2-5],[4-7]; every attempt is rejected, so all three degrade.
        List<ModelResponse> script = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            script.add(response("bad", 1, 0));
        }

        ChunkingResult result =
                chunk(units, MULTI_WINDOW_PER_UNIT_COST, MULTI_WINDOW_BUDGET, script);

        // No window contributed a cut, so the whole document is a single chunk.
        assertThat(chunkOrdinals(result)).containsExactly(List.of(0, 1, 2, 3, 4, 5, 6, 7));
        assertThat(result.warnings())
                .extracting(Warning::message)
                .hasSize(3)
                .satisfies(m -> assertThat(m.get(0)).contains("0-3"))
                .satisfies(m -> assertThat(m.get(1)).contains("2-5"))
                .satisfies(m -> assertThat(m.get(2)).contains("4-7"));
        assertThat(result.processingInfo()).isEqualTo(new ProcessingInfo(3, 3));
    }

    // ---- mixed accepted and degraded windows ----

    @Test
    void aMixOfAcceptedAndDegradedWindowsMergesAndChunksCorrectly() {
        List<DocumentUnit> units = units(8);
        // w0 [0-3] accepts [2]; w1 [2-5] is rejected three times (degraded); w2 [4-7] accepts [6].
        List<ModelResponse> script =
                List.of(
                        response("[2]", 4, 2),
                        response("bad", 1, 1),
                        response("bad", 1, 1),
                        response("bad", 1, 1),
                        response("[6]", 2, 3));

        ChunkingResult result =
                chunk(units, MULTI_WINDOW_PER_UNIT_COST, MULTI_WINDOW_BUDGET, script);

        // w1 is authoritative for its central region and, being degraded, cuts nothing there; the
        // accepted boundaries 2 and 6 (each owned by its reporting window) survive.
        assertThat(chunkOrdinals(result))
                .containsExactly(List.of(0, 1), List.of(2, 3, 4, 5), List.of(6, 7));
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0).message()).contains("2-5", "retry exhausted");
        assertThat(result.processingInfo()).isEqualTo(new ProcessingInfo(3, 1));
        // 4+2 accepted plus 3 rejected attempts (RECOVERY_POLICY §11).
        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(9, 8));
    }

    // ---- unit preservation through the whole pipeline (CHUNK_CONSTRUCTION §5–§7) ----

    @Test
    void chunksReferenceTheOriginalUnitsWithTheirProvenanceAndMetadataIntact() {
        DocumentUnit first =
                new Paragraph(new Provenance(0, 2, 10, 15), "alpha", Map.of("k", "v0"));
        DocumentUnit second =
                new Paragraph(new Provenance(1, 2, 16, 20), "beta", Map.of("k", "v1"));
        DocumentUnit third =
                new Paragraph(new Provenance(2, 3, 21, 30), "gamma", Map.of("k", "v2"));
        List<DocumentUnit> units = List.of(first, second, third);

        ChunkingResult result =
                chunk(
                        units,
                        SINGLE_WINDOW_PER_UNIT_COST,
                        SINGLE_WINDOW_BUDGET,
                        List.of(response("[2]", 1, 1)));

        assertThat(result.chunks()).hasSize(2);
        assertThat(result.chunks().get(0).units()).containsExactly(first, second);
        assertThat(result.chunks().get(1).units()).containsExactly(third);
        // Same instance, provenance and metadata unchanged.
        DocumentUnit chunkedThird = result.chunks().get(1).units().get(0);
        assertThat(chunkedThird).isSameAs(third);
        assertThat(chunkedThird.provenance()).isEqualTo(new Provenance(2, 3, 21, 30));
        assertThat(chunkedThird.metadata()).isEqualTo(Map.of("k", "v2"));
    }

    // ---- determinism (all specs §ondeterminism) ----

    @Test
    void identicalInputsProduceIdenticalResults() {
        List<ModelResponse> script =
                List.of(
                        response("[2]", 4, 2),
                        response("bad", 1, 1),
                        response("bad", 1, 1),
                        response("bad", 1, 1),
                        response("[6]", 2, 3));

        ChunkingResult first =
                chunk(units(8), MULTI_WINDOW_PER_UNIT_COST, MULTI_WINDOW_BUDGET, script);
        ChunkingResult second =
                chunk(units(8), MULTI_WINDOW_PER_UNIT_COST, MULTI_WINDOW_BUDGET, script);

        assertThat(second).isEqualTo(first);
    }

    // ---- extraction wiring and the DocumentSource entry point ----

    @Test
    void extractsNormalizesThenChunksARawDocumentSource() {
        List<DocumentUnit> units = units(4);
        DocumentSource source =
                DocumentSource.of("raw".getBytes(StandardCharsets.UTF_8), "text/plain");
        DocumentExtractor extractor = mock(DocumentExtractor.class);
        when(extractor.extract(source)).thenReturn(new PreparedDocument(units, Map.of()));
        ScriptedModel model =
                new ScriptedModel(
                        SINGLE_WINDOW_BUDGET,
                        estimator(units, SINGLE_WINDOW_PER_UNIT_COST),
                        List.of(response("[2]", 1, 1)));

        ChunkingResult result =
                SemanticChunker.builder()
                        .documentExtractor(extractor)
                        .chunkingModel(model)
                        .build()
                        .chunk(source);

        assertThat(chunkOrdinals(result)).containsExactly(List.of(0, 1), List.of(2, 3));
    }

    // ---- terminal failures (RECOVERY_POLICY §8) ----

    @Test
    void anExtractionFailurePropagatesAndProducesNoResult() {
        DocumentSource source =
                DocumentSource.of("raw".getBytes(StandardCharsets.UTF_8), "text/plain");
        DocumentExtractor extractor = mock(DocumentExtractor.class);
        when(extractor.extract(any())).thenThrow(new ExtractionException("cannot parse"));
        ChunkingModel model = new ScriptedModel(SINGLE_WINDOW_BUDGET, text -> 0, List.of());

        SemanticChunker chunker =
                SemanticChunker.builder().documentExtractor(extractor).chunkingModel(model).build();

        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> chunker.chunk(source))
                .withMessage("cannot parse");
    }

    @Test
    void aModelExecutionFailureIsTerminalAndPropagates() {
        List<DocumentUnit> units = units(4);
        ChunkingModel model =
                new FailingModel(
                        SINGLE_WINDOW_BUDGET, estimator(units, SINGLE_WINDOW_PER_UNIT_COST));

        SemanticChunker chunker = chunkerWith(model);

        assertThatExceptionOfType(ModelException.class)
                .isThrownBy(() -> chunker.chunk(new PreparedDocument(units, Map.of())))
                .withMessage("model unreachable");
    }

    @Test
    void aNonPositiveBudgetIsATerminalPlanningFailure() {
        List<DocumentUnit> units = units(2);
        ChunkingModel model =
                new ScriptedModel(0, estimator(units, SINGLE_WINDOW_PER_UNIT_COST), List.of());

        SemanticChunker chunker = chunkerWith(model);

        assertThatExceptionOfType(ChunkingException.class)
                .isThrownBy(() -> chunker.chunk(new PreparedDocument(units, Map.of())));
    }

    // ---- the Path overload remains an intentional, specification-bound stub ----

    @Test
    void chunkingFromABarePathIsNotSupportedBecauseNoMediaTypePolicyIsSpecified() {
        ChunkingModel model = new ScriptedModel(SINGLE_WINDOW_BUDGET, text -> 0, List.of());
        SemanticChunker chunker = chunkerWith(model);

        assertThatThrownBy(() -> chunker.chunk(java.nio.file.Path.of("doc.txt")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- fake models ----

    /** A model that answers execute() from a fixed, call-ordered script. */
    private static final class ScriptedModel implements ChunkingModel {
        private final int maxInputTokens;
        private final ToIntFunction<String> estimator;
        private final List<ModelResponse> responses;
        private int calls = 0;

        ScriptedModel(
                int maxInputTokens,
                ToIntFunction<String> estimator,
                List<ModelResponse> responses) {
            this.maxInputTokens = maxInputTokens;
            this.estimator = estimator;
            this.responses = responses;
        }

        @Override
        public ModelResponse execute(ChunkingRequest request) {
            if (calls >= responses.size()) {
                throw new AssertionError("unexpected model call #" + (calls + 1));
            }
            return responses.get(calls++);
        }

        @Override
        public int maxInputTokens() {
            return maxInputTokens;
        }

        @Override
        public int estimateTokens(String text) {
            return estimator.applyAsInt(text);
        }

        int callCount() {
            return calls;
        }
    }

    /** A model that cannot return a response — a terminal model execution failure. */
    private static final class FailingModel implements ChunkingModel {
        private final int maxInputTokens;
        private final ToIntFunction<String> estimator;

        FailingModel(int maxInputTokens, ToIntFunction<String> estimator) {
            this.maxInputTokens = maxInputTokens;
            this.estimator = estimator;
        }

        @Override
        public ModelResponse execute(ChunkingRequest request) {
            throw new ModelException("model unreachable");
        }

        @Override
        public int maxInputTokens() {
            return maxInputTokens;
        }

        @Override
        public int estimateTokens(String text) {
            return estimator.applyAsInt(text);
        }
    }
}
