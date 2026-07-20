package io.github.semanticchunker.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TokenUsageTest {

    @Test
    void exposesInputAndOutputTokens() {
        TokenUsage usage = new TokenUsage(120, 30);

        assertThat(usage.inputTokens()).isEqualTo(120);
        assertThat(usage.outputTokens()).isEqualTo(30);
    }

    @Test
    void allowsZeroCounts() {
        assertThat(new TokenUsage(0, 0).inputTokens()).isZero();
    }

    @Test
    void rejectsNegativeInputTokens() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TokenUsage(-1, 0))
                .withMessageContaining("inputTokens");
    }

    @Test
    void rejectsNegativeOutputTokens() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TokenUsage(0, -1))
                .withMessageContaining("outputTokens");
    }

    @Test
    void hasValueEquality() {
        assertThat(new TokenUsage(1, 2))
                .isEqualTo(new TokenUsage(1, 2))
                .isNotEqualTo(new TokenUsage(2, 1));
    }
}
