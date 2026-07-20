package io.github.semanticchunker.model;

/**
 * SPI: the core-owned abstraction over a language model. An adapter implements this contract to
 * present a specific provider to the library, and the core sees only this interface — never a
 * provider concept (SPI.md, section 4; CONTRIBUTING_ARCHITECTURE.md, section 6.2).
 *
 * <p><strong>Responsibility.</strong> To execute a library-composed {@link ChunkingRequest} against
 * a model and return its raw {@link ModelResponse}, and — because only the adapter knows which
 * model it wraps — to report that model's context-window limit ({@link #maxInputTokens()}) and to
 * estimate token counts ({@link #estimateTokens(String)}) so the library can plan windows. It is
 * <em>not</em> responsible for planning windows, framing prompts, validating the answer, or merging
 * decisions; those are the library's own work.
 *
 * <p><strong>Lifecycle.</strong> Instances are created by the caller and supplied to the {@code
 * SemanticChunker} builder. An instance is long-lived and reused across many calls and many
 * documents. The library owns orchestration: it consults {@link #maxInputTokens()} and {@link
 * #estimateTokens(String)} while planning windows, then calls {@link #execute(ChunkingRequest)}
 * once per window that requires a decision. The implementation neither creates nor disposes of
 * anything the library owns.
 *
 * <p><strong>Thread safety.</strong> An implementation MUST be safe for concurrent use by multiple
 * threads. The library may invoke any method from multiple threads without external synchronization
 * (CONTRIBUTING_ARCHITECTURE.md, section 10.4).
 *
 * <p><strong>Ownership.</strong> The core owns request construction, response validation, and every
 * decision about what to do with a response. The implementation owns reaching the provider and
 * faithfully delivering request and response, and owns reporting the model's limits and estimates
 * truthfully. The implementation validates nothing about the request beyond what it needs to send
 * it.
 *
 * <p><strong>Extension rules.</strong> An implementation MUST deliver the model's raw response for
 * the library to validate and MUST NOT interpret, validate, or route around validation. It MUST NOT
 * alter the request or substitute a different question, MUST NOT leak any provider-specific
 * concept, MUST NOT recreate unit identities, and MUST NOT perform the library's own recovery. When
 * it cannot obtain a response it MUST fail clearly (typically by throwing {@link ModelException})
 * rather than returning a fabricated or partial result, so the library can apply its retry and
 * fallback policy.
 */
public interface ChunkingModel {

    /**
     * Executes the given request against the language model and returns its raw response.
     *
     * @param request the framed request for a boundary decision about one window; never {@code
     *     null}
     * @return the model's raw response together with its token usage; never {@code null}
     * @throws ModelException if the model cannot be reached or does not return a response
     */
    ModelResponse execute(ChunkingRequest request);

    /**
     * The maximum number of input tokens the underlying model can consider at once.
     *
     * <p>Window planning sizes windows to fit within this limit while reserving room for the
     * model's response (CONTRIBUTING_ARCHITECTURE.md, section 6.2.3). This is a read-only property
     * of the model and carries no judgment about any document.
     *
     * @return the context-window limit, in tokens; a positive value
     */
    int maxInputTokens();

    /**
     * Estimates the number of tokens the given text will occupy for the underlying model.
     *
     * <p>The estimate is used by window planning. A documented heuristic is acceptable where the
     * provider cannot count precisely; under-counting risks context overflow and over-counting
     * wastes budget (CONTRIBUTING_ARCHITECTURE.md, section 6.2.3).
     *
     * @param text the text to estimate; never {@code null}
     * @return the estimated token count; a non-negative value
     */
    int estimateTokens(String text);
}
