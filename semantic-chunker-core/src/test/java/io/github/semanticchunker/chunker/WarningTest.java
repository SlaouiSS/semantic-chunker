package io.github.semanticchunker.chunker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class WarningTest {

    @Test
    void exposesItsMessage() {
        assertThat(new Warning("window 3 degraded").message()).isEqualTo("window 3 degraded");
    }

    @Test
    void rejectsNullMessage() {
        assertThatNullPointerException().isThrownBy(() -> new Warning(null));
    }

    @Test
    void hasValueEquality() {
        assertThat(new Warning("m")).isEqualTo(new Warning("m")).isNotEqualTo(new Warning("n"));
    }
}
