package io.github.semanticchunker.tika.internal;

import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Heading;
import io.github.semanticchunker.document.Image;
import io.github.semanticchunker.document.ListItem;
import io.github.semanticchunker.document.PageBreak;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.Provenance;
import io.github.semanticchunker.document.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A SAX {@link org.xml.sax.ContentHandler} that folds Tika's XHTML event stream into the core's
 * ordered {@link DocumentUnit}s, exactly as validated in Phase T1.
 *
 * <p><strong>What it maps.</strong> Following the T1 mapping specification, and faithfully to what
 * Tika actually emits:
 *
 * <ul>
 *   <li>{@code <h1>}–{@code <h6>} become {@link Heading} (the level is recorded in metadata);
 *   <li>{@code <p>}, and the block elements {@code <pre>}, {@code <blockquote>}, {@code <address>},
 *       {@code <dt>}, {@code <dd>} become {@link Paragraph};
 *   <li>{@code <li>} becomes {@link ListItem} — note that Word and PowerPoint do not emit {@code
 *       <li>}, so their list items arrive as {@code <p>} with the bullet in the text and map to
 *       {@link Paragraph} (a documented fidelity loss, not a defect);
 *   <li>{@code <table>} becomes {@link Table}, its cells folded to Markdown by {@link
 *       TableCollector};
 *   <li>{@code <img>} becomes {@link Image}, its {@code alt} attribute the caption;
 *   <li>a page/section container {@code <div class="page|sheet|slide-content">} advances the page
 *       counter and, on every page after the first, emits a {@link PageBreak}.
 * </ul>
 *
 * <p><strong>The provenance offset invariant, by construction.</strong> The handler builds the
 * document's normalized text — the concatenation, in emission order, of each unit's textual content
 * — as it emits units, and stamps each unit's span as the slice it just appended. So {@code
 * normalizedText.substring(startOffset, endOffset)} always equals the unit's textual content, in
 * UTF-16 code units, exactly as the frozen {@link Provenance} contract requires. Text that belongs
 * to no unit (inter-element whitespace) is never appended, so the normalized text stays exactly the
 * concatenation of the units.
 *
 * <p><strong>Not thread-safe and single-use.</strong> One instance parses one document. The adapter
 * creates a fresh handler per extraction, so no parsing state is shared between calls.
 */
public final class UnitAssemblingContentHandler extends DefaultHandler {

    private static final Set<String> PAGE_BOUNDARY_CLASSES =
            Set.of("page", "sheet", "slide-content");

    /** A text-bearing block currently being accumulated. */
    private enum TextKind {
        HEADING,
        PARAGRAPH,
        LIST_ITEM
    }

    private final int writeLimit;

    private final StringBuilder normalizedText = new StringBuilder();
    private final List<DocumentUnit> units = new ArrayList<>();

    private boolean inBody;
    private int page;

    private TextKind pendingKind;
    private int pendingHeadingLevel;
    private StringBuilder pendingText;

    private int tableDepth;
    private TableCollector tableCollector;

    /**
     * Creates a handler that stops extraction once the given write limit is exceeded.
     *
     * <p>The limit is checked as text is accumulated, not only when a unit is committed, so it
     * bounds working memory rather than merely the committed output.
     *
     * @param writeLimit the maximum number of characters to accumulate, or {@code -1} for no limit
     */
    public UnitAssemblingContentHandler(int writeLimit) {
        this.writeLimit = writeLimit;
    }

    /**
     * The units recovered so far, in document order.
     *
     * @return an unmodifiable copy of the recovered units
     */
    public List<DocumentUnit> units() {
        return List.copyOf(units);
    }

    /**
     * The normalized text the offsets address. Package-private: consumed only by tests; the adapter
     * needs only {@link #units()}.
     *
     * @return the concatenation, in unit order, of every recovered unit's textual content
     */
    String normalizedText() {
        return normalizedText.toString();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        String tag = tagOf(localName, qName);
        if ("body".equals(tag)) {
            inBody = true;
            return;
        }
        if (!inBody) {
            return;
        }
        // Only the paragraph-family blocks are guarded by tableDepth: inside a table their <p>
        // wrappers are noise, since cell text arrives via characters() into the collector. A stray
        // heading or list item inside a cell is harmless — it opens a block whose text is routed to
        // the collector instead, so it commits empty and is skipped. An <img> in a cell is emitted
        // as its own Image unit, which is the faithful choice given the cell has no place for it.
        switch (tag) {
            case "div" -> maybeStartPage(attributes);
            case "h1", "h2", "h3", "h4", "h5", "h6" ->
                    startTextBlock(TextKind.HEADING, tag.charAt(1) - '0');
            case "p", "pre", "blockquote", "address", "dt", "dd" -> {
                if (tableDepth == 0) {
                    startTextBlock(TextKind.PARAGRAPH, 0);
                }
            }
            case "li" -> startTextBlock(TextKind.LIST_ITEM, 0);
            case "img" -> emitImage(attributes);
            case "table" -> startTable();
            case "tr" -> {
                if (tableDepth == 1) {
                    tableCollector.startRow();
                }
            }
            case "td", "th" -> {
                if (tableDepth == 1) {
                    tableCollector.startCell();
                }
            }
            default -> {
                // Inline and structural elements (a, b, i, ul, ol, span, ...) carry no unit of
                // their own; their text still flows into the enclosing block via characters().
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        String tag = tagOf(localName, qName);
        if ("body".equals(tag)) {
            inBody = false;
            return;
        }
        if (!inBody) {
            return;
        }
        switch (tag) {
            case "h1",
                    "h2",
                    "h3",
                    "h4",
                    "h5",
                    "h6",
                    "p",
                    "pre",
                    "blockquote",
                    "address",
                    "dt",
                    "dd",
                    "li" -> {
                if (pendingKind != null) {
                    commitTextBlock();
                }
            }
            case "table" -> endTable();
            case "tr" -> {
                if (tableDepth == 1) {
                    tableCollector.endRow();
                }
            }
            case "td", "th" -> {
                if (tableDepth == 1) {
                    tableCollector.endCell();
                }
            }
            default -> {
                // Nothing to close for inline or structural elements.
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (!inBody) {
            return;
        }
        String text = new String(ch, start, length);
        if (tableDepth > 0) {
            // Check before buffering, so the limit bounds working memory even when a single table
            // (or cell) is large, not only the committed output.
            guardWriteLimit(tableCollector.bufferedLength() + length);
            tableCollector.characters(text);
        } else if (pendingKind != null) {
            guardWriteLimit(pendingText.length() + length);
            pendingText.append(text);
        }
        // Text outside any block or cell is inter-element whitespace and belongs to no unit.
    }

    @Override
    public void endDocument() throws SAXException {
        if (pendingKind != null) {
            commitTextBlock();
        }
    }

    private void maybeStartPage(Attributes attributes) {
        String styleClass = attributes.getValue("class");
        if (styleClass == null) {
            styleClass = attributes.getValue("", "class");
        }
        if (styleClass == null || !PAGE_BOUNDARY_CLASSES.contains(styleClass)) {
            return;
        }
        if (page >= 1) {
            page++;
            int offset = normalizedText.length();
            units.add(new PageBreak(new Provenance(units.size(), page, offset, offset), Map.of()));
        } else {
            page = 1;
        }
    }

    private void startTextBlock(TextKind kind, int headingLevel) throws SAXException {
        if (pendingKind != null) {
            commitTextBlock();
        }
        pendingKind = kind;
        pendingHeadingLevel = headingLevel;
        pendingText = new StringBuilder();
    }

    private void commitTextBlock() throws SAXException {
        String content = pendingText.toString();
        TextKind kind = pendingKind;
        int headingLevel = pendingHeadingLevel;
        pendingKind = null;
        pendingText = null;
        pendingHeadingLevel = 0;
        if (content.isBlank()) {
            return;
        }
        int start = normalizedText.length();
        append(content);
        Provenance provenance = new Provenance(units.size(), page, start, normalizedText.length());
        units.add(
                switch (kind) {
                    case HEADING ->
                            new Heading(
                                    provenance,
                                    content,
                                    Map.of("level", Integer.toString(headingLevel)));
                    case PARAGRAPH -> new Paragraph(provenance, content, Map.of());
                    case LIST_ITEM -> new ListItem(provenance, content, Map.of());
                });
    }

    private void emitImage(Attributes attributes) throws SAXException {
        String caption = attributes.getValue("alt");
        String source = attributes.getValue("src");
        int start = normalizedText.length();
        if (caption != null && !caption.isEmpty()) {
            append(caption);
        }
        Provenance provenance = new Provenance(units.size(), page, start, normalizedText.length());
        Map<String, String> metadata =
                source == null || source.isEmpty() ? Map.of() : Map.of("src", source);
        units.add(new Image(provenance, caption, metadata));
    }

    private void startTable() throws SAXException {
        if (pendingKind != null) {
            commitTextBlock();
        }
        tableDepth++;
        if (tableDepth == 1) {
            tableCollector = new TableCollector();
        }
    }

    private void endTable() throws SAXException {
        if (tableDepth == 0) {
            return;
        }
        tableDepth--;
        if (tableDepth != 0) {
            return;
        }
        if (!tableCollector.isEmpty()) {
            List<List<String>> cells = tableCollector.cells();
            String markdown = tableCollector.toMarkdown();
            int start = normalizedText.length();
            append(markdown);
            Provenance provenance =
                    new Provenance(units.size(), page, start, normalizedText.length());
            units.add(new Table(provenance, markdown, cells, Map.of()));
        }
        tableCollector = null;
    }

    private void append(String text) throws SAXException {
        guardWriteLimit(text.length());
        normalizedText.append(text);
    }

    /**
     * Fails if committing {@code additionalChars} more characters would exceed the write limit. A
     * negative limit means unlimited. Checking before accumulation keeps working memory bounded.
     */
    private void guardWriteLimit(int additionalChars) throws SAXException {
        if (writeLimit >= 0 && normalizedText.length() + additionalChars > writeLimit) {
            throw new WriteLimitExceededException(writeLimit);
        }
    }

    private static String tagOf(String localName, String qName) {
        String tag = localName == null || localName.isEmpty() ? qName : localName;
        return tag == null ? "" : tag.toLowerCase(java.util.Locale.ROOT);
    }
}
