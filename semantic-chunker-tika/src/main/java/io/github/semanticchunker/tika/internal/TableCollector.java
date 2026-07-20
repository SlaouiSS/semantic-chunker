package io.github.semanticchunker.tika.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates the cells of one Tika XHTML {@code <table>} and renders them as GitHub-flavored
 * Markdown, as the core's {@link io.github.semanticchunker.document.Table} requires (Phase T1
 * mapping specification).
 *
 * <p>The core's table is a decided representation: its Markdown serialization is what the model
 * sees and what lands in the chunk, and its structured cells are retained so a consumer can
 * re-render it (CONTRIBUTING_ARCHITECTURE.md, section 7.2.5). Tika supplies neither directly — it
 * emits {@code <tr>}/{@code <td>} SAX events — so this collector produces both from those events.
 *
 * <p>It is a single-use, single-threaded helper owned by one {@link UnitAssemblingContentHandler}
 * for the span of one table; it is never shared.
 */
final class TableCollector {

    private final List<List<String>> rows = new ArrayList<>();
    private List<String> currentRow;
    private StringBuilder currentCell;
    private int bufferedChars;

    /** Begins a new row. */
    void startRow() {
        currentRow = new ArrayList<>();
    }

    /** Begins a new cell within the current row, if a row is open. */
    void startCell() {
        if (currentRow != null) {
            currentCell = new StringBuilder();
        }
    }

    /**
     * Appends character content to the open cell, if any. Content arriving outside a cell (such as
     * inter-element whitespace) is ignored.
     *
     * @param text the characters to append
     */
    void characters(String text) {
        if (currentCell != null) {
            currentCell.append(text);
            bufferedChars += text.length();
        }
    }

    /** Ends the current cell, recording its trimmed text. */
    void endCell() {
        if (currentRow != null && currentCell != null) {
            currentRow.add(currentCell.toString().trim());
            currentCell = null;
        }
    }

    /** Ends the current row, recording it if it holds any cells. */
    void endRow() {
        if (currentRow != null) {
            rows.add(currentRow);
            currentRow = null;
        }
    }

    /**
     * Whether the table recovered no cells at all.
     *
     * @return {@code true} if there are no rows
     */
    boolean isEmpty() {
        return rows.isEmpty();
    }

    /**
     * The number of characters accumulated so far across all cells, for write-limit accounting. It
     * is maintained incrementally, so the caller can bound working memory without rescanning.
     *
     * @return the accumulated character count
     */
    int bufferedLength() {
        return bufferedChars;
    }

    /**
     * The recovered cells, as rows of trimmed cell text.
     *
     * @return the rows; the returned list and its rows are freshly built and may be retained by the
     *     caller
     */
    List<List<String>> cells() {
        return rows;
    }

    /**
     * Renders the recovered cells as a GitHub-flavored Markdown table.
     *
     * <p>The first row is treated as the header, followed by a separator row and the remaining
     * rows. Ragged rows are padded to the widest row. Pipe characters within a cell are escaped and
     * newlines are folded to spaces, so the Markdown remains one well-formed row per table row.
     *
     * @return the Markdown serialization; empty only when there are no rows
     */
    String toMarkdown() {
        if (rows.isEmpty()) {
            return "";
        }
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
}
