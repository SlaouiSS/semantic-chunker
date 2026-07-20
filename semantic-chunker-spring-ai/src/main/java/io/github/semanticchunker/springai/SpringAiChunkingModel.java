package io.github.semanticchunker.springai;

import io.github.semanticchunker.model.ChunkingModel;
import io.github.semanticchunker.model.ChunkingRequest;
import io.github.semanticchunker.model.ModelException;
import io.github.semanticchunker.model.ModelResponse;
import io.github.semanticchunker.model.TokenUsage;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * A {@link ChunkingModel} backed by Spring AI: it translates between the core's model contract and
 * Spring AI, so a team already working within that ecosystem can supply a model without the core
 * knowing anything about it (ARCHITECTURE.md, section 5).
 *
 * <p><strong>Provider neutrality.</strong> The adapter is written against Spring AI's
 * provider-neutral abstractions alone — {@link ChatModel}, {@link Prompt}, {@link ChatResponse},
 * {@link TokenCountEstimator} — and against nothing else. It names no provider, reaches for no
 * provider-specific type, and takes no part in choosing or configuring one: the caller supplies a
 * {@code ChatModel} that is already configured, and the adapter executes requests against it. The
 * higher-level {@code ChatClient} is deliberately not used, because its advisors, memory,
 * templating, and structured-output conversion are the interpretation and recovery that the core
 * reserves to itself (CONTRIBUTING_ARCHITECTURE.md, section 4.1.2).
 *
 * <p><strong>The context-window limit is supplied by the caller.</strong> No neutral Spring AI type
 * reports the size of a model's context window, so {@link #maxInputTokens()} returns the value
 * given to the builder. It is never guessed: there is no discovery, no reflection, no provider
 * lookup, and no default (CONTRIBUTING_ARCHITECTURE.md, section 4.1.3). <strong>The caller owns the
 * correctness of that value.</strong> A value larger than the model's true limit silently breaks
 * window planning by overflowing the context; a smaller one merely wastes budget.
 *
 * <p><strong>Token estimation.</strong> {@link #estimateTokens(String)} delegates to the {@link
 * TokenCountEstimator} the caller supplied, so the accuracy of the estimate — and therefore whether
 * window planning under-counts and risks overflow or over-counts and wastes budget — is a property
 * of the estimator chosen, not of this adapter.
 *
 * <p><strong>Faithful delivery.</strong> The adapter translates a request, executes it, translates
 * the response, and surfaces a failure to obtain one. It does not retry, recover, validate, or
 * interpret the model's answer; those remain the core's own work (SPI.md, section 4). The raw text
 * is delivered exactly as the model produced it, including when the model produced an empty string
 * — an empty answer is a real answer, and judging it is the core's responsibility.
 *
 * <p><strong>Absence is a failure, not an empty answer.</strong> When a {@link ChatResponse} is
 * structurally absent where the contract requires content — no response at all, no generations, no
 * output message, or no text — the adapter throws {@link ModelException} rather than fabricating an
 * empty result. Reporting the failure honestly lets the core apply its retry and fallback policy; a
 * fabricated empty answer would deny it that chance (SPI.md, section 7). Token usage is treated
 * differently: it is metadata about the execution rather than the answer itself, so when it is
 * missing the adapter reports zero counts rather than failing.
 *
 * <p><strong>Thread safety.</strong> This class is immutable and holds no per-call state, so it is
 * safe for concurrent use provided the {@code ChatModel} and {@code TokenCountEstimator} it was
 * given are (both are contractually expected to be).
 *
 * <p>This class is {@code final} and constructed only through its {@link #builder() builder}.
 */
public final class SpringAiChunkingModel implements ChunkingModel {

    private final ChatModel chatModel;
    private final TokenCountEstimator tokenCountEstimator;
    private final int maxInputTokens;

    private SpringAiChunkingModel(Builder builder) {
        this.chatModel = builder.chatModel;
        this.tokenCountEstimator = builder.tokenCountEstimator;
        this.maxInputTokens = builder.maxInputTokens;
    }

    /**
     * Starts building an adapter.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Executes the given request against the Spring AI {@link ChatModel} and returns its raw
     * response.
     *
     * @param request the framed request for a boundary decision about one window; must not be
     *     {@code null}
     * @return the model's raw response together with its token usage
     * @throws NullPointerException if {@code request} is {@code null}
     * @throws ModelException if Spring AI fails, or if the response carries no answer
     */
    @Override
    public ModelResponse execute(ChunkingRequest request) {
        Objects.requireNonNull(request, "request");

        ChatResponse chatResponse;
        try {
            chatResponse = chatModel.call(toPrompt(request));
        } catch (RuntimeException e) {
            throw new ModelException(
                    "The Spring AI ChatModel failed to execute the chunking request", e);
        }
        return toModelResponse(chatResponse);
    }

    /**
     * The context-window limit supplied to the builder.
     *
     * @return the configured limit, in tokens; always positive
     */
    @Override
    public int maxInputTokens() {
        return maxInputTokens;
    }

    /**
     * Estimates the token count by delegating to the configured {@link TokenCountEstimator}.
     *
     * @param text the text to estimate
     * @return the estimator's count
     */
    @Override
    public int estimateTokens(String text) {
        return tokenCountEstimator.estimate(text);
    }

    /**
     * Maps a request onto a Spring AI {@link Prompt}: the rendered prompt becomes the single user
     * message, and the core's generation hints become the neutral chat options.
     *
     * <p>The model name is deliberately never set. Which model answers is a property of the {@code
     * ChatModel} the caller configured and supplied, and overriding it here would let the adapter
     * take part in choosing a provider's model.
     *
     * <p>The request's optional response-shape hint is not mapped: expressing it would require
     * provider-native structured output, which the neutral abstraction does not offer and which the
     * adapter must not reach for. The contract permits an adapter that cannot use the hint to
     * ignore it.
     */
    private Prompt toPrompt(ChunkingRequest request) {
        ChatOptions options =
                ChatOptions.builder()
                        .temperature(request.temperature())
                        .maxTokens(request.maxOutputTokens())
                        .build();
        return new Prompt(new UserMessage(request.prompt()), options);
    }

    /**
     * Maps a Spring AI {@link ChatResponse} back onto the core's {@link ModelResponse}, taking the
     * first generation and rejecting a response that carries no answer.
     *
     * <p>Only the first generation is considered: the core asks one question about one window and
     * expects one answer, so any further generations are alternatives it did not ask for.
     */
    private ModelResponse toModelResponse(ChatResponse chatResponse) {
        if (chatResponse == null) {
            throw new ModelException("The Spring AI ChatModel returned no response");
        }
        List<Generation> generations = chatResponse.getResults();
        if (generations == null || generations.isEmpty()) {
            throw new ModelException("The Spring AI response carried no generation");
        }
        Generation generation = generations.get(0);
        if (generation == null) {
            throw new ModelException("The Spring AI response carried no generation");
        }
        AssistantMessage output = generation.getOutput();
        if (output == null) {
            throw new ModelException("The Spring AI generation carried no output message");
        }
        String rawText = output.getText();
        if (rawText == null) {
            throw new ModelException("The Spring AI output message carried no text");
        }
        return new ModelResponse(rawText, toTokenUsage(chatResponse.getMetadata()));
    }

    /**
     * Maps Spring AI's token usage onto the core's {@link TokenUsage}.
     *
     * <p>Usage describes the execution rather than the answer, so its absence is reported as zero
     * counts rather than as a failure: metadata may be missing, the usage may be absent or an
     * {@code EmptyUsage}, and either count may be {@code null}. A negative count is likewise
     * normalized to zero, since the core's contract admits only non-negative counts.
     */
    private TokenUsage toTokenUsage(ChatResponseMetadata metadata) {
        if (metadata == null) {
            return new TokenUsage(0, 0);
        }
        Usage usage = metadata.getUsage();
        if (usage == null) {
            return new TokenUsage(0, 0);
        }
        return new TokenUsage(
                nonNegative(usage.getPromptTokens()), nonNegative(usage.getCompletionTokens()));
    }

    /** Reads a Spring AI token count, treating an absent or negative one as zero. */
    private static int nonNegative(Integer count) {
        return count == null || count < 0 ? 0 : count;
    }

    /** Builds a {@link SpringAiChunkingModel}. Every collaborator is required. */
    public static final class Builder {

        private ChatModel chatModel;
        private TokenCountEstimator tokenCountEstimator;
        private int maxInputTokens;

        private Builder() {}

        /**
         * Sets the already-configured Spring AI model that will answer requests. Required.
         *
         * @param chatModel the model to execute against; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code chatModel} is {@code null}
         */
        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
            return this;
        }

        /**
         * Sets the estimator used to count tokens for window planning. Required.
         *
         * @param tokenCountEstimator the estimator to delegate to; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code tokenCountEstimator} is {@code null}
         */
        public Builder tokenCountEstimator(TokenCountEstimator tokenCountEstimator) {
            this.tokenCountEstimator =
                    Objects.requireNonNull(tokenCountEstimator, "tokenCountEstimator");
            return this;
        }

        /**
         * Sets the context-window limit of the model being wrapped. Required.
         *
         * <p>No neutral Spring AI type reports this, so the caller supplies it and owns its
         * correctness: a value larger than the model's true limit silently breaks window planning.
         *
         * @param maxInputTokens the limit, in tokens; must be positive
         * @return this builder
         * @throws IllegalArgumentException if {@code maxInputTokens} is not positive
         */
        public Builder maxInputTokens(int maxInputTokens) {
            if (maxInputTokens <= 0) {
                throw new IllegalArgumentException(
                        "maxInputTokens must be positive: " + maxInputTokens);
            }
            this.maxInputTokens = maxInputTokens;
            return this;
        }

        /**
         * Completes construction, verifying that every required collaborator is present.
         *
         * @return a new adapter
         * @throws IllegalStateException if the model, the estimator, or the context-window limit
         *     has not been supplied
         */
        public SpringAiChunkingModel build() {
            if (chatModel == null) {
                throw new IllegalStateException(
                        "chatModel is required and was not supplied to the builder");
            }
            if (tokenCountEstimator == null) {
                throw new IllegalStateException(
                        "tokenCountEstimator is required and was not supplied to the builder");
            }
            if (maxInputTokens == 0) {
                throw new IllegalStateException(
                        "maxInputTokens is required and was not supplied to the builder");
            }
            return new SpringAiChunkingModel(this);
        }
    }
}
