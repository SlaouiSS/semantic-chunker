package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.Provenance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Behavioral tests for the boundary-merging stage (BOUNDARY_MERGING.md). */
class BoundaryMergerTest {

    private final BoundaryMerger merger = new BoundaryMerger();

    private static DocumentUnit unit(int ordinal) {
        return new Paragraph(
                new Provenance(ordinal, 1, ordinal, ordinal + 1), "u" + ordinal, Map.of());
    }

    /** A window presenting the given global ordinals, in document order. */
    private static Window window(int... ordinals) {
        List<DocumentUnit> units = new ArrayList<>();
        for (int ordinal : ordinals) {
            units.add(unit(ordinal));
        }
        return new Window(units);
    }

    /** A window paired with the positions (global ordinals) it reports as cuts. */
    private static WindowDecision wd(Window window, int... reported) {
        List<Integer> ordinals = new ArrayList<>();
        for (int ordinal : reported) {
            ordinals.add(ordinal);
        }
        return new WindowDecision(window, new ValidatedBoundaryDecision(ordinals));
    }

    private List<Integer> merge(WindowDecision... windowDecisions) {
        return merger.merge(List.of(windowDecisions)).boundaryOrdinals();
    }

    // ---- inputs ----

    @Test
    void rejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> merger.merge(null));
    }

    @Test
    void producesAnEmptySetWhenThereAreNoWindows() {
        assertThat(merge()).isEmpty();
    }

    @Test
    void producesAnEmptySetWhenEveryWindowReportsNoBoundaries() {
        assertThat(merge(wd(window(0, 1, 2)), wd(window(2, 3, 4)))).isEmpty();
    }

    // ---- single window (§4 with one covering window) ----

    @Test
    void keepsEveryBoundaryOfASingleWindow() {
        assertThat(merge(wd(window(0, 1, 2, 3, 4), 2, 4))).containsExactly(2, 4);
    }

    @Test
    void sortsTheBoundariesOfASingleWindowAscending() {
        assertThat(merge(wd(window(0, 1, 2, 3, 4), 4, 2))).containsExactly(2, 4);
    }

    // ---- multiple, non-overlapping windows ----

    @Test
    void mergesDisjointWindowsByUnioningTheirBoundaries() {
        assertThat(merge(wd(window(10, 11, 12), 11), wd(window(20, 21, 22), 21)))
                .containsExactly(11, 21);
    }

    @Test
    void ordersBoundariesAcrossWindowsInDocumentOrderRegardlessOfWindowOrder() {
        // The later window is presented first; the result is still ascending (§8).
        assertThat(merge(wd(window(20, 21, 22), 21), wd(window(10, 11, 12), 11)))
                .containsExactly(11, 21);
    }

    // ---- agreement over an overlap (§4, §6) ----

    @Test
    void keepsAPositionBothOverlappingWindowsAgreeOn() {
        // Overlap {2,3,4}; both report 3. It is decided once and kept.
        assertThat(merge(wd(window(0, 1, 2, 3, 4), 3), wd(window(2, 3, 4, 5, 6), 3)))
                .containsExactly(3);
    }

    @Test
    void collapsesADuplicatePositionReportedByTwoWindowsIntoASingleBoundary() {
        // The same global ordinal named by two windows denotes the same position (§6).
        List<Integer> merged = merge(wd(window(0, 1, 2, 3, 4), 3), wd(window(2, 3, 4, 5, 6), 3));
        assertThat(merged).containsExactly(3).doesNotHaveDuplicates();
    }

    // ---- authoritative window & centeredness (§1, §4) ----

    @Test
    void theMoreCentralWindowGovernsWhenItReportsTheCut() {
        // Position 2: central in the left window (centeredness 2), at the left edge of the right
        // window (centeredness 0). The left window is authoritative and reports it -> kept.
        assertThat(merge(wd(window(0, 1, 2, 3, 4), 2), wd(window(2, 3, 4, 5, 6))))
                .containsExactly(2);
    }

    @Test
    void theLessCentralWindowDoesNotOverrideASilentAuthoritativeWindow() {
        // Position 2: authoritative left window is silent; the less-central right window reports
        // it.
        // The authoritative window's silence governs -> dropped (§4, §5).
        assertThat(merge(wd(window(0, 1, 2, 3, 4)), wd(window(2, 3, 4, 5, 6), 2))).isEmpty();
    }

    @Test
    void leftOverlapPositionIsOwnedByTheLeftWindowWhereItIsCentral() {
        // Position 2 sits near the right window's left edge; the left window judges it with
        // balanced
        // context and owns it. Left reports -> kept; only right reports -> dropped.
        assertThat(merge(wd(window(0, 1, 2, 3, 4), 2), wd(window(2, 3, 4, 5, 6), 2)))
                .containsExactly(2);
        assertThat(merge(wd(window(0, 1, 2, 3, 4)), wd(window(2, 3, 4, 5, 6), 2))).isEmpty();
    }

    @Test
    void rightOverlapPositionIsOwnedByTheRightWindowWhereItIsCentral() {
        // Position 4 sits at the left window's right edge (centeredness 1) but is central in the
        // right window (centeredness 2). The right window owns it.
        assertThat(merge(wd(window(0, 1, 2, 3, 4)), wd(window(2, 3, 4, 5, 6), 4)))
                .containsExactly(4);
        assertThat(merge(wd(window(0, 1, 2, 3, 4), 4), wd(window(2, 3, 4, 5, 6)))).isEmpty();
    }

    @Test
    void aBoundaryAtOneWindowsEdgeIsDecidedByTheWindowThatCentersIt() {
        // Position 4 is the left window's last unit; the right window centers it and is
        // authoritative. Its judgement (report) is honored even though the left window is silent.
        assertThat(merge(wd(window(0, 1, 2, 3, 4), 4), wd(window(2, 3, 4, 5, 6), 4)))
                .containsExactly(4);
    }

    // ---- conflict resolution (§5) ----

    @Test
    void keepsAConflictedPositionWhenTheAuthoritativeWindowReportsIt() {
        // Position 3: authoritative left window reports it, right window does not -> kept.
        assertThat(merge(wd(window(0, 1, 2, 3, 4), 3), wd(window(2, 3, 4, 5, 6))))
                .containsExactly(3);
    }

    @Test
    void dropsAConflictedPositionTheAuthoritativeWindowRejects() {
        // Position 3: less-central right window reports it, authoritative left window does not.
        // Merging never keeps a boundary the authoritative window rejected (§5, M5).
        assertThat(merge(wd(window(0, 1, 2, 3, 4)), wd(window(2, 3, 4, 5, 6), 3))).isEmpty();
    }

    // ---- centeredness tie-break (§4 step 2) ----

    @Test
    void breaksACenterednessTieTowardTheEarliestWindow() {
        // Windows [0..4] and [1..5] both center position 3 (centeredness 2 each). The earliest
        // window (first unit 0) is authoritative. It is silent while the later window reports 3
        // -> dropped, proving the earliest window won the tie.
        assertThat(merge(wd(window(0, 1, 2, 3, 4)), wd(window(1, 2, 3, 4, 5), 3))).isEmpty();
        // Reversed report: the earliest window reports and owns it -> kept.
        assertThat(merge(wd(window(0, 1, 2, 3, 4), 3), wd(window(1, 2, 3, 4, 5))))
                .containsExactly(3);
    }

    @Test
    void theTieBreakIsIndependentOfTheOrderWindowsArePresented() {
        // Same tie as above, windows presented later-first: the earliest window still wins.
        assertThat(merge(wd(window(1, 2, 3, 4, 5), 3), wd(window(0, 1, 2, 3, 4)))).isEmpty();
    }

    // ---- a window that returns zero boundaries (§7) ----

    @Test
    void aSilentAuthoritativeWindowSuppressesACutOnlyWhereItIsAuthoritative() {
        // The left window is empty (a degraded or genuinely empty decision). It is authoritative
        // for
        // position 2 (central there) and suppresses it, but not for position 4 (edge there), which
        // the right window owns and reports.
        assertThat(merge(wd(window(0, 1, 2, 3, 4)), wd(window(2, 3, 4, 5, 6), 2, 4)))
                .containsExactly(4);
    }

    @Test
    void treatsAnEmptyWindowIdenticallyWhetherDegradedOrGenuinelyBoundaryFree() {
        // Both interpretations enter merging as the empty decision and behave identically (§7).
        List<Integer> merged = merge(wd(window(0, 1, 2, 3, 4)), wd(window(4, 5, 6, 7, 8), 6));
        assertThat(merged).containsExactly(6);
    }

    // ---- invariants (§10) ----

    @Test
    void neverInventsABoundaryNoWindowReported() {
        // M5: every output element was reported by some covering window. No window reports 1, so it
        // cannot appear; the reported-and-owned 3 does.
        assertThat(merge(wd(window(0, 1, 2, 3, 4), 3))).containsExactly(3).doesNotContain(1);
    }

    @Test
    void producesAStrictlyIncreasingDuplicateFreeSet() {
        // M2: sorted ascending, no duplicates, across overlapping and disjoint windows.
        List<Integer> merged =
                merge(
                        wd(window(0, 1, 2, 3, 4), 2, 3),
                        wd(window(2, 3, 4, 5, 6), 2, 3),
                        wd(window(10, 11, 12, 13, 14), 12));
        assertThat(merged).containsExactly(2, 3, 12).isSorted().doesNotHaveDuplicates();
    }

    // ---- determinism (§11) ----

    @Test
    void isDeterministicForIdenticalInputs() {
        WindowDecision left = wd(window(0, 1, 2, 3, 4), 2, 3);
        WindowDecision right = wd(window(2, 3, 4, 5, 6), 4);

        assertThat(merger.merge(List.of(left, right)))
                .isEqualTo(merger.merge(List.of(left, right)));
    }

    @Test
    void isIndependentOfTheOrderWindowsAreConsidered() {
        // M4: each position is decided once by its authoritative window, so window order cannot
        // change the result.
        WindowDecision left = wd(window(0, 1, 2, 3, 4), 2, 3);
        WindowDecision right = wd(window(2, 3, 4, 5, 6), 4);

        assertThat(merger.merge(List.of(left, right)))
                .isEqualTo(merger.merge(List.of(right, left)));
    }

    // ---- large document ----

    @Test
    void mergesAManyWindowDocumentIntoAnAscendingUniqueSet() {
        // Fifty overlapping windows tiling a long document; each reports its own central position,
        // which it owns. The merged set is exactly those positions, ascending and unique.
        int windowCount = 50;
        int step = 3;
        int size = 5;
        List<WindowDecision> windowDecisions = new ArrayList<>();
        List<Integer> expected = new ArrayList<>();
        for (int w = 0; w < windowCount; w++) {
            int start = w * step;
            int[] ordinals = new int[size];
            for (int i = 0; i < size; i++) {
                ordinals[i] = start + i;
            }
            int center = start + size / 2;
            windowDecisions.add(wd(window(ordinals), center));
            expected.add(center);
        }

        List<Integer> merged = merger.merge(windowDecisions).boundaryOrdinals();

        assertThat(merged).isSorted().doesNotHaveDuplicates().containsExactlyElementsOf(expected);
    }

    // ---- terminal failure (§12) ----

    @Test
    void failsTerminallyWhenADecisionReportsAUnitItsWindowDoesNotPresent() {
        // Violates the upstream validation guarantee merging assumes; an internal defect, not a
        // recoverable, model-caused condition.
        assertThatIllegalStateException()
                .isThrownBy(() -> merge(wd(window(1, 2, 3), 9)))
                .withMessageContaining("9");
    }
}
