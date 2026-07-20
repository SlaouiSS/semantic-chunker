package io.github.semanticchunker.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ChunkingRequestTest {

    @Test
    void exposesPromptAndGenerationHints() {
        ChunkingRequest request = new ChunkingRequest("prompt", "json-schema", 0.0d, 512);

        assertThat(request.prompt()).isEqualTo("prompt");
        assertThat(request.temperature()).isZero();
        assertThat(request.maxOutputTokens()).isEqualTo(512);
        assertThat(request.responseShape()).contains("json-schema");
    }

    @Test
    void hasNoResponseShapeWhenHintIsAbsent() {
        ChunkingRequest request = new ChunkingRequest("prompt", null, 0.1d, 256);

        assertThat(request.responseShape()).isEmpty();
    }

    @Test
    void rejectsNullPrompt() {
        assertThatNullPointerException().isThrownBy(() -> new ChunkingRequest(null, null, 0.0d, 1));
    }

    @Test
    void rejectsNegativeTemperature() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChunkingRequest("p", null, -0.1d, 1))
                .withMessageContaining("temperature");
    }

    @Test
    void rejectsNonPositiveOutputBudget() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChunkingRequest("p", null, 0.0d, 0))
                .withMessageContaining("maxOutputTokens");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChunkingRequest("p", null, 0.0d, -1))
                .withMessageContaining("maxOutputTokens");
    }

    @Test
    void hasValueEquality() {
        ChunkingRequest one = new ChunkingRequest("p", "s", 0.2d, 128);
        ChunkingRequest same = new ChunkingRequest("p", "s", 0.2d, 128);

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    }
}
