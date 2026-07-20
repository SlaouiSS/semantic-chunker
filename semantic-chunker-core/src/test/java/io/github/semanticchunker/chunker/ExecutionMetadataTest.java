package io.github.semanticchunker.chunker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.github.semanticchunker.model.TokenUsage;
import org.junit.jupiter.api.Test;

class ExecutionMetadataTest {

    @Test
    void exposesTotalTokenUsage() {
        TokenUsage usage = new TokenUsage(1000, 250);

        assertThat(new ExecutionMetadata(usage).totalTokenUsage()).isEqualTo(usage);
    }

    @Test
    void rejectsNullTokenUsage() {
        assertThatNullPointerException().isThrownBy(() -> new ExecutionMetadata(null));
    }

    @Test
    void hasValueEquality() {
        ExecutionMetadata one = new ExecutionMetadata(new TokenUsage(1, 2));
        ExecutionMetadata same = new ExecutionMetadata(new TokenUsage(1, 2));

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    }
}
