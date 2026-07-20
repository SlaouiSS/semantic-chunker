package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Heading;
import io.github.semanticchunker.document.Image;
import io.github.semanticchunker.document.ListItem;
import io.github.semanticchunker.document.PageBreak;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.Provenance;
import io.github.semanticchunker.document.Table;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestRepresentationTest {

    private final RequestRepresentation representation = new RequestRepresentation();

    private static Provenance provenance(int ordinal) {
        return new Provenance(ordinal, 1, ordinal, ordinal + 1);
    }

    // ---- single-unit rendering: ordinal associated with textual representation (§5) ----

    @Test
    void rendersAHeadingAsItsOrdinalAndText() {
        Heading heading = new Heading(provenance(0), "Chapter One", Map.of());

        assertThat(representation.renderUnit(heading)).isEqualTo("[unit 0]\nChapter One");
    }

    @Test
    void rendersAParagraphAsItsOrdinalAndText() {
        Paragraph paragraph = new Paragraph(provenance(1), "Some prose.", Map.of());

        assertThat(representation.renderUnit(paragraph)).isEqualTo("[unit 1]\nSome prose.");
    }

    @Test
    void rendersAListItemAsItsOrdinalAndText() {
        ListItem listItem = new ListItem(provenance(2), "A point", Map.of());

        assertThat(representation.renderUnit(listItem)).isEqualTo("[unit 2]\nA point");
    }

    @Test
    void rendersATableAsItsCanonicalMarkdownSerialization() {
        Table table =
                new Table(
                        provenance(3),
                        "| a | b |\n| 1 | 2 |",
                        List.of(List.of("a", "b")),
                        Map.of());

        assertThat(representation.renderUnit(table)).isEqualTo("[unit 3]\n| a | b |\n| 1 | 2 |");
    }

    @Test
    void rendersAnImageWithACaptionAsItsCaption() {
        Image image = new Image(provenance(4), "a diagram", Map.of());

        assertThat(representation.renderUnit(image)).isEqualTo("[unit 4]\na diagram");
    }

    @Test
    void rendersAnImageWithoutACaptionAsAnEmptyRepresentation() {
        Image image = new Image(provenance(5), null, Map.of());

        assertThat(representation.renderUnit(image)).isEqualTo("[unit 5]\n");
    }

    @Test
    void rendersAPageBreakAsAnEmptyRepresentation() {
        PageBreak pageBreak = new PageBreak(provenance(6), Map.of());

        assertThat(representation.renderUnit(pageBreak)).isEqualTo("[unit 6]\n");
    }

    @Test
    void neverIncludesProvenanceInternalsOrMetadata() {
        Heading heading =
                new Heading(new Provenance(3, 99, 1000, 2000), "Title", Map.of("author", "secret"));

        String rendered = representation.renderUnit(heading);

        assertThat(rendered).isEqualTo("[unit 3]\nTitle");
        assertThat(rendered).doesNotContain("99").doesNotContain("1000").doesNotContain("2000");
        assertThat(rendered).doesNotContain("author").doesNotContain("secret");
    }

    @Test
    void rendersOrdinalsWithoutLocaleGroupingSeparators() {
        Paragraph paragraph = new Paragraph(provenance(1_000_000), "x", Map.of());

        assertThat(representation.renderUnit(paragraph))
                .isEqualTo("[unit 1000000]\nx")
                .doesNotContain("1,000,000");
    }

    @Test
    void rejectsNullUnit() {
        assertThatNullPointerException().isThrownBy(() -> representation.renderUnit(null));
    }

    // ---- content representation of a run of units (§4, §5) ----

    @Test
    void rendersContentAsUnitsInOrderSeparatedByLineBreaks() {
        DocumentUnit first = new Heading(provenance(0), "H", Map.of());
        DocumentUnit second = new Paragraph(provenance(1), "P", Map.of());

        assertThat(representation.renderContent(List.of(first, second)))
                .isEqualTo("[unit 0]\nH\n[unit 1]\nP");
    }

    @Test
    void preservesTheGivenDocumentOrderOfUnits() {
        DocumentUnit u2 = new Paragraph(provenance(2), "c", Map.of());
        DocumentUnit u0 = new Paragraph(provenance(0), "a", Map.of());
        DocumentUnit u1 = new Paragraph(provenance(1), "b", Map.of());

        // The renderer preserves the order it is given; it neither sorts nor reorders.
        assertThat(representation.renderContent(List.of(u0, u1, u2)))
                .isEqualTo("[unit 0]\na\n[unit 1]\nb\n[unit 2]\nc");
    }

    @Test
    void rendersNoUnitsAsTheEmptyString() {
        assertThat(representation.renderContent(List.of())).isEmpty();
    }

    @Test
    void rendersASingleUnitContentWithoutASeparator() {
        DocumentUnit only = new Paragraph(provenance(7), "solo", Map.of());

        assertThat(representation.renderContent(List.of(only))).isEqualTo("[unit 7]\nsolo");
    }

    @Test
    void rejectsNullContentList() {
        assertThatNullPointerException().isThrownBy(() -> representation.renderContent(null));
    }

    // ---- canonical response serialization (§8) ----

    @Test
    void serializesBoundaryOrdinalsAsAListOfIntegers() {
        assertThat(representation.renderResponse(List.of(1, 3, 5))).isEqualTo("[1,3,5]");
    }

    @Test
    void serializesASingleBoundary() {
        assertThat(representation.renderResponse(List.of(7))).isEqualTo("[7]");
    }

    @Test
    void serializesNoBoundariesAsAnEmptyList() {
        assertThat(representation.renderResponse(List.of())).isEqualTo("[]");
    }

    @Test
    void serializesBoundariesInTheGivenOrderWithoutReordering() {
        assertThat(representation.renderResponse(List.of(5, 2, 9))).isEqualTo("[5,2,9]");
    }

    @Test
    void rejectsNullResponseList() {
        assertThatNullPointerException().isThrownBy(() -> representation.renderResponse(null));
    }

    // ---- maximal valid response (WINDOW_PLANNING §1, §8) ----

    @Test
    void maximalResponseNamesEveryUnitExceptTheFirstByGlobalOrdinal() {
        DocumentUnit u5 = new Paragraph(provenance(5), "a", Map.of());
        DocumentUnit u8 = new Paragraph(provenance(8), "b", Map.of());
        DocumentUnit u13 = new Paragraph(provenance(13), "c", Map.of());

        assertThat(representation.renderMaximalResponse(List.of(u5, u8, u13))).isEqualTo("[8,13]");
    }

    @Test
    void maximalResponseOfASingleUnitIsEmpty() {
        DocumentUnit only = new Paragraph(provenance(4), "x", Map.of());

        assertThat(representation.renderMaximalResponse(List.of(only))).isEqualTo("[]");
    }

    @Test
    void maximalResponseOfNoUnitsIsEmpty() {
        assertThat(representation.renderMaximalResponse(List.of())).isEqualTo("[]");
    }

    @Test
    void maximalResponseOfTwoUnitsNamesOnlyTheSecond() {
        DocumentUnit first = new Paragraph(provenance(0), "a", Map.of());
        DocumentUnit second = new Paragraph(provenance(1), "b", Map.of());

        assertThat(representation.renderMaximalResponse(List.of(first, second))).isEqualTo("[1]");
    }

    @Test
    void rejectsNullMaximalResponseList() {
        assertThatNullPointerException()
                .isThrownBy(() -> representation.renderMaximalResponse(null));
    }

    // ---- determinism (§10; no locale, randomness, or environment dependence) ----

    @Test
    void producesIdenticalOutputForIdenticalInputs() {
        List<DocumentUnit> units =
                List.of(
                        new Heading(provenance(0), "H", Map.of()),
                        new Paragraph(provenance(1), "P", Map.of()));

        assertThat(representation.renderContent(units))
                .isEqualTo(representation.renderContent(units));
        assertThat(representation.renderMaximalResponse(units))
                .isEqualTo(representation.renderMaximalResponse(units));
    }

    @Test
    void isStatelessSoDistinctInstancesProduceIdenticalOutput() {
        List<DocumentUnit> units = List.of(new Paragraph(provenance(0), "P", Map.of()));

        RequestRepresentation other = new RequestRepresentation();

        assertThat(new RequestRepresentation().renderContent(units))
                .isEqualTo(other.renderContent(units));
    }
}
