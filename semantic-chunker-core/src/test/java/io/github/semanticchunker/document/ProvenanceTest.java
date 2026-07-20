package io.github.semanticchunker.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ProvenanceTest {

    @Test
    void exposesItsComponents() {
        Provenance provenance = new Provenance(7, 3, 100, 220);

        assertThat(provenance.globalOrdinal()).isEqualTo(7);
        assertThat(provenance.page()).isEqualTo(3);
        assertThat(provenance.startOffset()).isEqualTo(100);
        assertThat(provenance.endOffset()).isEqualTo(220);
    }

    @Test
    void allowsAZeroLengthSpan() {
        assertThat(new Provenance(0, 0, 42, 42).endOffset()).isEqualTo(42);
    }

    @Test
    void rejectsNegativeGlobalOrdinal() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Provenance(-1, 0, 0, 0))
                .withMessageContaining("globalOrdinal");
    }

    @Test
    void rejectsNegativePage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Provenance(0, -1, 0, 0))
                .withMessageContaining("page");
    }

    @Test
    void rejectsNegativeStartOffset() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Provenance(0, 0, -1, 0))
                .withMessageContaining("startOffset");
    }

    @Test
    void rejectsEndOffsetBeforeStartOffset() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Provenance(0, 0, 10, 9))
                .withMessageContaining("endOffset");
    }

    @Test
    void hasValueEquality() {
        Provenance one = new Provenance(1, 2, 3, 4);
        Provenance same = new Provenance(1, 2, 3, 4);
        Provenance other = new Provenance(1, 2, 3, 5);

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(other);
    }

    @Test
    void toStringNamesItsComponents() {
        assertThat(new Provenance(1, 2, 3, 4).toString()).contains("globalOrdinal");
    }
}
