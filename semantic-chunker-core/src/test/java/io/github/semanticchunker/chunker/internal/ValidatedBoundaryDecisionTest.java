package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidatedBoundaryDecisionTest {

    @Test
    void namesBoundaryOrdinals() {
        assertThat(new ValidatedBoundaryDecision(List.of(0, 3, 7)).boundaryOrdinals())
                .containsExactly(0, 3, 7);
    }

    @Test
    void allowsNoInternalBoundaries() {
        assertThat(new ValidatedBoundaryDecision(List.of()).boundaryOrdinals()).isEmpty();
    }

    @Test
    void defensivelyCopiesAndExposesUnmodifiable() {
        List<Integer> ordinals = new ArrayList<>(List.of(1));

        ValidatedBoundaryDecision decision = new ValidatedBoundaryDecision(ordinals);
        ordinals.add(2);

        assertThat(decision.boundaryOrdinals()).containsExactly(1);
        assertThatThrownBy(() -> decision.boundaryOrdinals().add(9))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullList() {
        assertThatNullPointerException().isThrownBy(() -> new ValidatedBoundaryDecision(null));
    }

    @Test
    void rejectsNegativeOrdinal() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ValidatedBoundaryDecision(List.of(0, -1)));
    }

    @Test
    void hasValueEquality() {
        ValidatedBoundaryDecision one = new ValidatedBoundaryDecision(List.of(2, 4));
        ValidatedBoundaryDecision same = new ValidatedBoundaryDecision(List.of(2, 4));

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    }
}
