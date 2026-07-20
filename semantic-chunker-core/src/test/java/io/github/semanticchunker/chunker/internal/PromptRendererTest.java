package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.Provenance;
import io.github.semanticchunker.model.ChunkingModel;
import io.github.semanticchunker.model.ChunkingRequest;
import io.github.semanticchunker.model.ModelResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Behavioral tests for the prompt-construction stage (PROMPT_SPECIFICATION.md). The model is a
 * controlled fake whose {@code execute} fails the test if it is ever called, proving prompt
 * construction never executes the model.
 */
class PromptRendererTest {

    private static final String SEPARATOR = "\n\n";

    private final RequestRepresentation representation = new RequestRepresentation();
    private final PromptRenderer renderer =
            new PromptRenderer(new LengthModel(), new RequestRepresentation());

    private static DocumentUnit unit(int ordinal, String text) {
        return new Paragraph(new Provenance(ordinal, 1, ordinal, ordinal + 1), text, Map.of());
    }

    private static Window window(DocumentUnit... units) {
        return new Window(List.of(units));
    }

    // ---- construction ----

    @Test
    void rejectsNullModel() {
        assertThatNullPointerException().isThrownBy(() -> new PromptRenderer(null, representation));
    }

    @Test
    void rejectsNullRepresentation() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PromptRenderer(new LengthModel(), null));
    }

    // ---- request assembly, content insertion, fixed instruction, response contract ----

    @Test
    void assemblesThePromptAsTheFixedInstructionThenTheCanonicalContent() {
        List<DocumentUnit> units = List.of(unit(0, "Alpha"), unit(1, "Beta"));

        ChunkingRequest request = renderer.render(new Window(units));

        String expected =
                PromptRenderer.INSTRUCTION + SEPARATOR + representation.renderContent(units);
        assertThat(request.prompt()).isEqualTo(expected);
    }

    @Test
    void thePromptBeginsWithTheFixedInstructionForEveryWindow() {
        assertThat(renderer.render(window(unit(0, "x"))).prompt())
                .startsWith(PromptRenderer.INSTRUCTION);
        assertThat(renderer.render(window(unit(0, "x"), unit(1, "y"))).prompt())
                .startsWith(PromptRenderer.INSTRUCTION);
    }

    @Test
    void thePromptEndsWithTheCanonicalContentRenderingReusingRequestRepresentation() {
        List<DocumentUnit> units = List.of(unit(0, "a"), unit(1, "b"), unit(2, "c"));

        ChunkingRequest request = renderer.render(new Window(units));

        assertThat(request.prompt()).endsWith(representation.renderContent(units));
    }

    @Test
    void preservesUnitOrderInTheRenderedContent() {
        List<DocumentUnit> units = List.of(unit(0, "a"), unit(1, "b"), unit(2, "c"));

        ChunkingRequest request = renderer.render(new Window(units));

        assertThat(request.prompt()).endsWith("[unit 0]\na\n[unit 1]\nb\n[unit 2]\nc");
    }

    // ---- generation hints ----

    @Test
    void setsALowTemperature() {
        assertThat(renderer.render(window(unit(0, "x"), unit(1, "y"))).temperature())
                .isEqualTo(0.0d);
    }

    @Test
    void omitsTheOptionalResponseShapeHint() {
        assertThat(renderer.render(window(unit(0, "x"), unit(1, "y"))).responseShape()).isEmpty();
    }

    @Test
    void setsTheOutputTokenBudgetToTheEstimatedSizeOfTheMaximalResponse() {
        List<DocumentUnit> units = List.of(unit(0, "a"), unit(1, "b"));

        ChunkingRequest request = renderer.render(new Window(units));

        // The LengthModel estimates tokens as character count; the maximal response for [0,1] is
        // "[1]".
        assertThat(request.maxOutputTokens())
                .isEqualTo(representation.renderMaximalResponse(units).length());
    }

    // ---- determinism ----

    @Test
    void producesByteForByteIdenticalRequestsForIdenticalWindows() {
        List<DocumentUnit> units = List.of(unit(0, "Alpha"), unit(1, "Beta"), unit(2, "Gamma"));

        ChunkingRequest first = renderer.render(new Window(units));
        ChunkingRequest second = renderer.render(new Window(units));

        assertThat(second).isEqualTo(first);
        assertThat(second.prompt()).isEqualTo(first.prompt());
    }

    // ---- document-size cases ----

    @Test
    void aSingleUnitWindowStillProducesARequest() {
        List<DocumentUnit> units = List.of(unit(0, "only"));

        ChunkingRequest request = renderer.render(new Window(units));

        assertThat(request.prompt())
                .isEqualTo(
                        PromptRenderer.INSTRUCTION
                                + SEPARATOR
                                + representation.renderContent(units));
        // The maximal response of a single-unit window is "[]"; its estimated size is positive.
        assertThat(request.maxOutputTokens())
                .isEqualTo(representation.renderMaximalResponse(units).length());
    }

    @Test
    void aLargeWindowRendersEveryUnitInOrder() {
        List<DocumentUnit> units = new ArrayList<>();
        for (int ordinal = 0; ordinal < 200; ordinal++) {
            units.add(unit(ordinal, "unit-" + ordinal));
        }

        ChunkingRequest request = renderer.render(new Window(units));

        assertThat(request.prompt())
                .isEqualTo(
                        PromptRenderer.INSTRUCTION
                                + SEPARATOR
                                + representation.renderContent(units));
    }

    // ---- the model is never executed ----

    @Test
    void neverExecutesTheModel() {
        // LengthModel.execute throws; a successful render proves execute was not called.
        List<DocumentUnit> units = List.of(unit(0, "a"), unit(1, "b"));

        ChunkingRequest request =
                new PromptRenderer(new LengthModel(), representation).render(new Window(units));

        assertThat(request).isNotNull();
    }

    /**
     * A deterministic fake whose token estimate is the character count and whose execute must never
     * run.
     */
    private static final class LengthModel implements ChunkingModel {
        @Override
        public ModelResponse execute(ChunkingRequest request) {
            throw new AssertionError("prompt construction must not execute the model");
        }

        @Override
        public int maxInputTokens() {
            return 1_000;
        }

        @Override
        public int estimateTokens(String text) {
            return text.length();
        }
    }
}
