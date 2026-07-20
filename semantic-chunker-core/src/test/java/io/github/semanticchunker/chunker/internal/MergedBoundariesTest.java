package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MergedBoundariesTest {

    @Test
    void namesBoundaryOrdinalsOverTheWholeDocument() {
        assertThat(new MergedBoundaries(List.of(0, 5, 12)).boundaryOrdinals())
                .containsExactly(0, 5, 12);
    }

    @Test
    void allowsNoBoundaries() {
        assertThat(new MergedBoundaries(List.of()).boundaryOrdinals()).isEmpty();
    }

    @Test
    void defensivelyCopiesAndExposesUnmodifiable() {
        List<Integer> ordinals = new ArrayList<>(List.of(3));

        MergedBoundaries merged = new MergedBoundaries(ordinals);
        ordinals.add(4);

        assertThat(merged.boundaryOrdinals()).containsExactly(3);
        assertThatThrownBy(() -> merged.boundaryOrdinals().add(9))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullList() {
        assertThatNullPointerException().isThrownBy(() -> new MergedBoundaries(null));
    }

    @Test
    void rejectsNegativeOrdinal() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MergedBoundaries(List.of(-2)));
    }

    @Test
    void hasValueEquality() {
        MergedBoundaries one = new MergedBoundaries(List.of(1, 6));
        MergedBoundaries same = new MergedBoundaries(List.of(1, 6));

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    }
}
