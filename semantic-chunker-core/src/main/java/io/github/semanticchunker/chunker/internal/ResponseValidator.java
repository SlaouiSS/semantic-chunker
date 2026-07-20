package io.github.semanticchunker.chunker.internal;

import io.github.semanticchunker.chunker.internal.ValidationOutcome.RejectionReason;
import io.github.semanticchunker.model.ModelResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Confirms that a raw model response is well-formed and refers to genuine units of the document,
 * turning it into a {@link ValidatedBoundaryDecision} or rejecting it with a classification
 * (RESPONSE_VALIDATION.md).
 *
 * <p>This stage is the guardian that ensures only trustworthy decisions proceed to merging. It
 * parses the response, validates it against the window, and normalizes an accepted response into a
 * sorted, duplicate-free set of boundaries. It never retries, degrades, or records warnings; it
 * only produces a {@link ValidationOutcome}. Recovery — deciding what to do with a rejection — is a
 * later stage.
 *
 * <p>Validation is a pure, deterministic function of the raw response and the window (§13): the
 * same response and window always yield the same outcome. It never modifies its inputs.
 */
class ResponseValidator {

    /**
     * The canonical response shape validation parses: an ordered list of integers written as {@code
     * [n,n,...]} or {@code []}. These tokens mirror the serialization the request states the model
     * must produce (PROMPT_SPECIFICATION.md §8); the request's stated output shape and the shape
     * parsed here are the same.
     */
    private static final char LIST_OPEN = '[';

    private static final char LIST_CLOSE = ']';
    private static final String SEPARATOR = ",";

    ResponseValidator() {}

    /**
     * Validates one window's raw response.
     *
     * @param response the model's raw response for the window; never {@code null}
     * @param window the window the response was obtained for; never {@code null}
     * @return an accepted, normalized decision, or a classified rejection
     */
    ValidationOutcome validate(ModelResponse response, Window window) {
        String raw = response.rawText();
        if (raw.isBlank()) {
            return new ValidationOutcome.Rejected(RejectionReason.MISSING_COLLECTION);
        }

        int open = raw.indexOf(LIST_OPEN);
        if (open < 0) {
            return new ValidationOutcome.Rejected(RejectionReason.MALFORMED);
        }
        int close = raw.indexOf(LIST_CLOSE, open + 1);
        if (close < 0) {
            return new ValidationOutcome.Rejected(RejectionReason.MALFORMED);
        }

        String inner = raw.substring(open + 1, close).strip();
        List<Integer> ordinals = new ArrayList<>();
        if (!inner.isEmpty()) {
            for (String token : inner.split(SEPARATOR, -1)) {
                Integer parsed = parseInteger(token.strip());
                if (parsed == null) {
                    return new ValidationOutcome.Rejected(RejectionReason.NON_INTEGER_ELEMENT);
                }
                ordinals.add(parsed);
            }
        }

        Set<Integer> windowOrdinals = windowOrdinals(window);
        for (int ordinal : ordinals) {
            if (!windowOrdinals.contains(ordinal)) {
                return new ValidationOutcome.Rejected(RejectionReason.OUT_OF_RANGE_ELEMENT);
            }
        }

        if (window.containsDocumentFirstUnit()) {
            int documentFirstOrdinal = window.units().get(0).provenance().globalOrdinal();
            for (int ordinal : ordinals) {
                if (ordinal == documentFirstOrdinal) {
                    return new ValidationOutcome.Rejected(RejectionReason.FIRST_UNIT_ELEMENT);
                }
            }
        }

        List<Integer> normalized = ordinals.stream().distinct().sorted().toList();
        return new ValidationOutcome.Accepted(new ValidatedBoundaryDecision(normalized));
    }

    private static Integer parseInteger(String token) {
        if (token.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(token);
        } catch (NumberFormatException notAnInteger) {
            return null;
        }
    }

    private static Set<Integer> windowOrdinals(Window window) {
        return window.units().stream()
                .map(unit -> unit.provenance().globalOrdinal())
                .collect(Collectors.toSet());
    }
}
