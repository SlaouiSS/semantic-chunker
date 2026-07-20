package io.github.semanticchunker.chunker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ProcessingInfoTest {

    @Test
    void exposesWindowCounts() {
        ProcessingInfo info = new ProcessingInfo(5, 2);

        assertThat(info.windowsProcessed()).isEqualTo(5);
        assertThat(info.windowsDegraded()).isEqualTo(2);
    }

    @Test
    void allowsAllWindowsCleanOrAllDegraded() {
        assertThat(new ProcessingInfo(3, 0).windowsDegraded()).isZero();
        assertThat(new ProcessingInfo(3, 3).windowsDegraded()).isEqualTo(3);
    }

    @Test
    void rejectsNegativeWindowsProcessed() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProcessingInfo(-1, 0))
                .withMessageContaining("windowsProcessed");
    }

    @Test
    void rejectsNegativeWindowsDegraded() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProcessingInfo(0, -1))
                .withMessageContaining("windowsDegraded");
    }

    @Test
    void rejectsMoreDegradedThanProcessed() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProcessingInfo(2, 3))
                .withMessageContaining("exceed");
    }

    @Test
    void hasValueEquality() {
        assertThat(new ProcessingInfo(4, 1))
                .isEqualTo(new ProcessingInfo(4, 1))
                .isNotEqualTo(new ProcessingInfo(4, 0));
    }
}
