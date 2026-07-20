package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.semanticchunker.chunker.ChunkingException;
import io.github.semanticchunker.chunker.ChunkingResult;
import io.github.semanticchunker.chunker.Warning;
import io.github.semanticchunker.document.DocumentSource;
import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.PreparedDocument;
import io.github.semanticchunker.document.Provenance;
import io.github.semanticchunker.extraction.DocumentExtractor;
import io.github.semanticchunker.extraction.ExtractionException;
import io.github.semanticchunker.model.ChunkingRequest;
import io.github.semanticchunker.model.ModelException;
import io.github.semanticchunker.model.ModelResponse;
import io.github.semanticchunker.model.TokenUsage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Behavioral tests for the recovery orchestration of the pipeline (RECOVERY_POLICY.md). */
class ChunkingPipelineRecoveryTest {

    private static final int ATTEMPT_MAX = 3;

    private final DocumentExtractor extractor = mock(DocumentExtractor.class);
    private final WindowPlanner planner = mock(WindowPlanner.class);
    private final PromptRenderer renderer = mock(PromptRenderer.class);
    private final ModelExecutor executor = mock(ModelExecutor.class);
    private final ResponseValidator validator = mock(ResponseValidator.class);
    private final BoundaryMerger merger = mock(BoundaryMerger.class);
    private final ChunkAssembler chunkAssembler = mock(ChunkAssembler.class);

    private final ChunkingPipeline pipeline =
            new ChunkingPipeline(
                    extractor,
                    planner,
                    renderer,
                    executor,
                    validator,
                    merger,
                    chunkAssembler,
                    new ResultAssembler());

    private static DocumentUnit paragraph(int ordinal) {
        return new Paragraph(
                new Provenance(ordinal, 1, ordinal, ordinal + 1), "u" + ordinal, Map.of());
    }

    /** An admissible window presenting the given units (at least two, so it admits a cut). */
    private static Window window(int... ordinals) {
        return new Window(paragraphs(ordinals), false, false);
    }

    /** An over-budget window: a single oversized unit that is never sent to the model. */
    private static Window overBudgetWindow(int ordinal) {
        return new Window(List.of(paragraph(ordinal)), true, false);
    }

    /** A single-unit admissible window: too small to admit a cut. */
    private static Window singleUnitWindow(int ordinal) {
        return new Window(List.of(paragraph(ordinal)), false, false);
    }

    private static List<DocumentUnit> paragraphs(int... ordinals) {
        List<DocumentUnit> units = new java.util.ArrayList<>();
        for (int ordinal : ordinals) {
            units.add(paragraph(ordinal));
        }
        return units;
    }

    private static PreparedDocument document() {
        return new PreparedDocument(List.of(paragraph(0)), Map.of());
    }

    private static ModelResponse response(int inputTokens, int outputTokens) {
        return new ModelResponse("raw", new TokenUsage(inputTokens, outputTokens));
    }

    private static ValidationOutcome accepted() {
        return new ValidationOutcome.Accepted(new ValidatedBoundaryDecision(List.of()));
    }

    private static ValidationOutcome rejected() {
        return new ValidationOutcome.Rejected(ValidationOutcome.RejectionReason.MALFORMED);
    }

    /** Stubs the downstream stages that this phase does not exercise, so a result can assemble. */
    private void stubDownstreamStages() {
        when(renderer.render(any())).thenReturn(new ChunkingRequest("p", null, 0.0d, 1));
        when(merger.merge(any())).thenReturn(new MergedBoundaries(List.of()));
        when(chunkAssembler.assemble(any(), any())).thenReturn(List.of());
    }

    // ---- accepted response (§1, §9) ----

    @Test
    void anAcceptedResponseProducesNoWarningAndOneCleanWindow() {
        stubDownstreamStages();
        when(planner.plan(any())).thenReturn(List.of(window(0, 1)));
        when(executor.execute(any())).thenReturn(response(3, 2));
        when(validator.validate(any(), any())).thenReturn(accepted());

        ChunkingResult result = pipeline.chunk(document());

        assertThat(result.warnings()).isEmpty();
        assertThat(result.processingInfo().windowsProcessed()).isEqualTo(1);
        assertThat(result.processingInfo().windowsDegraded()).isZero();
        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(3, 2));
        verify(executor, times(1)).execute(any());
    }

    // ---- retry succeeds (§3, §4, §9) ----

    @Test
    void aRetryThatSucceedsProducesNoWarning() {
        stubDownstreamStages();
        when(planner.plan(any())).thenReturn(List.of(window(0, 1)));
        when(executor.execute(any())).thenReturn(response(3, 2), response(4, 1));
        when(validator.validate(any(), any())).thenReturn(rejected(), accepted());

        ChunkingResult result = pipeline.chunk(document());

        assertThat(result.warnings()).isEmpty();
        assertThat(result.processingInfo().windowsDegraded()).isZero();
        // Both attempts contributed tokens, including the rejected first attempt (§11).
        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(7, 3));
        verify(executor, times(2)).execute(any());
    }

    @Test
    void aRetryThatSucceedsOnTheFinalAllowedAttemptProducesNoWarning() {
        stubDownstreamStages();
        when(planner.plan(any())).thenReturn(List.of(window(0, 1)));
        when(executor.execute(any())).thenReturn(response(1, 1), response(1, 1), response(1, 1));
        when(validator.validate(any(), any())).thenReturn(rejected(), rejected(), accepted());

        ChunkingResult result = pipeline.chunk(document());

        assertThat(result.warnings()).isEmpty();
        assertThat(result.processingInfo().windowsDegraded()).isZero();
        verify(executor, times(ATTEMPT_MAX)).execute(any());
    }

    // ---- retry exhausted (§4, §6, §9) ----

    @Test
    void threeRejectedAttemptsExhaustRetryAndDegradeTheWindow() {
        stubDownstreamStages();
        when(planner.plan(any())).thenReturn(List.of(window(0, 1)));
        when(executor.execute(any())).thenReturn(response(2, 2), response(3, 3), response(4, 4));
        when(validator.validate(any(), any())).thenReturn(rejected());

        ChunkingResult result = pipeline.chunk(document());

        assertThat(result.warnings()).hasSize(1);
        assertThat(result.processingInfo())
                .extracting("windowsProcessed", "windowsDegraded")
                .containsExactly(1, 1);
        // Every rejected attempt's tokens are counted (§11).
        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(9, 9));
    }

    @Test
    void aWindowIsNeverAttemptedMoreThanAttemptMaxTimes() {
        stubDownstreamStages();
        when(planner.plan(any())).thenReturn(List.of(window(0, 1)));
        when(executor.execute(any())).thenReturn(response(1, 1));
        when(validator.validate(any(), any())).thenReturn(rejected());

        pipeline.chunk(document());

        verify(executor, times(ATTEMPT_MAX)).execute(any());
        verify(validator, times(ATTEMPT_MAX)).validate(any(), any());
    }

    // ---- over-budget degradation (§6) ----

    @Test
    void anOverBudgetWindowIsDegradedWithoutAnyModelCall() {
        stubDownstreamStages();
        when(planner.plan(any())).thenReturn(List.of(overBudgetWindow(5)));

        ChunkingResult result = pipeline.chunk(document());

        assertThat(result.warnings()).hasSize(1);
        assertThat(result.processingInfo())
                .extracting("windowsProcessed", "windowsDegraded")
                .containsExactly(1, 1);
        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(0, 0));
        verifyNoInteractions(executor);
        verify(renderer, never()).render(any());
    }

    // ---- single-unit shortcut (§7) ----

    @Test
    void aSingleUnitWindowIsResolvedLocallyWithoutModelCallRetryOrWarning() {
        stubDownstreamStages();
        when(planner.plan(any())).thenReturn(List.of(singleUnitWindow(9)));

        ChunkingResult result = pipeline.chunk(document());

        assertThat(result.warnings()).isEmpty();
        // Processed but NOT degraded (§10).
        assertThat(result.processingInfo())
                .extracting("windowsProcessed", "windowsDegraded")
                .containsExactly(1, 0);
        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(0, 0));
        verifyNoInteractions(executor);
        verify(renderer, never()).render(any());
    }

    // ---- degraded windows still participate in merging (§7, BOUNDARY_MERGING §7) ----

    @Test
    @SuppressWarnings("unchecked")
    void everyWindowContributesADecisionToMergingIncludingDegradedOnes() {
        stubDownstreamStages();
        when(planner.plan(any()))
                .thenReturn(List.of(overBudgetWindow(0), window(1, 2), singleUnitWindow(3)));
        when(executor.execute(any())).thenReturn(response(1, 1));
        when(validator.validate(any(), any())).thenReturn(rejected());

        pipeline.chunk(document());

        ArgumentCaptor<List<WindowDecision>> captor = ArgumentCaptor.forClass(List.class);
        verify(merger).merge(captor.capture());
        List<WindowDecision> decisions = captor.getValue();
        // All three windows contribute a decision; the degraded ones contribute the empty decision.
        assertThat(decisions).hasSize(3);
        assertThat(decisions)
                .allSatisfy(
                        decision -> assertThat(decision.decision().boundaryOrdinals()).isEmpty());
    }

    // ---- warning identification and category (§9) ----

    @Test
    void anOverBudgetWarningIdentifiesTheWindowSpanAndCategory() {
        stubDownstreamStages();
        when(planner.plan(any())).thenReturn(List.of(overBudgetWindow(5)));

        ChunkingResult result = pipeline.chunk(document());

        String message = result.warnings().get(0).message();
        assertThat(message).contains("5-5").contains("over-budget");
    }

    @Test
    void aRetryExhaustedWarningIdentifiesTheWindowSpanAndCategory() {
        stubDownstreamStages();
        when(planner.plan(any())).thenReturn(List.of(window(3, 4, 5, 6, 7)));
        when(executor.execute(any())).thenReturn(response(1, 1));
        when(validator.validate(any(), any())).thenReturn(rejected());

        ChunkingResult result = pipeline.chunk(document());

        String message = result.warnings().get(0).message();
        assertThat(message).contains("3-7").contains("retry exhausted");
    }

    // ---- warning ordering (§9) ----

    @Test
    void warningsAreRecordedInWindowOrder() {
        stubDownstreamStages();
        when(planner.plan(any()))
                .thenReturn(List.of(window(0, 1), overBudgetWindow(2), window(3, 4)));
        when(executor.execute(any())).thenReturn(response(1, 1));
        when(validator.validate(any(), any())).thenReturn(rejected());

        ChunkingResult result = pipeline.chunk(document());

        assertThat(result.warnings())
                .extracting(Warning::message)
                .hasSize(3)
                .satisfies(messages -> assertThat(messages.get(0)).contains("0-1", "retry"))
                .satisfies(messages -> assertThat(messages.get(1)).contains("2-2", "over-budget"))
                .satisfies(messages -> assertThat(messages.get(2)).contains("3-4", "retry"));
    }

    // ---- processing information (§10) ----

    @Test
    void processingInfoCountsEveryPlannedWindowAndOnlyDegradedOnes() {
        stubDownstreamStages();
        when(planner.plan(any()))
                .thenReturn(
                        List.of(
                                window(0, 1), // accepted
                                overBudgetWindow(2), // degraded
                                window(3, 4), // retry-exhausted, degraded
                                singleUnitWindow(5))); // resolved locally, not degraded
        when(executor.execute(any())).thenReturn(response(1, 1));
        when(validator.validate(any(), any()))
                .thenReturn(accepted(), rejected(), rejected(), rejected());

        ChunkingResult result = pipeline.chunk(document());

        assertThat(result.processingInfo())
                .extracting("windowsProcessed", "windowsDegraded")
                .containsExactly(4, 2);
        // windowsDegraded equals the number of warnings (§10).
        assertThat(result.warnings()).hasSize(2);
    }

    @Test
    void anEmptyDocumentIsProcessedAsZeroWindows() {
        stubDownstreamStages();
        when(planner.plan(any())).thenReturn(List.of());

        ChunkingResult result = pipeline.chunk(new PreparedDocument(List.of(), Map.of()));

        assertThat(result.processingInfo())
                .extracting("windowsProcessed", "windowsDegraded")
                .containsExactly(0, 0);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(0, 0));
        verifyNoInteractions(executor);
    }

    // ---- execution metadata / token accumulation (§11) ----

    @Test
    void tokenUsageSumsRejectedAndRetryResponses() {
        stubDownstreamStages();
        when(planner.plan(any())).thenReturn(List.of(window(0, 1)));
        when(executor.execute(any())).thenReturn(response(10, 4), response(5, 1), response(2, 3));
        when(validator.validate(any(), any())).thenReturn(rejected(), rejected(), accepted());

        ChunkingResult result = pipeline.chunk(document());

        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(17, 8));
    }

    @Test
    void overBudgetAndSingleUnitWindowsContributeNoTokens() {
        stubDownstreamStages();
        when(planner.plan(any()))
                .thenReturn(List.of(overBudgetWindow(0), window(1, 2), singleUnitWindow(3)));
        when(executor.execute(any())).thenReturn(response(5, 5));
        when(validator.validate(any(), any())).thenReturn(accepted());

        ChunkingResult result = pipeline.chunk(document());

        // Only the one accepted, model-executed window contributes tokens.
        assertThat(result.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(5, 5));
    }

    // ---- terminal failures (§8) ----

    @Test
    void aModelExecutionFailureIsTerminalAndPropagates() {
        when(planner.plan(any())).thenReturn(List.of(window(0, 1)));
        when(renderer.render(any())).thenReturn(new ChunkingRequest("p", null, 0.0d, 1));
        when(executor.execute(any())).thenThrow(new ModelException("model unreachable"));

        assertThatThrownBy(() -> pipeline.chunk(document()))
                .isInstanceOf(ModelException.class)
                .hasMessage("model unreachable");

        verifyNoInteractions(merger, chunkAssembler);
    }

    @Test
    void aModelExecutionFailureIsNotRetried() {
        when(planner.plan(any())).thenReturn(List.of(window(0, 1)));
        when(renderer.render(any())).thenReturn(new ChunkingRequest("p", null, 0.0d, 1));
        when(executor.execute(any())).thenThrow(new ModelException("model unreachable"));

        assertThatThrownBy(() -> pipeline.chunk(document())).isInstanceOf(ModelException.class);

        verify(executor, times(1)).execute(any());
        verifyNoInteractions(validator);
    }

    @Test
    void aPlanningFailureIsTerminalAndPropagates() {
        when(planner.plan(any())).thenThrow(new ChunkingException("no usable budget"));

        assertThatThrownBy(() -> pipeline.chunk(document()))
                .isInstanceOf(ChunkingException.class)
                .hasMessage("no usable budget");

        verifyNoInteractions(renderer, executor, validator, merger, chunkAssembler);
    }

    @Test
    void anExtractionFailureIsTerminalAndPropagates() {
        DocumentSource source =
                DocumentSource.of("x".getBytes(StandardCharsets.UTF_8), "text/plain");
        when(extractor.extract(source)).thenThrow(new ExtractionException("cannot parse"));

        assertThatThrownBy(() -> pipeline.chunk(source))
                .isInstanceOf(ExtractionException.class)
                .hasMessage("cannot parse");

        verifyNoInteractions(planner, renderer, executor, validator, merger, chunkAssembler);
    }

    @Test
    void anInternalInvariantViolationIsTerminalAndPropagates() {
        stubDownstreamStages();
        when(planner.plan(any())).thenReturn(List.of(window(0, 1)));
        when(executor.execute(any())).thenReturn(response(1, 1));
        when(validator.validate(any(), any())).thenReturn(accepted());
        when(merger.merge(any())).thenThrow(new IllegalStateException("broken invariant"));

        assertThatThrownBy(() -> pipeline.chunk(document()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("broken invariant");

        verifyNoInteractions(chunkAssembler);
    }

    // ---- determinism (§14) ----

    @Test
    void producesTheSameAccountForTheSameResponses() {
        stubDownstreamStages();
        when(planner.plan(any())).thenReturn(List.of(overBudgetWindow(5), window(0, 1)));
        when(executor.execute(any())).thenReturn(response(2, 2));
        when(validator.validate(any(), any())).thenReturn(accepted());

        ChunkingResult first = pipeline.chunk(document());
        ChunkingResult second = pipeline.chunk(document());

        assertThat(first.warnings()).isEqualTo(second.warnings());
        assertThat(first.processingInfo()).isEqualTo(second.processingInfo());
        assertThat(first.executionMetadata()).isEqualTo(second.executionMetadata());
    }
}
