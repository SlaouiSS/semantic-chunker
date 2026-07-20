package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.semanticchunker.chunker.internal.ValidationOutcome.RejectionReason;
import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.Provenance;
import io.github.semanticchunker.model.ModelResponse;
import io.github.semanticchunker.model.TokenUsage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Behavioral tests for the response-validation stage (RESPONSE_VALIDATION.md). */
class ResponseValidatorTest {

    private final ResponseValidator validator = new ResponseValidator();

    private static DocumentUnit unit(int ordinal) {
        return new Paragraph(
                new Provenance(ordinal, 1, ordinal, ordinal + 1), "u" + ordinal, Map.of());
    }

    private static Window window(boolean containsDocumentFirstUnit, int... ordinals) {
        List<DocumentUnit> units = new ArrayList<>();
        for (int ordinal : ordinals) {
            units.add(unit(ordinal));
        }
        return new Window(units, false, containsDocumentFirstUnit);
    }

    private ValidationOutcome validate(String raw, Window window) {
        return validator.validate(new ModelResponse(raw, new TokenUsage(0, 0)), window);
    }

    private static ValidationOutcome accepted(int... boundaryOrdinals) {
        List<Integer> ordinals = Arrays.stream(boundaryOrdinals).boxed().toList();
        return new ValidationOutcome.Accepted(new ValidatedBoundaryDecision(ordinals));
    }

    private static ValidationOutcome rejected(RejectionReason reason) {
        return new ValidationOutcome.Rejected(reason);
    }

    // ---- accepted responses (§3) ----

    @Test
    void acceptsAnEmptyResponseAsAnEmptyDecision() {
        assertThat(validate("[]", window(false, 1, 2, 3))).isEqualTo(accepted());
    }

    @Test
    void acceptsASingleBoundary() {
        assertThat(validate("[2]", window(false, 1, 2, 3))).isEqualTo(accepted(2));
    }

    @Test
    void acceptsMultipleBoundaries() {
        assertThat(validate("[1,3,5]", window(false, 1, 2, 3, 4, 5))).isEqualTo(accepted(1, 3, 5));
    }

    @Test
    void acceptsAnAlreadyNormalizedResponseUnchanged() {
        assertThat(validate("[1,3,5]", window(false, 1, 2, 3, 4, 5))).isEqualTo(accepted(1, 3, 5));
    }

    @Test
    void acceptsAndStripsWhitespaceAroundElements() {
        assertThat(validate("[ 1 , 3 ]", window(false, 1, 2, 3))).isEqualTo(accepted(1, 3));
    }

    @Test
    void ignoresProseSurroundingTheStructuredResult() {
        assertThat(validate("The boundaries are [1,3].", window(false, 1, 2, 3)))
                .isEqualTo(accepted(1, 3));
    }

    // ---- normalization (§7, §8) ----

    @Test
    void normalizesDuplicateBoundariesByCollapsingThem() {
        assertThat(validate("[3,3,1]", window(false, 1, 2, 3))).isEqualTo(accepted(1, 3));
    }

    @Test
    void normalizesUnorderedBoundariesBySorting() {
        assertThat(validate("[5,1,3]", window(false, 1, 2, 3, 4, 5))).isEqualTo(accepted(1, 3, 5));
    }

    // ---- malformed / missing (§4 R1, R2; §9) ----

    @Test
    void rejectsABlankResponseAsMissingCollection() {
        assertThat(validate("", window(false, 1, 2)))
                .isEqualTo(rejected(RejectionReason.MISSING_COLLECTION));
        assertThat(validate("   ", window(false, 1, 2)))
                .isEqualTo(rejected(RejectionReason.MISSING_COLLECTION));
    }

    @Test
    void rejectsAResponseWithNoListAsMalformed() {
        assertThat(validate("no boundaries here", window(false, 1, 2)))
                .isEqualTo(rejected(RejectionReason.MALFORMED));
    }

    @Test
    void rejectsAnUnbalancedOrTruncatedListAsMalformed() {
        assertThat(validate("[1,3", window(false, 1, 2, 3)))
                .isEqualTo(rejected(RejectionReason.MALFORMED));
    }

    // ---- non-integer elements (§4 R3) ----

    @Test
    void rejectsANonIntegerElement() {
        assertThat(validate("[1,x,3]", window(false, 1, 2, 3)))
                .isEqualTo(rejected(RejectionReason.NON_INTEGER_ELEMENT));
    }

    @Test
    void rejectsADecimalElement() {
        assertThat(validate("[1.5]", window(false, 1, 2)))
                .isEqualTo(rejected(RejectionReason.NON_INTEGER_ELEMENT));
    }

    @Test
    void rejectsANullElement() {
        assertThat(validate("[null]", window(false, 1, 2)))
                .isEqualTo(rejected(RejectionReason.NON_INTEGER_ELEMENT));
    }

    @Test
    void rejectsAnEmptyElementFromAnExtraComma() {
        assertThat(validate("[1,,3]", window(false, 1, 2, 3)))
                .isEqualTo(rejected(RejectionReason.NON_INTEGER_ELEMENT));
        assertThat(validate("[1,3,]", window(false, 1, 2, 3)))
                .isEqualTo(rejected(RejectionReason.NON_INTEGER_ELEMENT));
    }

    // ---- out-of-range elements (§4 R4; §10) ----

    @Test
    void rejectsAnOrdinalNotPresentInTheWindow() {
        assertThat(validate("[9]", window(false, 1, 2, 3)))
                .isEqualTo(rejected(RejectionReason.OUT_OF_RANGE_ELEMENT));
    }

    @Test
    void rejectsANegativeOrdinalAsOutOfRange() {
        assertThat(validate("[-1]", window(false, 1, 2, 3)))
                .isEqualTo(rejected(RejectionReason.OUT_OF_RANGE_ELEMENT));
    }

    @Test
    void rejectsAZeroOrdinalNotPresentInTheWindow() {
        assertThat(validate("[0]", window(false, 1, 2, 3)))
                .isEqualTo(rejected(RejectionReason.OUT_OF_RANGE_ELEMENT));
    }

    @Test
    void rejectsTheWholeResponseWhenOnlySomeElementsAreOutOfRange() {
        assertThat(validate("[1,9]", window(false, 1, 2, 3)))
                .isEqualTo(rejected(RejectionReason.OUT_OF_RANGE_ELEMENT));
    }

    // ---- first-unit prohibition (§3 A4, §4 R5) ----

    @Test
    void rejectsTheDocumentFirstUnitWhenTheWindowContainsIt() {
        assertThat(validate("[0]", window(true, 0, 1, 2)))
                .isEqualTo(rejected(RejectionReason.FIRST_UNIT_ELEMENT));
    }

    @Test
    void acceptsANonFirstUnitInTheWindowThatContainsTheDocumentFirstUnit() {
        assertThat(validate("[1,2]", window(true, 0, 1, 2))).isEqualTo(accepted(1, 2));
    }

    @Test
    void acceptsAWindowsFirstUnitWhenItIsNotTheDocumentFirstUnit() {
        // A non-first window: its first unit (ordinal 2) is a valid boundary (PROMPT_SPECIFICATION
        // §6).
        assertThat(validate("[2]", window(false, 2, 3, 4))).isEqualTo(accepted(2));
    }

    @Test
    void acceptsOrdinalZeroWhenItsWindowDoesNotContainTheDocumentFirstUnit() {
        // The rule keys on the window flag, not on the literal value 0.
        assertThat(validate("[0]", window(false, 0, 1, 2))).isEqualTo(accepted(0));
    }

    // ---- determinism (§13) ----

    @Test
    void isDeterministic() {
        Window window = window(false, 1, 2, 3, 4, 5);

        assertThat(validate("[5,1,3,3]", window)).isEqualTo(validate("[5,1,3,3]", window));
        assertThat(validate("[9]", window)).isEqualTo(validate("[9]", window));
    }
}
