package io.github.semanticchunker.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Behavior shared by the three text-bearing {@link DocumentUnit} kinds — {@link Heading}, {@link
 * Paragraph}, and {@link ListItem} — which have identical structure.
 */
class TextBearingUnitTest {

    @FunctionalInterface
    interface TextUnitFactory {
        DocumentUnit create(Provenance provenance, String content, Map<String, String> metadata);
    }

    static Stream<Arguments> factories() {
        return Stream.of(
                arguments("Heading", (TextUnitFactory) Heading::new),
                arguments("Paragraph", (TextUnitFactory) Paragraph::new),
                arguments("ListItem", (TextUnitFactory) ListItem::new));
    }

    private static Provenance provenance() {
        return new Provenance(0, 1, 0, 5);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void exposesTextProvenanceAndMetadata(String name, TextUnitFactory factory) {
        Provenance provenance = provenance();

        DocumentUnit unit = factory.create(provenance, "hello", Map.of("k", "v"));

        assertThat(unit.provenance()).isEqualTo(provenance);
        assertThat(unit.text()).contains("hello");
        assertThat(unit.metadata()).containsEntry("k", "v");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void treatsNullMetadataAsEmpty(String name, TextUnitFactory factory) {
        DocumentUnit unit = factory.create(provenance(), "text", null);

        assertThat(unit.metadata()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void defensivelyCopiesMetadataAndExposesItUnmodifiable(String name, TextUnitFactory factory) {
        Map<String, String> mutable = new HashMap<>();
        mutable.put("a", "b");

        DocumentUnit unit = factory.create(provenance(), "text", mutable);
        mutable.put("c", "d");

        assertThat(unit.metadata()).hasSize(1).containsEntry("a", "b");
        assertThatThrownBy(() -> unit.metadata().put("e", "f"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void rejectsNullProvenance(String name, TextUnitFactory factory) {
        assertThatNullPointerException().isThrownBy(() -> factory.create(null, "text", Map.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void rejectsNullContent(String name, TextUnitFactory factory) {
        assertThatNullPointerException()
                .isThrownBy(() -> factory.create(provenance(), null, Map.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void hasValueEquality(String name, TextUnitFactory factory) {
        DocumentUnit one = factory.create(provenance(), "same", Map.of("k", "v"));
        DocumentUnit same = factory.create(provenance(), "same", Map.of("k", "v"));

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    }
}
