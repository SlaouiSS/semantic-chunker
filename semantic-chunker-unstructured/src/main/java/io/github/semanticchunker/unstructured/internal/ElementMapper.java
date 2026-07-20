package io.github.semanticchunker.unstructured.internal;

import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Heading;
import io.github.semanticchunker.document.Image;
import io.github.semanticchunker.document.ListItem;
import io.github.semanticchunker.document.PageBreak;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.Provenance;
import io.github.semanticchunker.document.Table;
import io.github.semanticchunker.unstructured.internal.TableHtmlConverter.ParsedTable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds an Unstructured partition response — a flat, ordered list of elements — into the core's
 * ordered {@link DocumentUnit}s, exactly as validated in Phase U1.
 *
 * <p><strong>What it maps.</strong> Following the U1 mapping specification:
 *
 * <ul>
 *   <li>{@code Title} becomes {@link Heading} (its {@code category_depth} recorded as the {@code
 *       level} metadata);
 *   <li>{@code ListItem} becomes {@link ListItem} — preserved exactly, unlike the Tika adapter,
 *       because Unstructured classifies list items directly;
 *   <li>{@code Table} becomes {@link Table}, its {@code text_as_html} converted to cells and
 *       Markdown by {@link TableHtmlConverter}, or its plain text when no HTML is present;
 *   <li>{@code Image} becomes {@link Image}, its text the caption;
 *   <li>{@code PageBreak} becomes {@link PageBreak};
 *   <li>every other text-bearing element ({@code NarrativeText}, {@code Header}, {@code Footer},
 *       {@code Address}, {@code Formula}, {@code CodeSnippet}, and the rest) becomes {@link
 *       Paragraph} — a documented fidelity loss, not a defect.
 * </ul>
 *
 * <p><strong>The provenance offset invariant, by construction.</strong> The mapper builds the
 * document's normalized text — the concatenation, in element order, of each unit's textual content
 * — as it emits units, and stamps each unit's span as the slice it just appended. So {@code
 * normalizedText.substring(startOffset, endOffset)} always equals the unit's textual content, in
 * UTF-16 code units, exactly as the frozen {@link Provenance} contract requires.
 *
 * <p><strong>Single-use and not thread-safe.</strong> One instance maps one response. The adapter
 * creates a fresh mapper per extraction, so no state is shared between calls.
 */
public final class ElementMapper {

    /** A text-bearing unit kind. */
    private enum TextKind {
        HEADING,
        PARAGRAPH,
        LIST_ITEM
    }

    private final StringBuilder normalizedText = new StringBuilder();
    private final List<DocumentUnit> units = new ArrayList<>();

    /**
     * Maps the elements, in order, into document units.
     *
     * @param elements the partition elements; {@code null} is treated as empty
     * @return an unmodifiable list of the recovered units, in document order
     */
    public List<DocumentUnit> map(List<UnstructuredElement> elements) {
        if (elements != null) {
            for (UnstructuredElement element : elements) {
                mapElement(element);
            }
        }
        return List.copyOf(units);
    }

    /**
     * The normalized text the offsets address. Package-private: consumed only by tests.
     *
     * @return the concatenation, in unit order, of every recovered unit's textual content
     */
    String normalizedText() {
        return normalizedText.toString();
    }

    private void mapElement(UnstructuredElement element) {
        if (element == null) {
            return;
        }
        UnstructuredMetadata metadata = element.metadata();
        int page = pageOf(metadata);
        String type = element.type() == null ? "" : element.type();
        switch (type) {
            case "Title" -> emitText(TextKind.HEADING, element.text(), page, metadata);
            case "ListItem" -> emitText(TextKind.LIST_ITEM, element.text(), page, metadata);
            case "Table" -> emitTable(element, metadata, page);
            case "Image" -> emitImage(element, metadata, page);
            case "PageBreak" -> emitPageBreak(page);
            default -> emitText(TextKind.PARAGRAPH, element.text(), page, metadata);
        }
    }

    private void emitText(TextKind kind, String text, int page, UnstructuredMetadata metadata) {
        if (text == null || text.isBlank()) {
            return;
        }
        int start = normalizedText.length();
        normalizedText.append(text);
        Provenance provenance = new Provenance(units.size(), page, start, normalizedText.length());
        Integer level =
                kind == TextKind.HEADING && metadata != null ? metadata.categoryDepth() : null;
        units.add(
                switch (kind) {
                    case HEADING -> new Heading(provenance, text, unitMetadata(metadata, level));
                    case PARAGRAPH -> new Paragraph(provenance, text, unitMetadata(metadata, null));
                    case LIST_ITEM -> new ListItem(provenance, text, unitMetadata(metadata, null));
                });
    }

    private void emitTable(UnstructuredElement element, UnstructuredMetadata metadata, int page) {
        String html = metadata == null ? null : metadata.textAsHtml();
        ParsedTable parsed = TableHtmlConverter.parse(html);
        String markdown;
        List<List<String>> cells;
        if (parsed.isEmpty()) {
            markdown = element.text() == null ? "" : element.text();
            cells = List.of();
        } else {
            markdown = parsed.markdown();
            cells = parsed.cells();
        }
        if (markdown.isBlank()) {
            return;
        }
        int start = normalizedText.length();
        normalizedText.append(markdown);
        Provenance provenance = new Provenance(units.size(), page, start, normalizedText.length());
        units.add(new Table(provenance, markdown, cells, unitMetadata(metadata, null)));
    }

    private void emitImage(UnstructuredElement element, UnstructuredMetadata metadata, int page) {
        String caption = element.text();
        int start = normalizedText.length();
        if (caption != null && !caption.isEmpty()) {
            normalizedText.append(caption);
        }
        Provenance provenance = new Provenance(units.size(), page, start, normalizedText.length());
        units.add(new Image(provenance, caption, unitMetadata(metadata, null)));
    }

    private void emitPageBreak(int page) {
        int offset = normalizedText.length();
        units.add(new PageBreak(new Provenance(units.size(), page, offset, offset), Map.of()));
    }

    private static int pageOf(UnstructuredMetadata metadata) {
        if (metadata == null || metadata.pageNumber() == null || metadata.pageNumber() < 0) {
            return 0;
        }
        return metadata.pageNumber();
    }

    private static Map<String, String> unitMetadata(UnstructuredMetadata metadata, Integer level) {
        Map<String, String> result = new LinkedHashMap<>();
        if (level != null) {
            result.put("level", level.toString());
        }
        if (metadata != null) {
            if (metadata.languages() != null && !metadata.languages().isEmpty()) {
                result.put("languages", String.join(", ", metadata.languages()));
            }
            if (metadata.coordinates() != null && !metadata.coordinates().isNull()) {
                result.put("coordinates", metadata.coordinates().toString());
            }
            if (metadata.filetype() != null && !metadata.filetype().isBlank()) {
                result.put("filetype", metadata.filetype());
            }
        }
        return Map.copyOf(result);
    }
}
