package io.github.semanticchunker.chunker.internal;

import io.github.semanticchunker.chunker.ChunkingResult;
import io.github.semanticchunker.chunker.ExecutionMetadata;
import io.github.semanticchunker.chunker.ProcessingInfo;
import io.github.semanticchunker.chunker.SemanticChunk;
import io.github.semanticchunker.chunker.Warning;
import java.util.List;

/**
 * Gathers the finished chunks together with the account of how they were produced into the {@link
 * ChunkingResult} handed back to the caller (ARCHITECTURE.md, section 3, "Chunking result";
 * PIPELINE.md, section 3, "Result Assembly").
 *
 * <p>This stage performs no analysis; it composes the result from parts the orchestration has
 * already gathered. The {@link ChunkingResult} it produces validates and defensively copies those
 * parts.
 */
class ResultAssembler {

    ResultAssembler() {}

    /**
     * Assembles the final result.
     *
     * @param chunks the semantic chunks, in order; never {@code null}
     * @param warnings the warnings recorded during the run; never {@code null}
     * @param processingInfo the account of the work performed; never {@code null}
     * @param executionMetadata the record of what the run consumed; never {@code null}
     * @return the assembled result
     */
    ChunkingResult assemble(
            List<SemanticChunk> chunks,
            List<Warning> warnings,
            ProcessingInfo processingInfo,
            ExecutionMetadata executionMetadata) {
        return new ChunkingResult(chunks, warnings, processingInfo, executionMetadata);
    }
}
