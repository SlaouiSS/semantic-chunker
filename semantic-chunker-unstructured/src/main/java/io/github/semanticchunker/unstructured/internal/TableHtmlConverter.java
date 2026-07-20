package io.github.semanticchunker.unstructured.internal;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/**
 * Turns a table's Unstructured {@code text_as_html} into the structured cells and GitHub-flavored
 * Markdown the core's {@link io.github.semanticchunker.document.Table} requires (Phase U1 mapping).
 *
 * <p>The core's table is a decided representation: its Markdown is what the model sees and what
 * lands in the chunk, and its structured cells are retained so a consumer can re-render it
 * (CONTRIBUTING_ARCHITECTURE.md, section 7.2.5). Unstructured supplies an HTML table string, so
 * this converter parses it with jsoup and produces both.
 *
 * <p>The Markdown rendering intentionally matches the one in the Tika adapter's {@code
 * TableCollector}. The two are not shared: the modules are independent adapters, and a common
 * utility module would be a speculative abstraction for a dozen lines. The duplication is
 * deliberate.
 */
final class TableHtmlConverter {

    private TableHtmlConverter() {}

    /**
     * Parses an HTML table into cells and Markdown.
     *
     * @param html the {@code text_as_html} value; may be {@code null} or blank
     * @return the parsed table, empty when {@code html} holds no table or no cells
     */
    static ParsedTable parse(String html) {
        if (html == null || html.isBlank()) {
            return ParsedTable.empty();
        }
        Element table = Jsoup.parse(html).selectFirst("table");
        if (table == null) {
            return ParsedTable.empty();
        }
        List<List<String>> rows = new ArrayList<>();
        for (Element tr : table.select("tr")) {
            List<String> row = new ArrayList<>();
            for (Element cell : tr.select("td, th")) {
                row.add(cell.text().trim());
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            return ParsedTable.empty();
        }
        return new ParsedTable(rows, toMarkdown(rows));
    }

    private static String toMarkdown(List<List<String>> rows) {
        int columns = 0;
        for (List<String> row : rows) {
            columns = Math.max(columns, row.size());
        }
        StringBuilder markdown = new StringBuilder();
        appendRow(markdown, rows.get(0), columns);
        appendSeparator(markdown, columns);
        for (int index = 1; index < rows.size(); index++) {
            appendRow(markdown, rows.get(index), columns);
        }
        return markdown.toString();
    }

    private static void appendRow(StringBuilder markdown, List<String> row, int columns) {
        markdown.append('|');
        for (int column = 0; column < columns; column++) {
            String cell = column < row.size() ? row.get(column) : "";
            markdown.append(' ').append(escape(cell)).append(" |");
        }
        markdown.append('\n');
    }

    private static void appendSeparator(StringBuilder markdown, int columns) {
        markdown.append('|');
        for (int column = 0; column < columns; column++) {
            markdown.append(" --- |");
        }
        markdown.append('\n');
    }

    private static String escape(String cell) {
        return cell.replace("|", "\\|").replace('\n', ' ').replace('\r', ' ');
    }

    /**
     * The result of parsing an HTML table: its cells and Markdown.
     *
     * @param cells the rows of trimmed cell text; empty when nothing was recovered
     * @param markdown the Markdown serialization; empty when nothing was recovered
     */
    record ParsedTable(List<List<String>> cells, String markdown) {

        private static final ParsedTable EMPTY = new ParsedTable(List.of(), "");

        static ParsedTable empty() {
            return EMPTY;
        }

        boolean isEmpty() {
            return cells.isEmpty();
        }
    }
}
