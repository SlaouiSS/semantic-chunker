package io.github.semanticchunker.chunker;

import java.util.List;
import java.util.Objects;

/**
 * The complete output of a run: the finished chunks together with an honest account of how they
 * were produced (MODEL.md, section 9; API.md, section 8).
 *
 * <p>The result is deliberately richer than a bare list of chunks. Because the library reaches
 * outside itself to a language model and can recover from localized difficulty, a caller needs to
 * know not only what was produced but how — which is why the result also carries warnings,
 * processing information, and execution metadata.
 *
 * @param chunks the semantic chunks, in document order; must not be {@code null}, defensively
 *     copied and exposed as an unmodifiable list
 * @param warnings notices of anomalies and recoveries during the run; must not be {@code null},
 *     defensively copied and exposed as an unmodifiable list
 * @param processingInfo an account of the work performed; must not be {@code null}
 * @param executionMetadata the record of what the run consumed; must not be {@code null}
 */
public record ChunkingResult(
        List<SemanticChunk> chunks,
        List<Warning> warnings,
        ProcessingInfo processingInfo,
        ExecutionMetadata executionMetadata) {

    /** Validates and defensively copies. */
    public ChunkingResult {
        chunks = List.copyOf(chunks);
        warnings = List.copyOf(warnings);
        Objects.requireNonNull(processingInfo, "processingInfo");
        Objects.requireNonNull(executionMetadata, "executionMetadata");
    }
}
