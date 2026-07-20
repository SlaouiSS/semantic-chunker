package io.github.semanticchunker.chunker.internal;

import io.github.semanticchunker.chunker.ChunkingResult;
import io.github.semanticchunker.chunker.ExecutionMetadata;
import io.github.semanticchunker.chunker.ProcessingInfo;
import io.github.semanticchunker.chunker.SemanticChunk;
import io.github.semanticchunker.chunker.Warning;
import io.github.semanticchunker.document.DocumentSource;
import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.PreparedDocument;
import io.github.semanticchunker.extraction.DocumentExtractor;
import io.github.semanticchunker.model.ChunkingModel;
import io.github.semanticchunker.model.ChunkingRequest;
import io.github.semanticchunker.model.ModelResponse;
import io.github.semanticchunker.model.TokenUsage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The chunking orchestration: the thin coordinator that drives the pipeline from a document to the
 * final result, in the fixed stage order, coordinating the collaborators but performing none of
 * their specialized work itself (ARCHITECTURE.md, section 4, "Chunking orchestration").
 *
 * <p>This class is <strong>non-public API</strong>: it lives in an {@code internal} package and may
 * change or be deleted without notice (see the package documentation). It is Java-public only
 * because the public {@link io.github.semanticchunker.chunker.SemanticChunker} facade, in a
 * different package, must construct and delegate to it; the collaborators it drives remain
 * package-private.
 *
 * <p>The stage order is fixed and is a correctness requirement, not a convenience: extraction and
 * normalization (through the {@link DocumentExtractor}) produce the {@link PreparedDocument}; the
 * document is then planned into windows; each window is rendered into a request, executed against
 * the model, and its response validated into a boundary decision; the per-window decisions are
 * merged into one set; chunks are constructed from that set; and the result is assembled.
 * Information moves as immutable values, each stage producing the input of the next.
 */
public final class ChunkingPipeline {

    /**
     * The maximum number of model attempts for a single window: one initial attempt plus up to two
     * retries (RECOVERY_POLICY.md, specification parameter {@code ATTEMPT_MAX}). It realizes the
     * bounded-retry requirement of CONTRIBUTING_ARCHITECTURE.md §9.2 and is the single source of
     * that bound.
     */
    private static final int ATTEMPT_MAX = 3;

    /**
     * The fewest units a window must present for it to admit a cut: a cut is a division between two
     * units, so a window of fewer than this many units can name no boundary and is resolved locally
     * (RECOVERY_POLICY.md §7).
     */
    private static final int MINIMUM_UNITS_FOR_BOUNDARY = 2;

    private final DocumentExtractor documentExtractor;
    private final WindowPlanner windowPlanner;
    private final PromptRenderer promptRenderer;
    private final ModelExecutor modelExecutor;
    private final ResponseValidator responseValidator;
    private final BoundaryMerger boundaryMerger;
    private final ChunkAssembler chunkAssembler;
    private final ResultAssembler resultAssembler;

    ChunkingPipeline(
            DocumentExtractor documentExtractor,
            WindowPlanner windowPlanner,
            PromptRenderer promptRenderer,
            ModelExecutor modelExecutor,
            ResponseValidator responseValidator,
            BoundaryMerger boundaryMerger,
            ChunkAssembler chunkAssembler,
            ResultAssembler resultAssembler) {
        this.documentExtractor = Objects.requireNonNull(documentExtractor, "documentExtractor");
        this.windowPlanner = Objects.requireNonNull(windowPlanner, "windowPlanner");
        this.promptRenderer = Objects.requireNonNull(promptRenderer, "promptRenderer");
        this.modelExecutor = Objects.requireNonNull(modelExecutor, "modelExecutor");
        this.responseValidator = Objects.requireNonNull(responseValidator, "responseValidator");
        this.boundaryMerger = Objects.requireNonNull(boundaryMerger, "boundaryMerger");
        this.chunkAssembler = Objects.requireNonNull(chunkAssembler, "chunkAssembler");
        this.resultAssembler = Objects.requireNonNull(resultAssembler, "resultAssembler");
    }

    /**
     * Wires a pipeline with the default collaborators around the given extraction and model
     * abstractions. This is the factory the public facade uses.
     *
     * @param extractor the extraction abstraction; never {@code null}
     * @param model the model abstraction; never {@code null}
     * @return a fully wired pipeline
     */
    public static ChunkingPipeline create(DocumentExtractor extractor, ChunkingModel model) {
        Objects.requireNonNull(extractor, "extractor");
        Objects.requireNonNull(model, "model");
        RequestRepresentation representation = new RequestRepresentation();
        return new ChunkingPipeline(
                extractor,
                new WindowPlanner(model, representation),
                new PromptRenderer(model, representation),
                new ModelExecutor(model),
                new ResponseValidator(),
                new BoundaryMerger(),
                new ChunkAssembler(),
                new ResultAssembler());
    }

    /**
     * Chunks a raw document: extracts and normalizes it, then chunks the prepared document.
     *
     * @param source the raw document; never {@code null}
     * @return the result of the run
     */
    public ChunkingResult chunk(DocumentSource source) {
        Objects.requireNonNull(source, "source");
        PreparedDocument prepared = documentExtractor.extract(source);
        return chunk(prepared);
    }

    /**
     * Chunks an already-prepared document, running the interior of the pipeline under the failure
     * policy (RECOVERY_POLICY.md).
     *
     * <p>The sequence is fixed: plan windows, then process each window in window order, then merge
     * → construct chunks → assemble the result. Each window is resolved into a validated decision
     * that always participates in merging (BOUNDARY_MERGING.md §7): an over-budget window is
     * degraded to a no-op (empty) decision with a warning before any model call (§6); a window too
     * small to admit a cut contributes an empty decision silently, without a model call (§7); every
     * other window is executed and validated, retried up to {@link #ATTEMPT_MAX} times on a
     * validation rejection, and degraded to an empty decision with a warning if all attempts are
     * rejected (§3–§5).
     *
     * <p>Only validation-level rejections are recovered. A failure to obtain a response, a planning
     * failure, an extraction failure, or an internal invariant violation is terminal and propagates
     * (§8); this method neither retries nor degrades it.
     *
     * <p>The run's bookkeeping is populated deterministically: {@link ProcessingInfo} carries the
     * number of windows planned and the number degraded (§10, equal to the number of warnings), and
     * {@link ExecutionMetadata} carries the summed token usage of every response obtained,
     * including rejected responses and every retry (§11).
     *
     * @param document the normalized document; never {@code null}
     * @return the result of the run
     */
    public ChunkingResult chunk(PreparedDocument document) {
        Objects.requireNonNull(document, "document");

        List<Window> windows = windowPlanner.plan(document);

        List<WindowDecision> windowDecisions = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        List<ModelResponse> responses = new ArrayList<>();
        for (Window window : windows) {
            WindowOutcome outcome = processWindow(window);
            windowDecisions.add(outcome.decision());
            outcome.warning().ifPresent(warnings::add);
            responses.addAll(outcome.responses());
        }

        MergedBoundaries merged = boundaryMerger.merge(windowDecisions);
        List<SemanticChunk> chunks = chunkAssembler.assemble(merged, document);

        ProcessingInfo processingInfo = new ProcessingInfo(windows.size(), warnings.size());
        ExecutionMetadata executionMetadata = new ExecutionMetadata(totalTokenUsage(responses));
        return resultAssembler.assemble(chunks, warnings, processingInfo, executionMetadata);
    }

    /**
     * Resolves a single window under the recovery policy, in window order (RECOVERY_POLICY.md §2).
     * The returned outcome always carries a validated decision for merging; it carries a warning
     * only when the window was degraded, and the responses actually obtained (for token
     * accounting).
     */
    private WindowOutcome processWindow(Window window) {
        if (window.overBudget()) {
            // §6: an over-budget window is never sent to the model; it is degraded and warned.
            return WindowOutcome.degraded(window, DegradationCategory.OVER_BUDGET);
        }
        if (window.units().size() < MINIMUM_UNITS_FOR_BOUNDARY) {
            // §7: too small to admit a cut — an empty decision, silently, without a model call.
            return WindowOutcome.resolvedLocally(window);
        }
        return attemptWindow(window);
    }

    /**
     * Executes and validates a window up to {@link #ATTEMPT_MAX} times, re-presenting the identical
     * request each attempt (RECOVERY_POLICY.md §3–§5). Returns the accepted decision on the first
     * attempt that validates, or a degraded (retry-exhausted) outcome if every attempt is rejected.
     */
    private WindowOutcome attemptWindow(Window window) {
        ChunkingRequest request = promptRenderer.render(window);
        List<ModelResponse> responses = new ArrayList<>();
        for (int attempt = 1; attempt <= ATTEMPT_MAX; attempt++) {
            ModelResponse response = modelExecutor.execute(request);
            responses.add(response);
            ValidationOutcome outcome = responseValidator.validate(response, window);
            if (outcome instanceof ValidationOutcome.Accepted accepted) {
                return WindowOutcome.accepted(window, accepted.decision(), responses);
            }
        }
        return WindowOutcome.degraded(window, DegradationCategory.RETRY_EXHAUSTED, responses);
    }

    /**
     * Chunks the document at the given path.
     *
     * <p><strong>Not yet implemented.</strong> Reading the bytes is trivial, but turning a bare
     * path into a {@link DocumentSource} requires determining the media type extraction routes on,
     * and the documentation does not specify the detection policy. Rather than invent one, this
     * overload is left unfinished; callers can build a {@link DocumentSource} explicitly and use
     * {@link #chunk(DocumentSource)}.
     *
     * @param path the path to the document; never {@code null}
     * @return the result of the run
     */
    public ChunkingResult chunk(Path path) {
        Objects.requireNonNull(path, "path");
        throw new UnsupportedOperationException(
                "Chunking from a Path is not implemented yet: determining the media type from a"
                        + " bare path is not specified by the documentation. Build a DocumentSource"
                        + " explicitly and use chunk(DocumentSource).");
    }

    private static TokenUsage totalTokenUsage(List<ModelResponse> responses) {
        int inputTokens = 0;
        int outputTokens = 0;
        for (ModelResponse response : responses) {
            inputTokens += response.tokenUsage().inputTokens();
            outputTokens += response.tokenUsage().outputTokens();
        }
        return new TokenUsage(inputTokens, outputTokens);
    }

    /**
     * The two conditions under which a window is degraded (RECOVERY_POLICY.md §6). Each carries the
     * label the warning uses to convey the category, as §9 requires.
     */
    private enum DegradationCategory {
        RETRY_EXHAUSTED("retry exhausted"),
        OVER_BUDGET("over-budget");

        private final String label;

        DegradationCategory(String label) {
            this.label = label;
        }
    }

    /**
     * The result of processing one window: the decision it contributes to merging, an optional
     * warning present only when the window was degraded, and the responses obtained (for token
     * accounting). A window is degraded exactly when a warning is present, so the number of
     * degraded windows equals the number of warnings (RECOVERY_POLICY.md §9, §10).
     */
    private record WindowOutcome(
            WindowDecision decision, Optional<Warning> warning, List<ModelResponse> responses) {

        static WindowOutcome accepted(
                Window window, ValidatedBoundaryDecision decision, List<ModelResponse> responses) {
            return new WindowOutcome(
                    new WindowDecision(window, decision), Optional.empty(), List.copyOf(responses));
        }

        /** A window resolved locally as an empty decision, without a model call (§7). */
        static WindowOutcome resolvedLocally(Window window) {
            return new WindowOutcome(noOpDecision(window), Optional.empty(), List.of());
        }

        /** A degraded window that was never sent to the model — over-budget (§6). */
        static WindowOutcome degraded(Window window, DegradationCategory category) {
            return degraded(window, category, List.of());
        }

        /** A degraded window, carrying its warning and any responses obtained. */
        static WindowOutcome degraded(
                Window window, DegradationCategory category, List<ModelResponse> responses) {
            return new WindowOutcome(
                    noOpDecision(window),
                    Optional.of(warningFor(window, category)),
                    List.copyOf(responses));
        }

        private static WindowDecision noOpDecision(Window window) {
            return new WindowDecision(window, new ValidatedBoundaryDecision(List.of()));
        }

        private static Warning warningFor(Window window, DegradationCategory category) {
            return new Warning(
                    "Window covering units "
                            + unitSpan(window)
                            + " was degraded ("
                            + category.label
                            + ") and reduced to a single chunk.");
        }

        /**
         * Identifies a window unambiguously by the span of its units' global ordinals — first
         * through last in document order (RECOVERY_POLICY.md §9).
         */
        private static String unitSpan(Window window) {
            List<DocumentUnit> units = window.units();
            int first = units.get(0).provenance().globalOrdinal();
            int last = units.get(units.size() - 1).provenance().globalOrdinal();
            return first + "-" + last;
        }
    }
}
