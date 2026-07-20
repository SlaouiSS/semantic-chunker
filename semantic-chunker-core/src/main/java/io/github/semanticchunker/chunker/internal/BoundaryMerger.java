package io.github.semanticchunker.chunker.internal;

import io.github.semanticchunker.document.DocumentUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reconciles the validated per-window decisions into a single coherent set of boundaries over the
 * whole document, resolving disagreements within overlapping regions (ARCHITECTURE.md, section 4,
 * "Boundary merging"; PIPELINE.md, section 3; BOUNDARY_MERGING.md).
 *
 * <p>This is the most delicate stage in the pipeline. It depends absolutely on the stable identity
 * of units — the global ordinals — which is what lets it recognize decisions from different windows
 * as referring to the same places (BOUNDARY_MERGING.md §9).
 *
 * <p>Every candidate position — the position before some unit {@code u}, named by {@code u}'s
 * global ordinal — that at least one window reports is decided exactly once, by its
 * <em>authoritative window</em>: among the windows that present {@code u}, the one in which the
 * position sits with the most balanced context on both sides (greatest centeredness), ties broken
 * toward the earliest window (BOUNDARY_MERGING.md §4). The position becomes a merged boundary if
 * and only if that window reports it. This resolves duplicates, agreement, disagreement, and
 * overlap uniformly (§4–§7).
 *
 * <p>Merging is a pure, total, deterministic function of the windows and their validated decisions
 * (§11, §12): it never validates, executes, retries, or modifies its inputs, and it produces the
 * identical merged set for identical inputs regardless of the order positions are examined.
 */
class BoundaryMerger {

    BoundaryMerger() {}

    /**
     * Merges the per-window decisions into one set of boundaries over the whole document.
     *
     * @param windowDecisions each window paired with its validated decision, in window order; never
     *     {@code null}
     * @return the reconciled, ascending, duplicate-free boundaries over the whole document
     * @throws IllegalStateException if a decision reports a unit its window does not present — an
     *     upstream validation guarantee (RESPONSE_VALIDATION.md §12) that merging assumes, whose
     *     violation is an internal defect and a terminal failure (BOUNDARY_MERGING.md §12)
     */
    MergedBoundaries merge(List<WindowDecision> windowDecisions) {
        Objects.requireNonNull(windowDecisions, "windowDecisions");

        List<CoveringWindow> coveringWindows =
                windowDecisions.stream().map(CoveringWindow::of).toList();

        // §4: consider every position reported by at least one window, each exactly once (M4).
        // A sorted set gives ascending, duplicate-free candidates independent of examination order.
        Set<Integer> candidatePositions = new TreeSet<>();
        for (CoveringWindow coveringWindow : coveringWindows) {
            candidatePositions.addAll(coveringWindow.reportedPositions());
        }

        List<Integer> mergedBoundaries = new ArrayList<>();
        for (int position : candidatePositions) {
            if (isCutAt(position, coveringWindows)) {
                mergedBoundaries.add(position);
            }
        }
        return new MergedBoundaries(mergedBoundaries);
    }

    /**
     * Decides position {@code position} by its authoritative window (§4): the covering window of
     * greatest centeredness, ties broken toward the window whose first unit is earliest in document
     * order. The merged set contains a cut here iff that window reports it.
     *
     * <p>The position is drawn from some window's reported cuts, and every reported cut is covered
     * by its own window (enforced in {@link CoveringWindow#of}), so a covering window always
     * exists.
     */
    private static boolean isCutAt(int position, List<CoveringWindow> coveringWindows) {
        CoveringWindow authoritativeWindow =
                coveringWindows.stream()
                        .filter(coveringWindow -> coveringWindow.covers(position))
                        .reduce(
                                (earlier, later) ->
                                        later.isMoreAuthoritativeThan(earlier, position)
                                                ? later
                                                : earlier)
                        .orElseThrow();
        return authoritativeWindow.reports(position);
    }

    /**
     * One window prepared for merging: its coverage indexed by global ordinal, plus the positions
     * it reports as cuts. Encapsulates covering, centeredness (§1), and the authoritative-window
     * comparison (§4) for a single window so the merge stays a plain sweep over positions.
     */
    private static final class CoveringWindow {

        private final int firstOrdinal;
        private final int coverageSize;
        private final Map<Integer, Integer> indexByOrdinal;
        private final Set<Integer> reportedPositions;

        private CoveringWindow(
                int firstOrdinal,
                int coverageSize,
                Map<Integer, Integer> indexByOrdinal,
                Set<Integer> reportedPositions) {
            this.firstOrdinal = firstOrdinal;
            this.coverageSize = coverageSize;
            this.indexByOrdinal = indexByOrdinal;
            this.reportedPositions = reportedPositions;
        }

        static CoveringWindow of(WindowDecision windowDecision) {
            List<DocumentUnit> units = windowDecision.window().units();
            Map<Integer, Integer> indexByOrdinal = new HashMap<>();
            for (int index = 0; index < units.size(); index++) {
                indexByOrdinal.put(units.get(index).provenance().globalOrdinal(), index);
            }
            Set<Integer> reportedPositions =
                    Set.copyOf(windowDecision.decision().boundaryOrdinals());
            for (int position : reportedPositions) {
                if (!indexByOrdinal.containsKey(position)) {
                    throw new IllegalStateException(
                            "decision reports a position its window does not present: "
                                    + position
                                    + "; the validation guarantee (RESPONSE_VALIDATION.md §12) that"
                                    + " merging assumes was violated upstream");
                }
            }
            int firstOrdinal = units.get(0).provenance().globalOrdinal();
            return new CoveringWindow(
                    firstOrdinal, units.size(), indexByOrdinal, reportedPositions);
        }

        /** Whether this window presents the unit the position precedes (§1). */
        boolean covers(int position) {
            return indexByOrdinal.containsKey(position);
        }

        /** Whether this window reports a cut at the position (§1). */
        boolean reports(int position) {
            return reportedPositions.contains(position);
        }

        Set<Integer> reportedPositions() {
            return reportedPositions;
        }

        /**
         * The centeredness of a covered position (§1): the smaller of its left context (the units
         * before it in this window) and its right context (the units from it through the window's
         * last unit). Greater means more balanced context on both sides.
         */
        int centeredness(int position) {
            int index = indexByOrdinal.get(position);
            int leftContext = index;
            int rightContext = coverageSize - index;
            return Math.min(leftContext, rightContext);
        }

        /**
         * The total, deterministic authoritative-window order at a position (§4, step 2): greater
         * centeredness wins; on a tie, the earlier window (smaller first-unit global ordinal) wins.
         * First-unit ordinals are unique across windows, so the order is total.
         */
        boolean isMoreAuthoritativeThan(CoveringWindow other, int position) {
            int centerednessDifference = centeredness(position) - other.centeredness(position);
            if (centerednessDifference != 0) {
                return centerednessDifference > 0;
            }
            return firstOrdinal < other.firstOrdinal;
        }
    }
}
