package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.semanticchunker.chunker.ChunkingResult;
import io.github.semanticchunker.chunker.ExecutionMetadata;
import io.github.semanticchunker.chunker.ProcessingInfo;
import io.github.semanticchunker.chunker.SemanticChunk;
import io.github.semanticchunker.document.DocumentSource;
import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.PreparedDocument;
import io.github.semanticchunker.document.Provenance;
import io.github.semanticchunker.extraction.DocumentExtractor;
import io.github.semanticchunker.extraction.ExtractionException;
import io.github.semanticchunker.model.ChunkingModel;
import io.github.semanticchunker.model.ChunkingRequest;
import io.github.semanticchunker.model.ModelResponse;
import io.github.semanticchunker.model.TokenUsage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

class ChunkingPipelineTest {

    private final DocumentExtractor extractor = mock(DocumentExtractor.class);
    private final WindowPlanner planner = mock(WindowPlanner.class);
    private final PromptRenderer renderer = mock(PromptRenderer.class);
    private final ModelExecutor executor = mock(ModelExecutor.class);
    private final ResponseValidator validator = mock(ResponseValidator.class);
    private final BoundaryMerger merger = mock(BoundaryMerger.class);
    private final ChunkAssembler chunkAssembler = mock(ChunkAssembler.class);
    private final ResultAssembler resultAssembler = mock(ResultAssembler.class);

    private static DocumentUnit paragraph(int ordinal) {
        return new Paragraph(
                new Provenance(ordinal, 1, ordinal, ordinal + 1), "u" + ordinal, Map.of());
    }

    private static Window window(int firstOrdinal, int secondOrdinal) {
        return new Window(List.of(paragraph(firstOrdinal), paragraph(secondOrdinal)));
    }

    private static PreparedDocument document() {
        return new PreparedDocument(List.of(paragraph(0)), Map.of());
    }

    private static ChunkingResult anyResult() {
        return new ChunkingResult(
                List.of(),
                List.of(),
                new ProcessingInfo(0, 0),
                new ExecutionMetadata(new TokenUsage(0, 0)));
    }

    private ChunkingPipeline pipelineWith(ResultAssembler assembler) {
        return new ChunkingPipeline(
                extractor,
                planner,
                renderer,
                executor,
                validator,
                merger,
                chunkAssembler,
                assembler);
    }

    // ---- wiring / construction ----

    @Test
    void createWiresANonNullPipeline() {
        assertThat(ChunkingPipeline.create(extractor, mock(ChunkingModel.class))).isNotNull();
    }

    @Test
    void createRejectsNullExtractor() {
        assertThatNullPointerException()
                .isThrownBy(() -> ChunkingPipeline.create(null, mock(ChunkingModel.class)));
    }

    @Test
    void createRejectsNullModel() {
        assertThatNullPointerException().isThrownBy(() -> ChunkingPipeline.create(extractor, null));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void constructorRejectsAnyNullCollaborator(int nullIndex) {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new ChunkingPipeline(
                                        nullIndex == 0 ? null : extractor,
                                        nullIndex == 1 ? null : planner,
                                        nullIndex == 2 ? null : renderer,
                                        nullIndex == 3 ? null : executor,
                                        nullIndex == 4 ? null : validator,
                                        nullIndex == 5 ? null : merger,
                                        nullIndex == 6 ? null : chunkAssembler,
                                        nullIndex == 7 ? null : resultAssembler));
    }

    // ---- orchestration order and data flow ----

    @Test
    void runsTheStagesInTheFixedOrderThreadingEachOutputToTheNextStage() {
        PreparedDocument document = document();
        Window window = window(0, 1);
        ChunkingRequest request = new ChunkingRequest("prompt", null, 0.0d, 4);
        ModelResponse response = new ModelResponse("raw", new TokenUsage(3, 2));
        ValidatedBoundaryDecision decision = new ValidatedBoundaryDecision(List.of(0));
        MergedBoundaries merged = new MergedBoundaries(List.of(0));
        SemanticChunk chunk = new SemanticChunk(List.of(paragraph(0)));
        ChunkingResult result = anyResult();

        when(planner.plan(document)).thenReturn(List.of(window));
        when(renderer.render(window)).thenReturn(request);
        when(executor.execute(request)).thenReturn(response);
        when(validator.validate(response, window))
                .thenReturn(new ValidationOutcome.Accepted(decision));
        when(merger.merge(List.of(new WindowDecision(window, decision)))).thenReturn(merged);
        when(chunkAssembler.assemble(merged, document)).thenReturn(List.of(chunk));
        when(resultAssembler.assemble(
                        List.of(chunk),
                        List.of(),
                        new ProcessingInfo(1, 0),
                        new ExecutionMetadata(new TokenUsage(3, 2))))
                .thenReturn(result);

        ChunkingResult out = pipelineWith(resultAssembler).chunk(document);

        assertThat(out).isSameAs(result);
        InOrder order =
                inOrder(
                        planner,
                        renderer,
                        executor,
                        validator,
                        merger,
                        chunkAssembler,
                        resultAssembler);
        order.verify(planner).plan(document);
        order.verify(renderer).render(window);
        order.verify(executor).execute(request);
        order.verify(validator).validate(response, window);
        order.verify(merger).merge(List.of(new WindowDecision(window, decision)));
        order.verify(chunkAssembler).assemble(merged, document);
        order.verify(resultAssembler)
                .assemble(
                        List.of(chunk),
                        List.of(),
                        new ProcessingInfo(1, 0),
                        new ExecutionMetadata(new TokenUsage(3, 2)));
    }

    @Test
    void extractsAndNormalizesBeforePlanningForARawDocument() {
        DocumentSource source =
                DocumentSource.of("hi".getBytes(StandardCharsets.UTF_8), "text/plain");
        PreparedDocument document = document();
        Window window = window(0, 1);

        when(extractor.extract(source)).thenReturn(document);
        when(planner.plan(document)).thenReturn(List.of(window));
        when(renderer.render(any())).thenReturn(new ChunkingRequest("p", null, 0.0d, 1));
        when(executor.execute(any())).thenReturn(new ModelResponse("r", new TokenUsage(0, 0)));
        when(validator.validate(any(), any()))
                .thenReturn(
                        new ValidationOutcome.Accepted(new ValidatedBoundaryDecision(List.of())));
        when(merger.merge(any())).thenReturn(new MergedBoundaries(List.of()));
        when(chunkAssembler.assemble(any(), any())).thenReturn(List.of());

        pipelineWith(new ResultAssembler()).chunk(source);

        InOrder order = inOrder(extractor, planner);
        order.verify(extractor).extract(source);
        order.verify(planner).plan(document);
    }

    @Test
    void aggregatesWindowCountAndTokenUsageAcrossWindows() {
        PreparedDocument document = document();
        Window firstWindow = window(0, 1);
        Window secondWindow = window(2, 3);
        ChunkingRequest firstRequest = new ChunkingRequest("a", null, 0.0d, 1);
        ChunkingRequest secondRequest = new ChunkingRequest("b", null, 0.0d, 1);
        SemanticChunk chunk = new SemanticChunk(List.of(paragraph(0)));

        when(planner.plan(document)).thenReturn(List.of(firstWindow, secondWindow));
        when(renderer.render(firstWindow)).thenReturn(firstRequest);
        when(renderer.render(secondWindow)).thenReturn(secondRequest);
        when(executor.execute(firstRequest))
                .thenReturn(new ModelResponse("x", new TokenUsage(10, 4)));
        when(executor.execute(secondRequest))
                .thenReturn(new ModelResponse("y", new TokenUsage(5, 1)));
        when(validator.validate(any(), any()))
                .thenReturn(
                        new ValidationOutcome.Accepted(new ValidatedBoundaryDecision(List.of())));
        when(merger.merge(any())).thenReturn(new MergedBoundaries(List.of()));
        when(chunkAssembler.assemble(any(), any())).thenReturn(List.of(chunk));

        ChunkingResult out = pipelineWith(new ResultAssembler()).chunk(document);

        assertThat(out.processingInfo()).isEqualTo(new ProcessingInfo(2, 0));
        assertThat(out.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(15, 5));
        assertThat(out.warnings()).isEmpty();
        assertThat(out.chunks()).containsExactly(chunk);
    }

    @Test
    void doesNotCallThePerWindowStagesWhenThereAreNoWindows() {
        PreparedDocument document = document();

        when(planner.plan(document)).thenReturn(List.of());
        when(merger.merge(List.of())).thenReturn(new MergedBoundaries(List.of()));
        when(chunkAssembler.assemble(any(), any())).thenReturn(List.of());

        ChunkingResult out = pipelineWith(new ResultAssembler()).chunk(document);

        assertThat(out.processingInfo()).isEqualTo(new ProcessingInfo(0, 0));
        assertThat(out.executionMetadata().totalTokenUsage()).isEqualTo(new TokenUsage(0, 0));
        verifyNoInteractions(renderer, executor, validator);
    }

    // ---- error propagation ----

    @Test
    void propagatesAnExtractionFailureWithoutRunningLaterStages() {
        DocumentSource source =
                DocumentSource.of("x".getBytes(StandardCharsets.UTF_8), "text/plain");
        when(extractor.extract(source)).thenThrow(new ExtractionException("cannot parse"));

        assertThatThrownBy(() -> pipelineWith(resultAssembler).chunk(source))
                .isInstanceOf(ExtractionException.class)
                .hasMessage("cannot parse");

        verifyNoInteractions(
                planner, renderer, executor, validator, merger, chunkAssembler, resultAssembler);
    }
}
