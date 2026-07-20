package io.github.semanticchunker.unstructured.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.Test;

/** Tests {@link ElementMapper}. */
class ElementMapperTest {

    private final ElementMapper mapper = new ElementMapper();

    // --- helpers -------------------------------------------------------------

    private static UnstructuredMetadata meta(Integer page) {
        return new UnstructuredMetadata(page, null, null, null, null, null, null);
    }

    private static UnstructuredElement element(String type, String text, UnstructuredMetadata md) {
        return new UnstructuredElement(type, "id", text, md);
    }

    private List<DocumentUnit> map(UnstructuredElement... elements) {
        return mapper.map(List.of(elements));
    }

    private void assertInvariant(List<DocumentUnit> units) {
        String normalized = mapper.normalizedText();
        int ordinal = 0;
        for (DocumentUnit unit : units) {
            Provenance provenance = unit.provenance();
            assertThat(provenance.globalOrdinal()).isEqualTo(ordinal++);
            assertThat(normalized.substring(provenance.startOffset(), provenance.endOffset()))
                    .isEqualTo(unit.text().orElse(""));
        }
        assertThat(normalized)
                .isEqualTo(units.stream().map(u -> u.text().orElse("")).reduce("", String::concat));
    }

    // --- type mapping --------------------------------------------------------

    @Test
    void mapsTitleToHeadingWithLevelFromCategoryDepth() {
        UnstructuredMetadata md = new UnstructuredMetadata(1, null, null, 2, null, null, null);

        List<DocumentUnit> units = map(element("Title", "A Heading", md));

        assertThat(units)
                .singleElement()
                .isInstanceOfSatisfying(
                        Heading.class,
                        h -> {
                            assertThat(h.content()).isEqualTo("A Heading");
                            assertThat(h.metadata()).containsEntry("level", "2");
                        });
        assertInvariant(units);
    }

    @Test
    void mapsNarrativeTextToParagraph() {
        List<DocumentUnit> units = map(element("NarrativeText", "Body text.", meta(1)));

        assertThat(units).singleElement().isInstanceOf(Paragraph.class);
        assertInvariant(units);
    }

    @Test
    void mapsListItemToListItem() {
        List<DocumentUnit> units = map(element("ListItem", "An item", meta(1)));

        assertThat(units)
                .singleElement()
                .isInstanceOfSatisfying(
                        ListItem.class, li -> assertThat(li.content()).isEqualTo("An item"));
        assertInvariant(units);
    }

    @Test
    void mapsOtherTextTypesToParagraph() {
        List<DocumentUnit> units =
                map(
                        element("Header", "h", meta(1)),
                        element("Footer", "f", meta(1)),
                        element("Address", "a", meta(1)),
                        element("Formula", "x=1", meta(1)),
                        element("CodeSnippet", "code", meta(1)),
                        element("UncategorizedText", "u", meta(1)),
                        element("PageNumber", "5", meta(1)));

        assertThat(units).hasSize(7).allMatch(u -> u instanceof Paragraph);
        assertInvariant(units);
    }

    @Test
    void mapsUnknownTypeWithTextToParagraph() {
        List<DocumentUnit> units = map(element("SomeFutureType", "still text", meta(1)));

        assertThat(units).singleElement().isInstanceOf(Paragraph.class);
    }

    @Test
    void mapsNullTypeWithTextToParagraph() {
        List<DocumentUnit> units = map(element(null, "text", meta(1)));

        assertThat(units).singleElement().isInstanceOf(Paragraph.class);
    }

    // --- tables --------------------------------------------------------------

    @Test
    void mapsTableFromTextAsHtml() {
        UnstructuredMetadata md =
                new UnstructuredMetadata(
                        2,
                        "<table><tr><td>a</td><td>b</td></tr><tr><td>c</td><td>d</td></tr></table>",
                        null,
                        null,
                        null,
                        null,
                        null);

        List<DocumentUnit> units = map(element("Table", "a b c d", md));

        assertThat(units)
                .singleElement()
                .isInstanceOfSatisfying(
                        Table.class,
                        t -> {
                            assertThat(t.cells())
                                    .isEqualTo(List.of(List.of("a", "b"), List.of("c", "d")));
                            assertThat(t.markdown()).contains("| a | b |");
                        });
        assertInvariant(units);
    }

    @Test
    void fallsBackToPlainTextWhenNoHtml() {
        List<DocumentUnit> units = map(element("Table", "row one row two", meta(1)));

        assertThat(units)
                .singleElement()
                .isInstanceOfSatisfying(
                        Table.class,
                        t -> {
                            assertThat(t.markdown()).isEqualTo("row one row two");
                            assertThat(t.cells()).isEmpty();
                        });
        assertInvariant(units);
    }

    @Test
    void skipsBlankTable() {
        List<DocumentUnit> units = map(element("Table", "   ", meta(1)));

        assertThat(units).isEmpty();
    }

    // --- images --------------------------------------------------------------

    @Test
    void mapsImageWithCaption() {
        List<DocumentUnit> units = map(element("Image", "A caption", meta(1)));

        assertThat(units)
                .singleElement()
                .isInstanceOfSatisfying(
                        Image.class, i -> assertThat(i.caption()).isEqualTo("A caption"));
        assertInvariant(units);
    }

    @Test
    void mapsImageWithoutCaption() {
        List<DocumentUnit> units = map(element("Image", null, meta(1)));

        assertThat(units)
                .singleElement()
                .isInstanceOfSatisfying(
                        Image.class,
                        i -> {
                            assertThat(i.caption()).isNull();
                            assertThat(i.text()).isEmpty();
                        });
        assertInvariant(units);
    }

    @Test
    void mapsImageWithEmptyCaption() {
        List<DocumentUnit> units = map(element("Image", "", meta(1)));

        assertThat(units).singleElement().isInstanceOf(Image.class);
        assertInvariant(units);
    }

    // --- page breaks ---------------------------------------------------------

    @Test
    void mapsPageBreakWithZeroLengthSpan() {
        List<DocumentUnit> units =
                map(element("NarrativeText", "text", meta(1)), element("PageBreak", null, meta(2)));

        assertThat(units.get(1))
                .isInstanceOfSatisfying(
                        PageBreak.class,
                        pb -> {
                            assertThat(pb.text()).isEmpty();
                            assertThat(pb.provenance().page()).isEqualTo(2);
                            assertThat(pb.provenance().startOffset())
                                    .isEqualTo(pb.provenance().endOffset());
                        });
        assertInvariant(units);
    }

    // --- blank skipping ------------------------------------------------------

    @Test
    void skipsBlankTextElements() {
        List<DocumentUnit> units =
                map(
                        element("Title", "  ", meta(1)),
                        element("NarrativeText", null, meta(1)),
                        element("ListItem", "\n\t", meta(1)),
                        element("NarrativeText", "kept", meta(1)));

        assertThat(units).singleElement().isInstanceOf(Paragraph.class);
        assertThat(units.get(0).text()).contains("kept");
    }

    // --- provenance page -----------------------------------------------------

    @Test
    void assignsPageFromMetadata() {
        List<DocumentUnit> units = map(element("NarrativeText", "t", meta(7)));

        assertThat(units.get(0).provenance().page()).isEqualTo(7);
    }

    @Test
    void assignsPageZeroWhenAbsent() {
        List<DocumentUnit> units = map(element("NarrativeText", "t", meta(null)));

        assertThat(units.get(0).provenance().page()).isZero();
    }

    @Test
    void assignsPageZeroWhenMetadataAbsent() {
        List<DocumentUnit> units = map(element("NarrativeText", "t", null));

        assertThat(units.get(0).provenance().page()).isZero();
    }

    @Test
    void assignsPageZeroWhenNegative() {
        List<DocumentUnit> units = map(element("NarrativeText", "t", meta(-3)));

        assertThat(units.get(0).provenance().page()).isZero();
    }

    // --- metadata ------------------------------------------------------------

    @Test
    void recordsLanguagesAndFiletype() {
        UnstructuredMetadata md =
                new UnstructuredMetadata(
                        1, null, List.of("eng", "fra"), null, null, "application/pdf", null);

        List<DocumentUnit> units = map(element("NarrativeText", "t", md));

        assertThat(units.get(0).metadata())
                .containsEntry("languages", "eng, fra")
                .containsEntry("filetype", "application/pdf");
    }

    @Test
    void mapsTitleWithoutMetadata() {
        List<DocumentUnit> units = map(element("Title", "Heading", null));

        assertThat(units)
                .singleElement()
                .isInstanceOfSatisfying(
                        Heading.class, h -> assertThat(h.metadata()).doesNotContainKey("level"));
    }

    @Test
    void omitsBlankOrNullMetadataValues() {
        JsonNode nullCoordinates = new ObjectMapper().nullNode();
        UnstructuredMetadata md =
                new UnstructuredMetadata(1, null, List.of(), null, null, "  ", nullCoordinates);

        List<DocumentUnit> units = map(element("NarrativeText", "t", md));

        assertThat(units.get(0).metadata()).isEmpty();
    }

    @Test
    void recordsCoordinatesAsString() throws Exception {
        JsonNode coordinates = new ObjectMapper().readTree("{\"system\":\"PixelSpace\"}");
        UnstructuredMetadata md =
                new UnstructuredMetadata(1, null, null, null, null, null, coordinates);

        List<DocumentUnit> units = map(element("NarrativeText", "t", md));

        assertThat(units.get(0).metadata().get("coordinates")).contains("PixelSpace");
    }

    // --- list handling -------------------------------------------------------

    @Test
    void returnsEmptyForNullElements() {
        assertThat(mapper.map(null)).isEmpty();
    }

    @Test
    void skipsNullElementsInTheList() {
        List<UnstructuredElement> elements = new ArrayList<>();
        elements.add(element("NarrativeText", "kept", meta(1)));
        elements.add(null);

        List<DocumentUnit> units = mapper.map(elements);

        assertThat(units).hasSize(1);
    }

    @Test
    void preservesOrderAndInvariantAcrossAMixedDocument() {
        UnstructuredMetadata tableMd =
                new UnstructuredMetadata(
                        1, "<table><tr><td>x</td></tr></table>", null, null, null, null, null);
        List<DocumentUnit> units =
                map(
                        element("Title", "Title", meta(1)),
                        element("NarrativeText", "Para", meta(1)),
                        element("ListItem", "Item", meta(1)),
                        element("Table", "x", tableMd),
                        element("Image", "Caption", meta(1)),
                        element("PageBreak", null, meta(2)));

        assertThat(units).hasSize(6);
        assertThat(units.get(0)).isInstanceOf(Heading.class);
        assertThat(units.get(1)).isInstanceOf(Paragraph.class);
        assertThat(units.get(2)).isInstanceOf(ListItem.class);
        assertThat(units.get(3)).isInstanceOf(Table.class);
        assertThat(units.get(4)).isInstanceOf(Image.class);
        assertThat(units.get(5)).isInstanceOf(PageBreak.class);
        assertInvariant(units);
    }
}
