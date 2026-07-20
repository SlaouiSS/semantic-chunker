package io.github.semanticchunker.chunker.internal;

import java.util.Objects;

/**
 * One window paired with the validated boundary decision produced for it — the unit boundary
 * merging consumes (BOUNDARY_MERGING.md §2).
 *
 * <p>Merging reconciles overlapping windows, so it needs both halves together: the window supplies
 * the coverage against which centeredness and authoritative ownership are computed (§1, §4), and
 * the decision supplies the cuts that window reports. Pairing them makes the correspondence
 * explicit and keeps the two lists from drifting out of step.
 *
 * @param window the window a decision was produced for; never {@code null}
 * @param decision the validated boundary decision for that window (the empty decision for a
 *     degraded window); never {@code null}
 */
record WindowDecision(Window window, ValidatedBoundaryDecision decision) {

    /** Validates. */
    WindowDecision {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(decision, "decision");
    }
}
