package io.github.semanticchunker.chunker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.Provenance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SemanticChunkTest {

    private static DocumentUnit paragraph(int ordinal) {
        return new Paragraph(
                new Provenance(ordinal, 1, ordinal, ordinal + 1), "u" + ordinal, Map.of());
    }

    @Test
    void exposesItsUnits() {
        List<DocumentUnit> units = List.of(paragraph(0), paragraph(1));

        assertThat(new SemanticChunk(units).units()).containsExactlyElementsOf(units);
    }

    @Test
    void defensivelyCopiesUnitsAndExposesThemUnmodifiable() {
        List<DocumentUnit> units = new ArrayList<>();
        units.add(paragraph(0));

        SemanticChunk chunk = new SemanticChunk(units);
        units.add(paragraph(1));

        assertThat(chunk.units()).hasSize(1);
        assertThatThrownBy(() -> chunk.units().add(paragraph(2)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullUnits() {
        assertThatNullPointerException().isThrownBy(() -> new SemanticChunk(null));
    }

    @Test
    void hasValueEquality() {
        SemanticChunk one = new SemanticChunk(List.of(paragraph(0)));
        SemanticChunk same = new SemanticChunk(List.of(paragraph(0)));

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    }
}
