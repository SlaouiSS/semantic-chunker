package io.github.semanticchunker.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ModelResponseTest {

    @Test
    void exposesRawTextAndTokenUsage() {
        TokenUsage usage = new TokenUsage(10, 5);

        ModelResponse response = new ModelResponse("raw", usage);

        assertThat(response.rawText()).isEqualTo("raw");
        assertThat(response.tokenUsage()).isEqualTo(usage);
    }

    @Test
    void rejectsNullRawText() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ModelResponse(null, new TokenUsage(0, 0)));
    }

    @Test
    void rejectsNullTokenUsage() {
        assertThatNullPointerException().isThrownBy(() -> new ModelResponse("raw", null));
    }

    @Test
    void hasValueEquality() {
        ModelResponse one = new ModelResponse("raw", new TokenUsage(1, 1));
        ModelResponse same = new ModelResponse("raw", new TokenUsage(1, 1));

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    }
}
