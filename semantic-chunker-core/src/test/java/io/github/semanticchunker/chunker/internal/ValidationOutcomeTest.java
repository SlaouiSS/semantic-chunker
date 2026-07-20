package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.github.semanticchunker.chunker.internal.ValidationOutcome.RejectionReason;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationOutcomeTest {

    @Test
    void anAcceptedOutcomeCarriesItsDecision() {
        ValidatedBoundaryDecision decision = new ValidatedBoundaryDecision(List.of(1, 2));

        assertThat(new ValidationOutcome.Accepted(decision).decision()).isEqualTo(decision);
    }

    @Test
    void aRejectedOutcomeCarriesItsReason() {
        assertThat(new ValidationOutcome.Rejected(RejectionReason.MALFORMED).reason())
                .isEqualTo(RejectionReason.MALFORMED);
    }

    @Test
    void rejectsANullDecision() {
        assertThatNullPointerException().isThrownBy(() -> new ValidationOutcome.Accepted(null));
    }

    @Test
    void rejectsANullReason() {
        assertThatNullPointerException().isThrownBy(() -> new ValidationOutcome.Rejected(null));
    }
}
