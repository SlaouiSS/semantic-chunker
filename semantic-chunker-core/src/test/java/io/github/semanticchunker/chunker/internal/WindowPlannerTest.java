package io.github.semanticchunker.chunker.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.semanticchunker.chunker.ChunkingException;
import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Paragraph;
import io.github.semanticchunker.document.PreparedDocument;
import io.github.semanticchunker.document.Provenance;
import io.github.semanticchunker.model.ChunkingModel;
import io.github.semanticchunker.model.ChunkingRequest;
import io.github.semanticchunker.model.ModelResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Behavioral tests for the window-planning stage (WINDOW_PLANNING.md).
 *
 * <p>The token estimator is supplied by a controlled fake model, exactly as the specification
 * allows (§2, §7). Most tests use a estimator that counts a sentinel character present only in unit
 * text, so that {@code promptOverhead} and {@code outputReservation} are zero and window shaping is
 * driven purely by {@code contentCost}; dedicated tests use estimators that make {@code
 * promptOverhead} and {@code outputReservation} non-zero to prove they are reserved.
 */
class WindowPlannerTest {

    private static final char SENTINEL = (char) 1;

    private final RequestRepresentation representation = new RequestRepresentation();

    // ---- fixtures ----

    private static DocumentUnit unit(int ordinal, String text) {
        return new Paragraph(new Provenance(ordinal, 1, ordinal, ordinal + 1), text, Map.of());
    }

    private static DocumentUnit sentinelUnit(int ordinal, int cost) {
        return unit(ordinal, String.valueOf(SENTINEL).repeat(cost));
    }

    private static PreparedDocument sentinelDocument(int... costs) {
        List<DocumentUnit> units = new ArrayList<>();
        for (int ordinal = 0; ordinal < costs.length; ordinal++) {
            units.add(sentinelUnit(ordinal, costs[ordinal]));
        }
        return new PreparedDocument(units, Map.of());
    }

    private static PreparedDocument plainDocument(int size) {
        List<DocumentUnit> units = new ArrayList<>();
        for (int ordinal = 0; ordinal < size; ordinal++) {
            units.add(unit(ordinal, "u" + ordinal));
        }
        return new PreparedDocument(units, Map.of());
    }

    private static int countSentinel(String text) {
        return (int) text.chars().filter(character -> character == SENTINEL).count();
    }

    private WindowPlanner planner(ChunkingModel model) {
        return new WindowPlanner(model, new RequestRepresentation());
    }

    private static List<Integer> ordinals(Window window) {
        return window.units().stream().map(unit -> unit.provenance().globalOrdinal()).toList();
    }

    private static List<List<Integer>> ordinals(List<Window> windows) {
        return windows.stream().map(WindowPlannerTest::ordinals).toList();
    }

    // ---- construction ----

    @Test
    void rejectsNullModel() {
        assertThatNullPointerException().isThrownBy(() -> new WindowPlanner(null, representation));
    }

    @Test
    void rejectsNullRepresentation() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new WindowPlanner(
                                        new FakeModel(100, WindowPlannerTest::countSentinel),
                                        null));
    }

    // ---- empty document (§11) ----

    @Test
    void emptyDocumentProducesNoWindowsAndNeverTouchesTheModel() {
        List<Window> windows =
                planner(new ExplodingModel()).plan(new PreparedDocument(List.of(), Map.of()));

        assertThat(windows).isEmpty();
    }

    // ---- single unit / single window (§11) ----

    @Test
    void singleUnitThatFitsProducesOneAdmissibleWindow() {
        List<Window> windows =
                planner(new FakeModel(100, WindowPlannerTest::countSentinel))
                        .plan(sentinelDocument(1));

        assertThat(windows).hasSize(1);
        assertThat(ordinals(windows.get(0))).containsExactly(0);
        assertThat(windows.get(0).overBudget()).isFalse();
    }

    @Test
    void aDocumentThatFitsProducesASingleWindowCoveringEveryUnit() {
        List<Window> windows =
                planner(new FakeModel(100, WindowPlannerTest::countSentinel))
                        .plan(sentinelDocument(1, 1, 1));

        assertThat(windows).hasSize(1);
        assertThat(ordinals(windows.get(0))).containsExactly(0, 1, 2);
        assertThat(windows.get(0).overBudget()).isFalse();
    }

    // ---- multiple windows, overlap, growth, termination (§8, §9) ----

    @Test
    void aLargeDocumentProducesMultipleWindowsThatOverlapByTwoUnits() {
        // maxInputTokens 100 -> safetyMargin ceil(100/20)=5 -> content budget 95; each unit costs
        // 20,
        // so at most four units fit per window (80 <= 95, 100 > 95).
        List<Window> windows =
                planner(new FakeModel(100, WindowPlannerTest::countSentinel))
                        .plan(sentinelDocument(20, 20, 20, 20, 20, 20, 20, 20));

        assertThat(ordinals(windows))
                .containsExactly(List.of(0, 1, 2, 3), List.of(2, 3, 4, 5), List.of(4, 5, 6, 7));
        // Overlap of exactly OVERLAP_UNITS (2): the last two units of each window lead the next.
        for (int i = 0; i + 1 < windows.size(); i++) {
            List<Integer> current = ordinals(windows.get(i));
            List<Integer> next = ordinals(windows.get(i + 1));
            assertThat(current.subList(current.size() - 2, current.size()))
                    .isEqualTo(next.subList(0, 2));
        }
    }

    @Test
    void growsAWindowUntilAddingTheNextUnitWouldExceedTheBudget() {
        // budget 95, each unit costs 20 -> first window is exactly [0,1,2,3]; the fifth would
        // overflow.
        List<Window> windows =
                planner(new FakeModel(100, WindowPlannerTest::countSentinel))
                        .plan(sentinelDocument(20, 20, 20, 20, 20));

        assertThat(ordinals(windows.get(0))).containsExactly(0, 1, 2, 3);
    }

    @Test
    void planningStopsWhenTheLastUnitIsReached() {
        List<Window> windows =
                planner(new FakeModel(100, WindowPlannerTest::countSentinel))
                        .plan(sentinelDocument(20, 20, 20, 20, 20, 20, 20, 20));

        List<Integer> last = ordinals(windows.get(windows.size() - 1));
        assertThat(last.get(last.size() - 1)).isEqualTo(7);
    }

    // ---- invariants: coverage, ordering, progress, contiguity, identity ----

    @Test
    void everyUnitIsCoveredByAtLeastOneWindow() {
        int size = 8;
        List<Window> windows =
                planner(new FakeModel(100, WindowPlannerTest::countSentinel))
                        .plan(sentinelDocument(20, 20, 20, 20, 20, 20, 20, 20));

        Set<Integer> covered =
                windows.stream()
                        .flatMap(window -> ordinals(window).stream())
                        .collect(Collectors.toSet());
        assertThat(covered).isEqualTo(IntStream.range(0, size).boxed().collect(Collectors.toSet()));
    }

    @Test
    void windowsAreOrderedByStrictlyIncreasingFirstUnit() {
        List<Window> windows =
                planner(new FakeModel(100, WindowPlannerTest::countSentinel))
                        .plan(sentinelDocument(20, 20, 20, 20, 20, 20, 20, 20));

        List<Integer> firsts = windows.stream().map(window -> ordinals(window).get(0)).toList();
        for (int i = 0; i + 1 < firsts.size(); i++) {
            assertThat(firsts.get(i + 1)).isGreaterThan(firsts.get(i));
        }
    }

    @Test
    void eachWindowViewsAContiguousRunOfUnitsInDocumentOrder() {
        List<Window> windows =
                planner(new FakeModel(100, WindowPlannerTest::countSentinel))
                        .plan(sentinelDocument(20, 20, 20, 20, 20, 20, 20, 20));

        for (Window window : windows) {
            List<Integer> windowOrdinals = ordinals(window);
            for (int i = 0; i + 1 < windowOrdinals.size(); i++) {
                assertThat(windowOrdinals.get(i + 1)).isEqualTo(windowOrdinals.get(i) + 1);
            }
        }
    }

    @Test
    void preservesGlobalOrdinalsAndTheOriginalUnitInstances() {
        PreparedDocument document = sentinelDocument(20, 20, 20, 20, 20, 20, 20, 20);

        List<Window> windows =
                planner(new FakeModel(100, WindowPlannerTest::countSentinel)).plan(document);

        for (Window window : windows) {
            for (DocumentUnit unit : window.units()) {
                int ordinal = unit.provenance().globalOrdinal();
                // Same identity and the very same instance the prepared document holds (a view,
                // §6).
                assertThat(unit).isSameAs(document.units().get(ordinal));
            }
        }
    }

    // ---- oversized unit / over-budget window (§12) ----

    @Test
    void anOversizedSingleUnitBecomesAnOverBudgetWindow() {
        // content budget 95; the one unit costs 200, and the fixed floor (margin 5) leaves budget >
        // 0
        // so this is a localized over-budget window, not a terminal failure.
        List<Window> windows =
                planner(new FakeModel(100, WindowPlannerTest::countSentinel))
                        .plan(sentinelDocument(200));

        assertThat(windows).hasSize(1);
        assertThat(ordinals(windows.get(0))).containsExactly(0);
        assertThat(windows.get(0).overBudget()).isTrue();
    }

    @Test
    void anOversizedUnitInTheMiddleIsIsolatedAsItsOwnOverBudgetWindow() {
        List<Window> windows =
                planner(new FakeModel(100, WindowPlannerTest::countSentinel))
                        .plan(sentinelDocument(10, 200, 10));

        assertThat(ordinals(windows)).containsExactly(List.of(0), List.of(1), List.of(2));
        assertThat(windows.get(0).overBudget()).isFalse();
        assertThat(windows.get(1).overBudget()).isTrue();
        assertThat(windows.get(2).overBudget()).isFalse();
    }

    // ---- usable budget boundary ----

    @Test
    void aUnitThatExactlyFitsTheUsableBudgetIsAdmissible() {
        // maxInputTokens 2 -> safetyMargin ceil(2/20)=1 -> content budget 1.
        List<Window> fits =
                planner(new FakeModel(2, WindowPlannerTest::countSentinel))
                        .plan(sentinelDocument(1));
        assertThat(fits.get(0).overBudget()).isFalse();

        List<Window> overflows =
                planner(new FakeModel(2, WindowPlannerTest::countSentinel))
                        .plan(sentinelDocument(2));
        assertThat(overflows.get(0).overBudget()).isTrue();
    }

    // ---- non-positive usable budget / model capability (§14, terminal) ----

    @Test
    void aNonPositiveUsableBudgetIsATerminalChunkingFailure() {
        // maxInputTokens 1 -> safetyMargin ceil(1/20)=1 -> floor 1 >= 1, no content can ever fit.
        assertThatThrownBy(
                        () ->
                                planner(new FakeModel(1, WindowPlannerTest::countSentinel))
                                        .plan(sentinelDocument(1)))
                .isInstanceOf(ChunkingException.class);
    }

    @Test
    void aNonPositiveContextWindowLimitIsATerminalChunkingFailure() {
        assertThatThrownBy(
                        () ->
                                planner(new FakeModel(0, WindowPlannerTest::countSentinel))
                                        .plan(sentinelDocument(1)))
                .isInstanceOf(ChunkingException.class);
        assertThatThrownBy(
                        () ->
                                planner(new FakeModel(-5, WindowPlannerTest::countSentinel))
                                        .plan(sentinelDocument(1)))
                .isInstanceOf(ChunkingException.class);
    }

    // ---- constrained-budget degenerate case (§11) ----

    @Test
    void aConstrainedBudgetStillGuaranteesCoverageAndForwardProgress() {
        // budget 95, each unit costs 40 -> windows hold at most two units (< OVERLAP_UNITS + 1),
        // so overlap is best-effort but coverage and progress must still hold.
        List<Window> windows =
                planner(new FakeModel(100, WindowPlannerTest::countSentinel))
                        .plan(sentinelDocument(40, 40, 40, 40));

        assertThat(windows)
                .allSatisfy(window -> assertThat(window.units()).hasSizeLessThanOrEqualTo(2));
        Set<Integer> covered =
                windows.stream()
                        .flatMap(window -> ordinals(window).stream())
                        .collect(Collectors.toSet());
        assertThat(covered).isEqualTo(Set.of(0, 1, 2, 3));
        List<Integer> firsts = windows.stream().map(window -> ordinals(window).get(0)).toList();
        for (int i = 0; i + 1 < firsts.size(); i++) {
            assertThat(firsts.get(i + 1)).isGreaterThan(firsts.get(i));
        }
    }

    @Test
    void onlyTheFirstWindowContainsTheDocumentFirstUnit() {
        List<Window> windows =
                planner(new FakeModel(100, WindowPlannerTest::countSentinel))
                        .plan(sentinelDocument(20, 20, 20, 20, 20, 20, 20, 20));

        assertThat(windows.get(0).containsDocumentFirstUnit()).isTrue();
        assertThat(windows.subList(1, windows.size()))
                .allSatisfy(window -> assertThat(window.containsDocumentFirstUnit()).isFalse());
    }

    // ---- determinism (§13) ----

    @Test
    void producesIdenticalWindowsForIdenticalInputs() {
        PreparedDocument document = sentinelDocument(20, 20, 20, 20, 20, 20, 20, 20);

        List<List<Integer>> first =
                ordinals(
                        planner(new FakeModel(100, WindowPlannerTest::countSentinel))
                                .plan(document));
        List<List<Integer>> second =
                ordinals(
                        planner(new FakeModel(100, WindowPlannerTest::countSentinel))
                                .plan(document));

        assertThat(second).isEqualTo(first);
    }

    // ---- token estimation: reuse of the canonical representation and the fixed overhead (§7, §8)
    // ----

    @Test
    void estimatesTheContentAndMaximalResponseRenderingsAndAFixedOverheadInstruction() {
        RecordingModel model = new RecordingModel(100);
        PreparedDocument document = plainDocument(1);
        List<DocumentUnit> units = document.units();

        planner(model).plan(document);

        // contentCost is estimated on the canonical content rendering; outputReservation on the
        // canonical maximal-response rendering; both reuse RequestRepresentation (no duplication).
        assertThat(model.estimated).contains(representation.renderContent(units));
        assertThat(model.estimated).contains(representation.renderMaximalResponse(units));
        // promptOverhead is estimated on a fixed instruction that is neither of those renderings.
        String content = representation.renderContent(units);
        String maximalResponse = representation.renderMaximalResponse(units);
        assertThat(model.estimated)
                .anySatisfy(
                        estimated ->
                                assertThat(estimated)
                                        .isNotBlank()
                                        .isNotEqualTo(content)
                                        .isNotEqualTo(maximalResponse));
    }

    // ---- promptOverhead and outputReservation are reserved from the budget (§8) ----

    @Test
    void outputReservationIsReservedSoAWindowSplitsWhereItOtherwiseWouldNot() {
        PreparedDocument document = plainDocument(3);
        // contentCost = 5 per unit; outputReservation jumps to 30 at three units; promptOverhead 0.
        // Without reserving output, [0,1,2] would fit (15 + 0 + 3 = 18 <= 45) and be one window;
        // reserving output makes it 15 + 0 + 30 + 3 = 48 > 45, forcing a split.
        ChunkingModel model =
                mappedModel(
                        45,
                        document.units(),
                        run -> 5 * run.size(),
                        run -> run.size() <= 1 ? 0 : run.size() == 2 ? 5 : 30,
                        0);

        List<Window> windows = planner(model).plan(document);

        assertThat(ordinals(windows)).containsExactly(List.of(0, 1), List.of(1, 2));
    }

    @Test
    void promptOverheadIsReservedSoAWindowSplitsWhereItOtherwiseWouldNot() {
        PreparedDocument document = plainDocument(3);
        // contentCost 8/15/25 for size 1/2/3; outputReservation 0; promptOverhead 15.
        // Without overhead, [0,1,2] fits (25 + 0 + 2 = 27 <= 40) as one window; reserving overhead
        // makes it 25 + 15 + 0 + 2 = 42 > 40, forcing a split.
        ChunkingModel model =
                mappedModel(
                        40,
                        document.units(),
                        run -> run.size() == 1 ? 8 : run.size() == 2 ? 15 : 25,
                        run -> 0,
                        15);

        List<Window> windows = planner(model).plan(document);

        assertThat(ordinals(windows)).containsExactly(List.of(0, 1), List.of(1, 2));
    }

    /**
     * Builds a model whose token estimate is looked up per exact rendered string: content
     * renderings cost {@code contentCost(run)}, maximal-response renderings cost {@code
     * outputCost(run)}, and any other string (the fixed overhead instruction) costs {@code
     * promptOverhead}.
     */
    private ChunkingModel mappedModel(
            int maxInputTokens,
            List<DocumentUnit> allUnits,
            ToIntFunction<List<DocumentUnit>> contentCost,
            ToIntFunction<List<DocumentUnit>> outputCost,
            int promptOverhead) {
        Map<String, Integer> costs = new HashMap<>();
        int size = allUnits.size();
        for (int i = 0; i < size; i++) {
            for (int j = i; j < size; j++) {
                List<DocumentUnit> run = allUnits.subList(i, j + 1);
                costs.put(representation.renderContent(run), contentCost.applyAsInt(run));
                costs.put(representation.renderMaximalResponse(run), outputCost.applyAsInt(run));
            }
        }
        costs.put(representation.renderMaximalResponse(List.of()), 0);
        return new FakeModel(maxInputTokens, text -> costs.getOrDefault(text, promptOverhead));
    }

    // ---- fake models ----

    private static final class FakeModel implements ChunkingModel {
        private final int maxInputTokens;
        private final ToIntFunction<String> estimator;

        FakeModel(int maxInputTokens, ToIntFunction<String> estimator) {
            this.maxInputTokens = maxInputTokens;
            this.estimator = estimator;
        }

        @Override
        public ModelResponse execute(ChunkingRequest request) {
            throw new AssertionError("window planning must not execute the model");
        }

        @Override
        public int maxInputTokens() {
            return maxInputTokens;
        }

        @Override
        public int estimateTokens(String text) {
            return estimator.applyAsInt(text);
        }
    }

    private static final class RecordingModel implements ChunkingModel {
        private final int maxInputTokens;
        private final List<String> estimated = new ArrayList<>();

        RecordingModel(int maxInputTokens) {
            this.maxInputTokens = maxInputTokens;
        }

        @Override
        public ModelResponse execute(ChunkingRequest request) {
            throw new AssertionError("window planning must not execute the model");
        }

        @Override
        public int maxInputTokens() {
            return maxInputTokens;
        }

        @Override
        public int estimateTokens(String text) {
            estimated.add(text);
            return 0;
        }
    }

    private static final class ExplodingModel implements ChunkingModel {
        @Override
        public ModelResponse execute(ChunkingRequest request) {
            throw new AssertionError("the model must not be touched for an empty document");
        }

        @Override
        public int maxInputTokens() {
            throw new AssertionError("the model must not be touched for an empty document");
        }

        @Override
        public int estimateTokens(String text) {
            throw new AssertionError("the model must not be touched for an empty document");
        }
    }
}
