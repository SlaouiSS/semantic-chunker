package io.github.semanticchunker.tika.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests {@link TableCollector}. */
class TableCollectorTest {

    private final TableCollector collector = new TableCollector();

    private void row(String... cells) {
        collector.startRow();
        for (String cell : cells) {
            collector.startCell();
            collector.characters(cell);
            collector.endCell();
        }
        collector.endRow();
    }

    @Test
    void isEmptyUntilARowIsRecorded() {
        assertThat(collector.isEmpty()).isTrue();

        row("a");

        assertThat(collector.isEmpty()).isFalse();
    }

    @Test
    void rendersAHeaderSeparatorAndBodyRows() {
        row("h1", "h2");
        row("a", "b");
        row("c", "d");

        assertThat(collector.toMarkdown())
                .isEqualTo("| h1 | h2 |\n| --- | --- |\n| a | b |\n| c | d |\n");
    }

    @Test
    void retainsStructuredCells() {
        row("a", "b");
        row("c", "d");

        assertThat(collector.cells()).isEqualTo(List.of(List.of("a", "b"), List.of("c", "d")));
    }

    @Test
    void trimsCellText() {
        collector.startRow();
        collector.startCell();
        collector.characters("  spaced  ");
        collector.endCell();
        collector.endRow();

        assertThat(collector.cells()).isEqualTo(List.of(List.of("spaced")));
    }

    @Test
    void padsRaggedRowsToTheWidestRow() {
        row("a", "b", "c");
        row("x");

        assertThat(collector.toMarkdown())
                .isEqualTo("| a | b | c |\n| --- | --- | --- |\n| x |  |  |\n");
    }

    @Test
    void escapesPipesAndFoldsNewlines() {
        collector.startRow();
        collector.startCell();
        collector.characters("a|b\nc");
        collector.endCell();
        collector.endRow();

        assertThat(collector.toMarkdown()).isEqualTo("| a\\|b c |\n| --- |\n");
    }

    @Test
    void appendsAcrossMultipleCharacterCallsWithinACell() {
        collector.startRow();
        collector.startCell();
        collector.characters("Hello ");
        collector.characters("world");
        collector.endCell();
        collector.endRow();

        assertThat(collector.cells()).isEqualTo(List.of(List.of("Hello world")));
    }

    @Test
    void ignoresCharactersOutsideAnyCell() {
        collector.characters("stray");
        row("a");

        assertThat(collector.cells()).isEqualTo(List.of(List.of("a")));
    }

    @Test
    void ignoresACellStartedWithoutARow() {
        collector.startCell();
        collector.characters("orphan");
        collector.endCell();

        assertThat(collector.isEmpty()).isTrue();
    }

    @Test
    void rendersEmptyMarkdownWhenNoRows() {
        assertThat(collector.toMarkdown()).isEmpty();
    }

    @Test
    void rendersASingleRowAsHeaderWithSeparator() {
        row("only");

        assertThat(collector.toMarkdown()).isEqualTo("| only |\n| --- |\n");
    }

    @Test
    void ignoresRowEndWithoutRowStart() {
        collector.endRow();

        assertThat(collector.isEmpty()).isTrue();
    }

    @Test
    void ignoresCellEndWithoutCellStart() {
        collector.startRow();
        collector.endCell();
        collector.endRow();

        assertThat(collector.cells()).isEqualTo(List.of(List.of()));
    }
}
