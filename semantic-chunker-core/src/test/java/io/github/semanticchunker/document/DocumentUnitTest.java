package io.github.semanticchunker.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Behavior of the sealed {@link DocumentUnit} hierarchy seen through the common contract. */
class DocumentUnitTest {

    private static Provenance provenance() {
        return new Provenance(0, 1, 0, 3);
    }

    private static String kindOf(DocumentUnit unit) {
        // Exhaustive switch over the closed hierarchy: this must compile without a default branch,
        // which is the guarantee the sealed type provides to consumers.
        return switch (unit) {
            case Heading ignored -> "heading";
            case Paragraph ignored -> "paragraph";
            case ListItem ignored -> "listItem";
            case Table ignored -> "table";
            case Image ignored -> "image";
            case PageBreak ignored -> "pageBreak";
        };
    }

    @Test
    void everyKindIsReachableThroughAnExhaustiveSwitch() {
        List<DocumentUnit> units =
                List.of(
                        new Heading(provenance(), "h", Map.of()),
                        new Paragraph(provenance(), "p", Map.of()),
                        new ListItem(provenance(), "l", Map.of()),
                        new Table(provenance(), "t", List.of(), Map.of()),
                        new Image(provenance(), "i", Map.of()),
                        new PageBreak(provenance(), Map.of()));

        assertThat(units.stream().map(DocumentUnitTest::kindOf))
                .containsExactly("heading", "paragraph", "listItem", "table", "image", "pageBreak");
    }

    @Test
    void everyUnitExposesProvenanceTextAndMetadataThroughTheContract() {
        DocumentUnit textual = new Paragraph(provenance(), "body", Map.of("k", "v"));
        DocumentUnit nonTextual = new PageBreak(provenance(), Map.of());

        assertThat(textual.provenance()).isEqualTo(provenance());
        assertThat(textual.text()).contains("body");
        assertThat(textual.metadata()).containsEntry("k", "v");
        assertThat(nonTextual.text()).isEmpty();
    }
}
