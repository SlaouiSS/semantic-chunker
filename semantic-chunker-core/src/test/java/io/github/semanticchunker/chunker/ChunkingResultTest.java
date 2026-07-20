package io.github.semanticchunker.chunker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.Provenance;
import io.github.semanticchunker.model.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChunkingResultTest {

    private static SemanticChunk chunk(int ordinal) {
        return new SemanticChunk(
                List.of(
                        new Paragraph(
                                new Provenance(ordinal, 1, ordinal, ordinal + 1), "u", Map.of())));
    }

    private static ProcessingInfo processingInfo() {
        return new ProcessingInfo(1, 0);
    }

    private static ExecutionMetadata executionMetadata() {
        return new ExecutionMetadata(new TokenUsage(10, 5));
    }

    @Test
    void exposesChunksWarningsProcessingInfoAndExecutionMetadata() {
        List<SemanticChunk> chunks = List.of(chunk(0));
        List<Warning> warnings = List.of(new Warning("noted"));

        ChunkingResult result =
                new ChunkingResult(chunks, warnings, processingInfo(), executionMetadata());

        assertThat(result.chunks()).containsExactlyElementsOf(chunks);
        assertThat(result.warnings()).containsExactlyElementsOf(warnings);
        assertThat(result.processingInfo()).isEqualTo(processingInfo());
        assertThat(result.executionMetadata()).isEqualTo(executionMetadata());
    }

    @Test
    void allowsNoChunksAndNoWarnings() {
        ChunkingResult result =
                new ChunkingResult(List.of(), List.of(), processingInfo(), executionMetadata());

        assertThat(result.chunks()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void defensivelyCopiesChunksAndWarningsAndExposesThemUnmodifiable() {
        List<SemanticChunk> chunks = new ArrayList<>(List.of(chunk(0)));
        List<Warning> warnings = new ArrayList<>(List.of(new Warning("one")));

        ChunkingResult result =
                new ChunkingResult(chunks, warnings, processingInfo(), executionMetadata());
        chunks.add(chunk(1));
        warnings.add(new Warning("two"));

        assertThat(result.chunks()).hasSize(1);
        assertThat(result.warnings()).hasSize(1);
        assertThatThrownBy(() -> result.chunks().add(chunk(9)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.warnings().add(new Warning("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullChunks() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new ChunkingResult(
                                        null, List.of(), processingInfo(), executionMetadata()));
    }

    @Test
    void rejectsNullWarnings() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new ChunkingResult(
                                        List.of(), null, processingInfo(), executionMetadata()));
    }

    @Test
    void rejectsNullProcessingInfo() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> new ChunkingResult(List.of(), List.of(), null, executionMetadata()));
    }

    @Test
    void rejectsNullExecutionMetadata() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ChunkingResult(List.of(), List.of(), processingInfo(), null));
    }

    @Test
    void hasValueEquality() {
        ChunkingResult one =
                new ChunkingResult(
                        List.of(chunk(0)), List.of(), processingInfo(), executionMetadata());
        ChunkingResult same =
                new ChunkingResult(
                        List.of(chunk(0)), List.of(), processingInfo(), executionMetadata());

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    }
}
