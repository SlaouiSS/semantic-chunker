package io.github.semanticchunker.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TableTest {

    private static Provenance provenance() {
        return new Provenance(2, 1, 10, 40);
    }

    @Test
    void exposesMarkdownAsTextAlongsideStructuredCells() {
        List<List<String>> cells = List.of(List.of("a", "b"), List.of("c", "d"));

        Table table = new Table(provenance(), "| a | b |", cells, Map.of("k", "v"));

        assertThat(table.text()).contains("| a | b |");
        assertThat(table.markdown()).isEqualTo("| a | b |");
        assertThat(table.cells()).isEqualTo(cells);
        assertThat(table.metadata()).containsEntry("k", "v");
    }

    @Test
    void treatsNullCellsAsEmpty() {
        Table table = new Table(provenance(), "md", null, Map.of());

        assertThat(table.cells()).isEmpty();
    }

    @Test
    void treatsNullMetadataAsEmpty() {
        assertThat(new Table(provenance(), "md", List.of(), null).metadata()).isEmpty();
    }

    @Test
    void deeplyCopiesCellsAndExposesThemUnmodifiable() {
        List<String> row = new ArrayList<>(List.of("x", "y"));
        List<List<String>> cells = new ArrayList<>();
        cells.add(row);

        Table table = new Table(provenance(), "md", cells, Map.of());
        cells.add(List.of("added"));
        row.add("z");

        assertThat(table.cells()).hasSize(1);
        assertThat(table.cells().get(0)).containsExactly("x", "y");
        assertThatThrownBy(() -> table.cells().add(List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> table.cells().get(0).add("w"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullProvenance() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Table(null, "md", List.of(), Map.of()));
    }

    @Test
    void rejectsNullMarkdown() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Table(provenance(), null, List.of(), Map.of()));
    }

    @Test
    void hasValueEquality() {
        Table one = new Table(provenance(), "md", List.of(List.of("a")), Map.of());
        Table same = new Table(provenance(), "md", List.of(List.of("a")), Map.of());

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    }
}
