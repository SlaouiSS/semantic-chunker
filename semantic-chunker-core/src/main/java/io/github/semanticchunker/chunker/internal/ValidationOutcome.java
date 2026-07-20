package io.github.semanticchunker.chunker.internal;

import java.util.Objects;

/**
 * The outcome of validating one window's raw model response: either an accepted, normalized
 * boundary decision, or a rejection carrying its classification (RESPONSE_VALIDATION.md §1, §14).
 *
 * <p>A rejection is a normal, recoverable, window-local outcome — not an exception. Validation
 * itself neither retries nor degrades; it accepts or rejects with a reason, and the recovery path
 * (a later stage) decides what to do with a rejection.
 */
sealed interface ValidationOutcome permits ValidationOutcome.Accepted, ValidationOutcome.Rejected {

    /**
     * The reason a response was rejected (RESPONSE_VALIDATION.md §4). Every rejection is
     * window-local and recoverable (§14).
     */
    enum RejectionReason {
        /** R1: the response could not be parsed into the required list of integers (§4, §9). */
        MALFORMED,
        /** R2: the response carried no list at all (§4, §5). */
        MISSING_COLLECTION,
        /** R3: the list contained an element that is not an integer (§4). */
        NON_INTEGER_ELEMENT,
        /** R4: an element named a unit the window did not present (§4, §10). */
        OUT_OF_RANGE_ELEMENT,
        /** R5: an element named the document's first unit, which is never a boundary (§4, §10). */
        FIRST_UNIT_ELEMENT
    }

    /**
     * An accepted response, carrying the normalized validated boundary decision.
     *
     * @param decision the validated, deduplicated, ascending boundary decision; never {@code null}
     */
    record Accepted(ValidatedBoundaryDecision decision) implements ValidationOutcome {
        public Accepted {
            Objects.requireNonNull(decision, "decision");
        }
    }

    /**
     * A rejected response, carrying its classification.
     *
     * @param reason why the response was rejected; never {@code null}
     */
    record Rejected(RejectionReason reason) implements ValidationOutcome {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
