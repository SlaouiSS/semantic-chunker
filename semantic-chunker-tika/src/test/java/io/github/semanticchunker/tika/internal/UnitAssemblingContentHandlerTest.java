package io.github.semanticchunker.tika.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Heading;
import io.github.semanticchunker.document.Image;
import io.github.semanticchunker.document.ListItem;
import io.github.semanticchunker.document.PageBreak;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.Provenance;
import io.github.semanticchunker.document.Table;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/** Tests {@link UnitAssemblingContentHandler} by driving it with SAX events directly. */
class UnitAssemblingContentHandlerTest {

    private final UnitAssemblingContentHandler handler = new UnitAssemblingContentHandler(-1);

    // --- SAX driving helpers -------------------------------------------------

    private void start(String tag, String... classAttr) throws SAXException {
        AttributesImpl atts = new AttributesImpl();
        if (classAttr.length > 0) {
            atts.addAttribute("", "class", "class", "CDATA", classAttr[0]);
        }
        handler.startElement("", tag, tag, atts);
    }

    private void startImg(String alt, String src) throws SAXException {
        AttributesImpl atts = new AttributesImpl();
        if (alt != null) {
            atts.addAttribute("", "alt", "alt", "CDATA", alt);
        }
        if (src != null) {
            atts.addAttribute("", "src", "src", "CDATA", src);
        }
        handler.startElement("", "img", "img", atts);
        handler.endElement("", "img", "img");
    }

    private void end(String tag) throws SAXException {
        handler.endElement("", tag, tag);
    }

    private void text(String value) throws SAXException {
        handler.characters(value.toCharArray(), 0, value.length());
    }

    private void block(String tag, String content) throws SAXException {
        start(tag);
        text(content);
        end(tag);
    }

    private void assertInvariantHolds() {
        String normalized = handler.normalizedText();
        List<DocumentUnit> units = handler.units();
        for (int i = 0; i < units.size(); i++) {
            DocumentUnit unit = units.get(i);
            Provenance provenance = unit.provenance();
            assertThat(provenance.globalOrdinal()).isEqualTo(i);
            assertThat(normalized.substring(provenance.startOffset(), provenance.endOffset()))
                    .isEqualTo(unit.text().orElse(""));
        }
        assertThat(normalized)
                .isEqualTo(units.stream().map(u -> u.text().orElse("")).reduce("", String::concat));
    }

    // --- Tests ---------------------------------------------------------------

    @Test
    void ignoresEverythingBeforeBody() throws SAXException {
        start("html");
        start("head");
        block("title", "Document title");
        end("head");

        assertThat(handler.units()).isEmpty();
        assertThat(handler.normalizedText()).isEmpty();
    }

    @Test
    void mapsHeadingsWithTheirLevel() throws SAXException {
        start("body");
        block("h1", "Top");
        block("h3", "Deeper");
        end("body");

        assertThat(handler.units()).hasSize(2);
        assertThat(handler.units().get(0))
                .isInstanceOfSatisfying(
                        Heading.class,
                        h -> {
                            assertThat(h.content()).isEqualTo("Top");
                            assertThat(h.metadata()).containsEntry("level", "1");
                        });
        assertThat(handler.units().get(1))
                .isInstanceOfSatisfying(
                        Heading.class, h -> assertThat(h.metadata()).containsEntry("level", "3"));
        assertInvariantHolds();
    }

    @Test
    void mapsParagraphs() throws SAXException {
        start("body");
        block("p", "A paragraph.");
        end("body");

        assertThat(handler.units()).singleElement().isInstanceOf(Paragraph.class);
        assertThat(handler.units().get(0).text()).contains("A paragraph.");
        assertInvariantHolds();
    }

    @Test
    void mapsBlockElementsToParagraphs() throws SAXException {
        start("body");
        block("pre", "code");
        block("blockquote", "quote");
        block("address", "addr");
        block("dt", "term");
        block("dd", "definition");
        end("body");

        assertThat(handler.units()).hasSize(5).allMatch(u -> u instanceof Paragraph);
        assertInvariantHolds();
    }

    @Test
    void mapsListItems() throws SAXException {
        start("body");
        start("ul");
        block("li", "First");
        block("li", "Second");
        end("ul");
        end("body");

        assertThat(handler.units()).hasSize(2).allMatch(u -> u instanceof ListItem);
        assertInvariantHolds();
    }

    @Test
    void mapsWordStyleListItemsAsParagraphsNotListItems() throws SAXException {
        // Word/PowerPoint emit list items as <p> with the bullet in the text, never <li>
        // (verified in Phase T1). The adapter faithfully maps them to Paragraph — a documented
        // loss.
        start("body");
        block("p", "• First bullet");
        block("p", "• Second bullet");
        end("body");

        assertThat(handler.units()).hasSize(2).allMatch(u -> u instanceof Paragraph);
        assertThat(handler.units().get(0).text()).contains("• First bullet");
    }

    @Test
    void skipsBlankTextBlocks() throws SAXException {
        start("body");
        block("p", "   \n  ");
        block("p", "Real content.");
        end("body");

        assertThat(handler.units()).singleElement().isInstanceOf(Paragraph.class);
        assertThat(handler.units().get(0).text()).contains("Real content.");
    }

    @Test
    void accumulatesTextAcrossInlineElements() throws SAXException {
        start("body");
        start("p");
        text("Hello ");
        start("b");
        text("bold");
        end("b");
        text(" world");
        end("p");
        end("body");

        assertThat(handler.units().get(0).text()).contains("Hello bold world");
        assertInvariantHolds();
    }

    @Test
    void mapsImageWithAltToCaption() throws SAXException {
        start("body");
        startImg("A photo", "embedded:image1.png");
        end("body");

        assertThat(handler.units())
                .singleElement()
                .isInstanceOfSatisfying(
                        Image.class,
                        image -> {
                            assertThat(image.caption()).isEqualTo("A photo");
                            assertThat(image.metadata())
                                    .containsEntry("src", "embedded:image1.png");
                        });
        assertInvariantHolds();
    }

    @Test
    void mapsImageWithoutAltToACaptionlessImage() throws SAXException {
        start("body");
        startImg(null, null);
        end("body");

        assertThat(handler.units())
                .singleElement()
                .isInstanceOfSatisfying(
                        Image.class,
                        image -> {
                            assertThat(image.caption()).isNull();
                            assertThat(image.text()).isEmpty();
                            assertThat(image.metadata()).isEmpty();
                        });
        assertInvariantHolds();
    }

    @Test
    void treatsEmptyAltAsAnEmptyCaption() throws SAXException {
        start("body");
        startImg("", "");
        end("body");

        assertThat(handler.units())
                .singleElement()
                .isInstanceOfSatisfying(
                        Image.class,
                        image -> {
                            assertThat(image.caption()).isEmpty();
                            assertThat(image.metadata()).isEmpty();
                        });
        assertInvariantHolds();
    }

    @Test
    void emitsAnInlineImageAsItsOwnUnitBeforeTheEnclosingParagraph() throws SAXException {
        start("body");
        start("p");
        text("before ");
        startImg("caption", "embedded:i.png");
        text("after");
        end("p");
        end("body");

        // A mid-paragraph image floats to its own unit, ordered before the paragraph text it was
        // embedded in (documented behaviour); no text is lost and the invariant still holds.
        assertThat(handler.units()).hasSize(2);
        assertThat(handler.units().get(0)).isInstanceOf(Image.class);
        assertThat(handler.units().get(1))
                .isInstanceOfSatisfying(
                        Paragraph.class, p -> assertThat(p.text()).contains("before after"));
        assertInvariantHolds();
    }

    @Test
    void emitsAnImageInsideATableCellAsASeparateUnit() throws SAXException {
        start("body");
        start("table");
        start("tr");
        start("td");
        text("cell ");
        startImg("pic", "embedded:i.png");
        end("td");
        end("tr");
        end("table");
        end("body");

        assertThat(handler.units()).hasSize(2);
        assertThat(handler.units().get(0))
                .isInstanceOfSatisfying(Image.class, i -> assertThat(i.caption()).isEqualTo("pic"));
        assertThat(handler.units().get(1))
                .isInstanceOfSatisfying(
                        Table.class, t -> assertThat(t.cells().get(0).get(0)).isEqualTo("cell"));
        assertInvariantHolds();
    }

    @Test
    void mapsTablesToMarkdownAndCells() throws SAXException {
        start("body");
        start("table");
        start("tr");
        block("td", "a");
        block("td", "b");
        end("tr");
        start("tr");
        block("td", "c");
        block("td", "d");
        end("tr");
        end("table");
        end("body");

        assertThat(handler.units())
                .singleElement()
                .isInstanceOfSatisfying(
                        Table.class,
                        table -> {
                            assertThat(table.cells())
                                    .isEqualTo(List.of(List.of("a", "b"), List.of("c", "d")));
                            assertThat(table.markdown()).contains("| a | b |");
                        });
        assertInvariantHolds();
    }

    @Test
    void handlesTableHeaderCells() throws SAXException {
        start("body");
        start("table");
        start("tr");
        block("th", "H1");
        block("th", "H2");
        end("tr");
        end("table");
        end("body");

        assertThat(handler.units().get(0))
                .isInstanceOfSatisfying(
                        Table.class,
                        table -> assertThat(table.cells()).isEqualTo(List.of(List.of("H1", "H2"))));
    }

    @Test
    void flattensNestedTablesIntoTheEnclosingCell() throws SAXException {
        start("body");
        start("table");
        start("tr");
        start("td");
        text("outer ");
        start("table");
        start("tr");
        block("td", "inner");
        end("tr");
        end("table");
        end("td");
        end("tr");
        end("table");
        end("body");

        assertThat(handler.units()).singleElement().isInstanceOf(Table.class);
        Table table = (Table) handler.units().get(0);
        assertThat(table.cells().get(0).get(0)).contains("outer").contains("inner");
        assertInvariantHolds();
    }

    @Test
    void omitsEmptyTables() throws SAXException {
        start("body");
        start("table");
        end("table");
        end("body");

        assertThat(handler.units()).isEmpty();
    }

    @Test
    void emitsPageBreaksBetweenPagesAndNumbersUnits() throws SAXException {
        start("body");
        start("div", "page");
        block("p", "Page one text.");
        end("div");
        start("div", "page");
        block("p", "Page two text.");
        end("div");
        end("body");

        List<DocumentUnit> units = handler.units();
        assertThat(units).hasSize(3);
        assertThat(units.get(0)).isInstanceOf(Paragraph.class);
        assertThat(units.get(0).provenance().page()).isEqualTo(1);
        assertThat(units.get(1)).isInstanceOf(PageBreak.class);
        assertThat(units.get(1).provenance().page()).isEqualTo(2);
        assertThat(units.get(2).provenance().page()).isEqualTo(2);
        assertInvariantHolds();
    }

    @Test
    void recognisesSheetAndSlideContainersAsPages() throws SAXException {
        start("body");
        start("div", "sheet");
        block("p", "Sheet one.");
        end("div");
        start("div", "slide-content");
        block("p", "Slide two.");
        end("div");
        end("body");

        assertThat(handler.units().get(1)).isInstanceOf(PageBreak.class);
    }

    @Test
    void ignoresNonPageDivs() throws SAXException {
        start("body");
        start("div", "annotation");
        block("p", "Inside a non-page div.");
        end("div");
        end("body");

        assertThat(handler.units()).hasSize(1);
        assertThat(handler.units().get(0).provenance().page()).isZero();
    }

    @Test
    void leavesUnpagedUnitsOnPageZero() throws SAXException {
        start("body");
        block("p", "No page container here.");
        end("body");

        assertThat(handler.units().get(0).provenance().page()).isZero();
    }

    @Test
    void flushesATrailingOpenBlockAtEndOfDocument() throws SAXException {
        start("body");
        start("p");
        text("Unclosed paragraph");
        // no </p>, no </body>
        handler.endDocument();

        assertThat(handler.units()).singleElement().isInstanceOf(Paragraph.class);
        assertThat(handler.units().get(0).text()).contains("Unclosed paragraph");
    }

    @Test
    void commitsAnOpenBlockWhenANewBlockStarts() throws SAXException {
        start("body");
        start("p");
        text("First");
        // a heading starts without closing the paragraph
        start("h1");
        text("Second");
        end("h1");
        end("body");

        assertThat(handler.units()).hasSize(2);
        assertThat(handler.units().get(0)).isInstanceOf(Paragraph.class);
        assertThat(handler.units().get(1)).isInstanceOf(Heading.class);
        assertInvariantHolds();
    }

    @Test
    void closesAnOpenBlockWhenATableStarts() throws SAXException {
        start("body");
        start("p");
        text("Before table");
        start("table");
        start("tr");
        block("td", "x");
        end("tr");
        end("table");
        end("body");

        assertThat(handler.units()).hasSize(2);
        assertThat(handler.units().get(0)).isInstanceOf(Paragraph.class);
        assertThat(handler.units().get(1)).isInstanceOf(Table.class);
        assertInvariantHolds();
    }

    @Test
    void resolvesTagsFromQNameWhenLocalNameIsEmpty() throws SAXException {
        handler.startElement("", "", "body", new AttributesImpl());
        handler.startElement("", "", "p", new AttributesImpl());
        text("From qName");
        handler.endElement("", "", "p");
        handler.endElement("", "", "body");

        assertThat(handler.units()).singleElement().isInstanceOf(Paragraph.class);
    }

    @Test
    void ignoresTableEndWithoutTableStart() throws SAXException {
        start("body");
        end("table");
        end("body");

        assertThat(handler.units()).isEmpty();
    }

    @Test
    void ignoresPageDivWithoutAClassAttribute() throws SAXException {
        start("body");
        start("div");
        block("p", "Div without a class.");
        end("div");
        end("body");

        assertThat(handler.units()).hasSize(1);
        assertThat(handler.units().get(0).provenance().page()).isZero();
    }

    @Test
    void throwsWhenTheWriteLimitIsExceeded() throws SAXException {
        UnitAssemblingContentHandler limited = new UnitAssemblingContentHandler(5);
        limited.startElement("", "body", "body", new AttributesImpl());
        limited.startElement("", "p", "p", new AttributesImpl());

        assertThatExceptionOfType(WriteLimitExceededException.class)
                .isThrownBy(() -> limited.characters("1234567890".toCharArray(), 0, 10))
                .satisfies(e -> assertThat(e.writeLimit()).isEqualTo(5));
    }

    @Test
    void boundsBufferedTextAcrossCharacterEventsBeforeCommit() throws SAXException {
        UnitAssemblingContentHandler limited = new UnitAssemblingContentHandler(5);
        limited.startElement("", "body", "body", new AttributesImpl());
        limited.startElement("", "p", "p", new AttributesImpl());
        limited.characters("123".toCharArray(), 0, 3); // within the limit, buffered uncommitted

        // The cumulative buffer (3 + 3) crosses the limit before any unit commits.
        assertThatExceptionOfType(WriteLimitExceededException.class)
                .isThrownBy(() -> limited.characters("456".toCharArray(), 0, 3));
    }

    @Test
    void boundsBufferedTableCellsBeforeCommit() throws SAXException {
        UnitAssemblingContentHandler limited = new UnitAssemblingContentHandler(5);
        limited.startElement("", "body", "body", new AttributesImpl());
        limited.startElement("", "table", "table", new AttributesImpl());
        limited.startElement("", "tr", "tr", new AttributesImpl());
        limited.startElement("", "td", "td", new AttributesImpl());
        limited.characters("123".toCharArray(), 0, 3); // buffered in the open cell

        assertThatExceptionOfType(WriteLimitExceededException.class)
                .isThrownBy(() -> limited.characters("456".toCharArray(), 0, 3));
    }

    @Test
    void acceptsBufferedTextWithinTheLimit() throws SAXException {
        UnitAssemblingContentHandler limited = new UnitAssemblingContentHandler(3);
        limited.startElement("", "body", "body", new AttributesImpl());
        limited.startElement("", "p", "p", new AttributesImpl());
        limited.characters("ok".toCharArray(), 0, 2);
        limited.endElement("", "p", "p");

        assertThat(limited.units()).hasSize(1);
    }
}
