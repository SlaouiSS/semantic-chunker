package io.github.semanticchunker.chunker.internal;

import io.github.semanticchunker.model.ChunkingModel;
import io.github.semanticchunker.model.ChunkingRequest;
import java.util.Objects;

/**
 * Turns one window into a well-defined request for a boundary decision, using the library's own
 * fixed instruction (PROMPT_SPECIFICATION.md; ARCHITECTURE.md §4, "Prompt orchestration").
 *
 * <p>This stage owns the complete model-facing instruction ({@link #INSTRUCTION}): the fixed task
 * description, the boundary-reference convention, and the required output shape (§2, §6, §8). The
 * instruction is fixed by the library, not the caller, so the library's behavior is defined by
 * itself (PROMPT.md). It is the single source of that text; window planning references it to
 * measure the prompt overhead it sizes windows against (WINDOW_PLANNING.md §8).
 *
 * <p>A request is assembled as the fixed instruction followed by the window's canonical content
 * representation, together with the generation hints the core cares about — a low temperature and
 * an output-token budget (§8; CONTRIBUTING_ARCHITECTURE.md §6.2.2). The content and the
 * output-budget sizing reuse the shared {@link RequestRepresentation}; no rendering or
 * serialization is duplicated.
 *
 * <p>Prompt construction only <em>prepares</em> the request. It never executes the model, performs
 * no inference, and does not validate, parse, retry, merge, or build chunks. It uses the model
 * solely for its read-only token-estimation capability, to size the output-token budget. It is
 * deterministic: for a given window the request is constructed identically every time (§10), and it
 * never modifies its inputs.
 */
class PromptRenderer {

    /**
     * The fixed, library-owned model instruction (§2, §6, §8). Its wording is an internal asset
     * (PROMPT.md §1); it describes the boundary-identification task, states that a new section is
     * named by the identifier of the unit at which it begins and that the first unit shown is not a
     * boundary, and states the required output shape (an ascending list of integers, or an empty
     * list).
     */
    static final String INSTRUCTION =
            "You are given a sequence of consecutive units of a document. Each unit begins with a"
                + " line of the form [unit N], where N is the unit's identifier, followed by that"
                + " unit's text. Read the units in order and decide, from the meaning of the text"
                + " alone, where the material divides into coherent, self-contained sections. Judge"
                + " only where the material divides, not what it means. A division is the unit at"
                + " which a new section begins. Report each such unit by its identifier N. The"
                + " start of the given material is not a division, so do not report the first unit"
                + " shown. Answer with only the identifiers, as a list of integers in ascending"
                + " order, written exactly as [N1,N2,N3]. Answer with [] when the material has no"
                + " internal division. Include nothing else in the answer.";

    /** Separates the fixed instruction from the window's content within the prompt. */
    private static final String PROMPT_SEPARATOR = "\n\n";

    /**
     * The generation temperature hint: the lowest value, so the request asks for as consistent a
     * response as the model can give (§8, "a low temperature"; PROMPT.md §3).
     */
    private static final double LOW_TEMPERATURE = 0.0d;

    private final ChunkingModel model;
    private final RequestRepresentation representation;

    PromptRenderer(ChunkingModel model, RequestRepresentation representation) {
        this.model = Objects.requireNonNull(model, "model");
        this.representation = Objects.requireNonNull(representation, "representation");
    }

    /**
     * Constructs the request for a window.
     *
     * <p>The prompt is the fixed instruction followed by the window's canonical content
     * representation. The output-token budget is the estimated size of the maximal valid response
     * for the window, which is the largest response the request could legitimately elicit; it uses
     * the model's read-only token estimation and the shared {@link RequestRepresentation} (§8;
     * WINDOW_PLANNING.md §8). The optional response-shape hint is omitted because the prompt
     * already states the required shape in full (§8).
     *
     * @param window the window to present; never {@code null}
     * @return the request to be executed against the model
     */
    ChunkingRequest render(Window window) {
        String content = representation.renderContent(window.units());
        String prompt = INSTRUCTION + PROMPT_SEPARATOR + content;
        int outputTokenBudget =
                model.estimateTokens(representation.renderMaximalResponse(window.units()));
        return new ChunkingRequest(prompt, null, LOW_TEMPERATURE, outputTokenBudget);
    }
}
