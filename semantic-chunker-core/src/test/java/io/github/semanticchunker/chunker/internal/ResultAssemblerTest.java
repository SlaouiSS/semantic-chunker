package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.semanticchunker.chunker.ChunkingResult;
import io.github.semanticchunker.chunker.ExecutionMetadata;
import io.github.semanticchunker.chunker.ProcessingInfo;
import io.github.semanticchunker.chunker.SemanticChunk;
import io.github.semanticchunker.chunker.Warning;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.Provenance;
import io.github.semanticchunker.model.TokenUsage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResultAssemblerTest {

    @Test
    void composesTheResultFromItsParts() {
        ResultAssembler assembler = new ResultAssembler();
        SemanticChunk chunk =
                new SemanticChunk(
                        List.of(new Paragraph(new Provenance(0, 1, 0, 1), "u", Map.of())));
        Warning warning = new Warning("noted");
        ProcessingInfo processingInfo = new ProcessingInfo(1, 0);
        ExecutionMetadata executionMetadata = new ExecutionMetadata(new TokenUsage(2, 1));

        ChunkingResult result =
                assembler.assemble(
                        List.of(chunk), List.of(warning), processingInfo, executionMetadata);

        assertThat(result.chunks()).containsExactly(chunk);
        assertThat(result.warnings()).containsExactly(warning);
        assertThat(result.processingInfo()).isEqualTo(processingInfo);
        assertThat(result.executionMetadata()).isEqualTo(executionMetadata);
    }
}
