package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.Provenance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WindowDecisionTest {

    private static DocumentUnit unit(int ordinal) {
        return new Paragraph(
                new Provenance(ordinal, 1, ordinal, ordinal + 1), "u" + ordinal, Map.of());
    }

    private static Window window() {
        return new Window(List.of(unit(0), unit(1)));
    }

    private static ValidatedBoundaryDecision decision() {
        return new ValidatedBoundaryDecision(List.of(1));
    }

    @Test
    void carriesItsWindowAndDecision() {
        Window window = window();
        ValidatedBoundaryDecision decision = decision();

        WindowDecision pair = new WindowDecision(window, decision);

        assertThat(pair.window()).isSameAs(window);
        assertThat(pair.decision()).isSameAs(decision);
    }

    @Test
    void rejectsANullWindow() {
        assertThatNullPointerException().isThrownBy(() -> new WindowDecision(null, decision()));
    }

    @Test
    void rejectsANullDecision() {
        assertThatNullPointerException().isThrownBy(() -> new WindowDecision(window(), null));
    }
}
