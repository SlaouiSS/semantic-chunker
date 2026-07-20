package io.github.semanticchunker.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PageBreakTest {

    private static Provenance provenance() {
        return new Provenance(9, 3, 500, 500);
    }

    @Test
    void neverHasText() {
        PageBreak pageBreak = new PageBreak(provenance(), Map.of("k", "v"));

        assertThat(pageBreak.text()).isEmpty();
        assertThat(pageBreak.provenance()).isEqualTo(provenance());
        assertThat(pageBreak.metadata()).containsEntry("k", "v");
    }

    @Test
    void treatsNullMetadataAsEmpty() {
        assertThat(new PageBreak(provenance(), null).metadata()).isEmpty();
    }

    @Test
    void rejectsNullProvenance() {
        assertThatNullPointerException().isThrownBy(() -> new PageBreak(null, Map.of()));
    }

    @Test
    void hasValueEquality() {
        PageBreak one = new PageBreak(provenance(), Map.of());
        PageBreak same = new PageBreak(provenance(), Map.of());

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    }
}
