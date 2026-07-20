package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.Provenance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WindowTest {

    private static DocumentUnit paragraph(int ordinal) {
        return new Paragraph(
                new Provenance(ordinal, 1, ordinal, ordinal + 1), "u" + ordinal, Map.of());
    }

    @Test
    void viewsAContiguousRunOfUnits() {
        List<DocumentUnit> units = List.of(paragraph(0), paragraph(1));

        assertThat(new Window(units).units()).containsExactlyElementsOf(units);
    }

    @Test
    void defensivelyCopiesUnitsAndExposesThemUnmodifiable() {
        List<DocumentUnit> units = new ArrayList<>();
        units.add(paragraph(0));

        Window window = new Window(units);
        units.add(paragraph(1));

        assertThat(window.units()).hasSize(1);
        assertThatThrownBy(() -> window.units().add(paragraph(2)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullUnits() {
        assertThatNullPointerException().isThrownBy(() -> new Window(null));
    }

    @Test
    void hasValueEquality() {
        Window one = new Window(List.of(paragraph(0)));
        Window same = new Window(List.of(paragraph(0)));

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    }

    @Test
    void isAdmissibleByDefault() {
        assertThat(new Window(List.of(paragraph(0))).overBudget()).isFalse();
    }

    @Test
    void canBeMarkedOverBudget() {
        assertThat(new Window(List.of(paragraph(0)), true).overBudget()).isTrue();
    }

    @Test
    void distinguishesAdmissibleFromOverBudgetByValue() {
        Window admissible = new Window(List.of(paragraph(0)), false);
        Window overBudget = new Window(List.of(paragraph(0)), true);

        assertThat(admissible).isNotEqualTo(overBudget);
    }

    @Test
    void doesNotContainTheDocumentFirstUnitByDefault() {
        assertThat(new Window(List.of(paragraph(0))).containsDocumentFirstUnit()).isFalse();
        assertThat(new Window(List.of(paragraph(0)), true).containsDocumentFirstUnit()).isFalse();
    }

    @Test
    void canBeMarkedAsContainingTheDocumentFirstUnit() {
        assertThat(new Window(List.of(paragraph(0)), false, true).containsDocumentFirstUnit())
                .isTrue();
    }
}
