package io.github.semanticchunker.unstructured.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.semanticchunker.unstructured.internal.TableHtmlConverter.ParsedTable;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests {@link TableHtmlConverter}. */
class TableHtmlConverterTest {

    @Test
    void parsesRowsIntoCellsAndMarkdown() {
        ParsedTable parsed =
                TableHtmlConverter.parse(
                        "<table><tr><td>h1</td><td>h2</td></tr>"
                                + "<tr><td>a</td><td>b</td></tr></table>");

        assertThat(parsed.cells()).isEqualTo(List.of(List.of("h1", "h2"), List.of("a", "b")));
        assertThat(parsed.markdown()).isEqualTo("| h1 | h2 |\n| --- | --- |\n| a | b |\n");
    }

    @Test
    void handlesHeaderCells() {
        ParsedTable parsed =
                TableHtmlConverter.parse("<table><tr><th>H1</th><th>H2</th></tr></table>");

        assertThat(parsed.cells()).isEqualTo(List.of(List.of("H1", "H2")));
    }

    @Test
    void padsRaggedRows() {
        ParsedTable parsed =
                TableHtmlConverter.parse(
                        "<table><tr><td>a</td><td>b</td></tr><tr><td>c</td></tr></table>");

        assertThat(parsed.markdown()).isEqualTo("| a | b |\n| --- | --- |\n| c |  |\n");
    }

    @Test
    void escapesPipes() {
        ParsedTable parsed = TableHtmlConverter.parse("<table><tr><td>a|b</td></tr></table>");

        assertThat(parsed.markdown()).isEqualTo("| a\\|b |\n| --- |\n");
    }

    @Test
    void trimsCellText() {
        ParsedTable parsed = TableHtmlConverter.parse("<table><tr><td>  x  </td></tr></table>");

        assertThat(parsed.cells()).isEqualTo(List.of(List.of("x")));
    }

    @Test
    void isEmptyForNullHtml() {
        assertThat(TableHtmlConverter.parse(null).isEmpty()).isTrue();
    }

    @Test
    void isEmptyForBlankHtml() {
        assertThat(TableHtmlConverter.parse("   ").isEmpty()).isTrue();
    }

    @Test
    void isEmptyWhenNoTablePresent() {
        assertThat(TableHtmlConverter.parse("<div>no table here</div>").isEmpty()).isTrue();
    }

    @Test
    void isEmptyWhenTableHasNoCells() {
        assertThat(TableHtmlConverter.parse("<table></table>").isEmpty()).isTrue();
    }

    @Test
    void isEmptyWhenRowsHaveNoCells() {
        assertThat(TableHtmlConverter.parse("<table><tr></tr></table>").isEmpty()).isTrue();
    }
}
