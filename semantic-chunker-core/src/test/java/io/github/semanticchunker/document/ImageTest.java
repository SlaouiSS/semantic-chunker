package io.github.semanticchunker.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ImageTest {

    private static Provenance provenance() {
        return new Provenance(4, 2, 0, 0);
    }

    @Test
    void exposesCaptionAsText() {
        Image image = new Image(provenance(), "a diagram", Map.of("k", "v"));

        assertThat(image.text()).contains("a diagram");
        assertThat(image.provenance()).isEqualTo(provenance());
        assertThat(image.metadata()).containsEntry("k", "v");
    }

    @Test
    void hasNoTextWhenCaptionIsAbsent() {
        Image image = new Image(provenance(), null, Map.of());

        assertThat(image.text()).isEmpty();
    }

    @Test
    void treatsNullMetadataAsEmpty() {
        assertThat(new Image(provenance(), "c", null).metadata()).isEmpty();
    }

    @Test
    void rejectsNullProvenance() {
        assertThatNullPointerException().isThrownBy(() -> new Image(null, "c", Map.of()));
    }

    @Test
    void hasValueEquality() {
        Image one = new Image(provenance(), "c", Map.of());
        Image same = new Image(provenance(), "c", Map.of());

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    }
}
