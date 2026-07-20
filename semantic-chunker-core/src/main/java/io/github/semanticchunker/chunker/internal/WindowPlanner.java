package io.github.semanticchunker.chunker.internal;

import io.github.semanticchunker.chunker.ChunkingException;
import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.PreparedDocument;
import io.github.semanticchunker.model.ChunkingModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Divides the prepared document into overlapping windows sized to fit what the model can consider
 * at once (WINDOW_PLANNING.md).
 *
 * <p>It sizes windows against the model's context-window limit and token estimates, which it
 * obtains from the {@link ChunkingModel} — the only component that knows which model it wraps
 * (CONTRIBUTING_ARCHITECTURE.md §6.2.3). It reuses the shared {@link RequestRepresentation} to
 * render the content and the maximal valid response whose sizes it estimates, so no rendering is
 * duplicated. It never executes the model.
 */
class WindowPlanner {

    /** Minimum units of context a boundary is guaranteed on each side within a window (§1). */
    private static final int CONTEXT_UNITS = 1;

    /**
     * Overlap size in units: the trailing units repeated as the next window's leading units (§1).
     */
    private static final int OVERLAP_UNITS = 2 * CONTEXT_UNITS;

    /**
     * The reciprocal of {@code SAFETY_MARGIN_FRACTION} (§1, {@code 0.05}). The safety margin is
     * {@code ceil(SAFETY_MARGIN_FRACTION × maxInputTokens)}; because the fraction is exactly {@code
     * 1/20}, the margin is computed as an exact integer ceiling division by this reciprocal, which
     * avoids the off-by-one that binary floating-point would introduce for exact multiples.
     */
    private static final int SAFETY_MARGIN_RECIPROCAL = 20;

    private final ChunkingModel model;
    private final RequestRepresentation representation;

    WindowPlanner(ChunkingModel model, RequestRepresentation representation) {
        this.model = Objects.requireNonNull(model, "model");
        this.representation = Objects.requireNonNull(representation, "representation");
    }

    /**
     * Plans the windows for a document (WINDOW_PLANNING.md §8).
     *
     * @param document the document to divide
     * @return the ordered, overlapping windows over the document's units; empty for an empty
     *     document
     * @throws ChunkingException if the model reports a non-positive context-window limit, or if the
     *     fixed request overhead leaves no usable input budget for any content (§14, terminal)
     */
    List<Window> plan(PreparedDocument document) {
        List<DocumentUnit> units = document.units();
        if (units.isEmpty()) {
            return List.of();
        }

        int maxInputTokens = model.maxInputTokens();
        if (maxInputTokens <= 0) {
            throw new ChunkingException(
                    "Window planning cannot proceed: the model reported a non-positive"
                            + " context-window limit ("
                            + maxInputTokens
                            + ").");
        }

        int promptOverhead = estimate(PromptRenderer.INSTRUCTION);
        int safetyMargin = Math.ceilDiv(maxInputTokens, SAFETY_MARGIN_RECIPROCAL);
        int smallestOutputReservation = estimate(representation.renderMaximalResponse(List.of()));
        if (promptOverhead + safetyMargin + smallestOutputReservation >= maxInputTokens) {
            throw new ChunkingException(
                    "Window planning cannot proceed: the fixed request overhead leaves no usable"
                            + " input budget for content within the model's context-window limit ("
                            + maxInputTokens
                            + ").");
        }

        int overheadPlusMargin = promptOverhead + safetyMargin;
        List<Window> windows = new ArrayList<>();
        int lastPosition = units.size() - 1;
        int startPosition = 0;
        while (true) {
            int endPosition = startPosition;
            boolean startAdmissible =
                    admissible(
                            units.subList(startPosition, startPosition + 1),
                            overheadPlusMargin,
                            maxInputTokens);
            if (startAdmissible) {
                while (endPosition < lastPosition
                        && admissible(
                                units.subList(startPosition, endPosition + 2),
                                overheadPlusMargin,
                                maxInputTokens)) {
                    endPosition++;
                }
            }
            windows.add(
                    new Window(
                            units.subList(startPosition, endPosition + 1),
                            !startAdmissible,
                            startPosition == 0));

            if (endPosition == lastPosition) {
                break;
            }
            startPosition = Math.max(startPosition + 1, endPosition - OVERLAP_UNITS + 1);
        }
        return List.copyOf(windows);
    }

    private boolean admissible(
            List<DocumentUnit> candidate, int overheadPlusMargin, int maxInputTokens) {
        int contentCost = estimate(representation.renderContent(candidate));
        int outputReservation = estimate(representation.renderMaximalResponse(candidate));
        return (long) contentCost + overheadPlusMargin + outputReservation <= maxInputTokens;
    }

    private int estimate(String text) {
        return model.estimateTokens(text);
    }
}
