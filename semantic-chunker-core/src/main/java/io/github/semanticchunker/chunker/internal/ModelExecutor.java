package io.github.semanticchunker.chunker.internal;

import io.github.semanticchunker.model.ChunkingModel;
import io.github.semanticchunker.model.ChunkingRequest;
import io.github.semanticchunker.model.ModelResponse;
import java.util.Objects;

/**
 * Sends a request to the language model and returns its raw response, through the core's model
 * abstraction (ARCHITECTURE.md, section 4, "Model interaction"; PIPELINE.md, section 3, "LLM
 * Execution").
 *
 * <p>This stage's whole responsibility is to obtain the model's answer for a given request; it
 * holds no provider knowledge and makes no judgment about the answer. It is the pipeline's second
 * external touchpoint and the sole location of non-deterministic behavior. Detecting a bad response
 * and deciding whether to retry belong to validation and the recovery policy, not here.
 */
class ModelExecutor {

    private final ChunkingModel model;

    ModelExecutor(ChunkingModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Executes the request against the model.
     *
     * @param request the request to execute; never {@code null}
     * @return the model's raw response
     */
    ModelResponse execute(ChunkingRequest request) {
        return model.execute(request);
    }
}
