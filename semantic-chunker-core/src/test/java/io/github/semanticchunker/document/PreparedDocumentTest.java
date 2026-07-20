package io.github.semanticchunker.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PreparedDocumentTest {

    private static DocumentUnit paragraph(int ordinal) {
        return new Paragraph(
                new Provenance(ordinal, 1, ordinal, ordinal + 1), "u" + ordinal, Map.of());
    }

    @Test
    void exposesItsUnitsAndMetadata() {
        List<DocumentUnit> units = List.of(paragraph(0), paragraph(1));

        PreparedDocument document = new PreparedDocument(units, Map.of("title", "Doc"));

        assertThat(document.units()).containsExactlyElementsOf(units);
        assertThat(document.metadata()).containsEntry("title", "Doc");
    }

    @Test
    void allowsAnEmptyDocument() {
        assertThat(new PreparedDocument(List.of(), Map.of()).units()).isEmpty();
    }

    @Test
    void treatsNullMetadataAsEmpty() {
        assertThat(new PreparedDocument(List.of(), null).metadata()).isEmpty();
    }

    @Test
    void defensivelyCopiesUnitsAndExposesThemUnmodifiable() {
        List<DocumentUnit> units = new ArrayList<>();
        units.add(paragraph(0));

        PreparedDocument document = new PreparedDocument(units, Map.of());
        units.add(paragraph(1));

        assertThat(document.units()).hasSize(1);
        assertThatThrownBy(() -> document.units().add(paragraph(9)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void defensivelyCopiesMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("a", "b");

        PreparedDocument document = new PreparedDocument(List.of(), metadata);
        metadata.put("c", "d");

        assertThat(document.metadata()).hasSize(1).containsEntry("a", "b");
    }

    @Test
    void rejectsNullUnits() {
        assertThatNullPointerException().isThrownBy(() -> new PreparedDocument(null, Map.of()));
    }

    @Test
    void hasValueEquality() {
        PreparedDocument one = new PreparedDocument(List.of(paragraph(0)), Map.of("k", "v"));
        PreparedDocument same = new PreparedDocument(List.of(paragraph(0)), Map.of("k", "v"));

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    }
}
