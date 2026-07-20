package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.semanticchunker.model.ChunkingModel;
import io.github.semanticchunker.model.ChunkingRequest;
import io.github.semanticchunker.model.ModelResponse;
import io.github.semanticchunker.model.TokenUsage;
import org.junit.jupiter.api.Test;

class ModelExecutorTest {

    @Test
    void delegatesExecutionToTheModel() {
        ChunkingModel model = mock(ChunkingModel.class);
        ChunkingRequest request = new ChunkingRequest("prompt", null, 0.0d, 8);
        ModelResponse response = new ModelResponse("raw", new TokenUsage(1, 1));
        when(model.execute(request)).thenReturn(response);

        ModelExecutor executor = new ModelExecutor(model);

        assertThat(executor.execute(request)).isSameAs(response);
        verify(model).execute(request);
    }

    @Test
    void rejectsNullModel() {
        assertThatNullPointerException().isThrownBy(() -> new ModelExecutor(null));
    }
}
