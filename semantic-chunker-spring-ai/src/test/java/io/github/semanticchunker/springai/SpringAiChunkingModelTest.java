package io.github.semanticchunker.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.semanticchunker.model.ChunkingRequest;
import io.github.semanticchunker.model.ModelException;
import io.github.semanticchunker.model.ModelResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/** Tests {@link SpringAiChunkingModel}. */
class SpringAiChunkingModelTest {

    private static final ChunkingRequest REQUEST =
            new ChunkingRequest("the framed prompt", "json-schema", 0.2d, 512);

    private final ChatModel chatModel = mock(ChatModel.class);
    private final TokenCountEstimator tokenCountEstimator = mock(TokenCountEstimator.class);

    private SpringAiChunkingModel adapter() {
        return SpringAiChunkingModel.builder()
                .chatModel(chatModel)
                .tokenCountEstimator(tokenCountEstimator)
                .maxInputTokens(8192)
                .build();
    }

    /** Answers with a response whose single generation carries the given text and no usage. */
    private void respondWith(String text) {
        respondWith(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    }

    private void respondWith(ChatResponse response) {
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }

    /** Builds a response carrying the given usage. */
    private static ChatResponse responseWithUsage(Usage usage) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage("text"))),
                ChatResponseMetadata.builder().usage(usage).build());
    }

    @Nested
    class BuilderTest {

        @Test
        void buildsWithEveryCollaboratorSupplied() {
            SpringAiChunkingModel model = adapter();

            assertThat(model).isNotNull();
            assertThat(model.maxInputTokens()).isEqualTo(8192);
        }

        @Test
        void rejectsNullChatModel() {
            assertThatNullPointerException()
                    .isThrownBy(() -> SpringAiChunkingModel.builder().chatModel(null))
                    .withMessageContaining("chatModel");
        }

        @Test
        void rejectsNullTokenCountEstimator() {
            assertThatNullPointerException()
                    .isThrownBy(() -> SpringAiChunkingModel.builder().tokenCountEstimator(null))
                    .withMessageContaining("tokenCountEstimator");
        }

        @Test
        void rejectsZeroMaxInputTokens() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> SpringAiChunkingModel.builder().maxInputTokens(0))
                    .withMessageContaining("maxInputTokens");
        }

        @Test
        void rejectsNegativeMaxInputTokens() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> SpringAiChunkingModel.builder().maxInputTokens(-1))
                    .withMessageContaining("maxInputTokens");
        }

        @Test
        void rejectsMissingChatModel() {
            assertThatIllegalStateException()
                    .isThrownBy(
                            () ->
                                    SpringAiChunkingModel.builder()
                                            .tokenCountEstimator(tokenCountEstimator)
                                            .maxInputTokens(1024)
                                            .build())
                    .withMessageContaining("chatModel");
        }

        @Test
        void rejectsMissingTokenCountEstimator() {
            assertThatIllegalStateException()
                    .isThrownBy(
                            () ->
                                    SpringAiChunkingModel.builder()
                                            .chatModel(chatModel)
                                            .maxInputTokens(1024)
                                            .build())
                    .withMessageContaining("tokenCountEstimator");
        }

        @Test
        void rejectsMissingMaxInputTokens() {
            assertThatIllegalStateException()
                    .isThrownBy(
                            () ->
                                    SpringAiChunkingModel.builder()
                                            .chatModel(chatModel)
                                            .tokenCountEstimator(tokenCountEstimator)
                                            .build())
                    .withMessageContaining("maxInputTokens");
        }
    }

    @Nested
    class ModelIntrospectionTest {

        @Test
        void estimateTokensDelegatesToTheEstimator() {
            when(tokenCountEstimator.estimate("some text")).thenReturn(42);

            assertThat(adapter().estimateTokens("some text")).isEqualTo(42);

            verify(tokenCountEstimator).estimate("some text");
        }

        @Test
        void maxInputTokensReturnsTheConfiguredValue() {
            SpringAiChunkingModel model =
                    SpringAiChunkingModel.builder()
                            .chatModel(chatModel)
                            .tokenCountEstimator(tokenCountEstimator)
                            .maxInputTokens(128_000)
                            .build();

            assertThat(model.maxInputTokens()).isEqualTo(128_000);
        }

        @Test
        void neverConsultsTheChatModelToReportItsLimit() {
            adapter().maxInputTokens();

            verify(chatModel, never()).call(any(Prompt.class));
        }
    }

    @Nested
    class RequestMappingTest {

        private Prompt executeAndCapturePrompt() {
            respondWith("decision");
            adapter().execute(REQUEST);

            ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
            verify(chatModel).call(prompt.capture());
            return prompt.getValue();
        }

        @Test
        void sendsTheRenderedPromptAsASingleUserMessage() {
            List<Message> messages = executeAndCapturePrompt().getInstructions();

            assertThat(messages).hasSize(1);
            assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
            assertThat(messages.get(0).getMessageType()).isEqualTo(MessageType.USER);
            assertThat(messages.get(0).getText()).isEqualTo("the framed prompt");
        }

        @Test
        void propagatesTheTemperature() {
            ChatOptions options = executeAndCapturePrompt().getOptions();

            assertThat(options.getTemperature()).isEqualTo(0.2d);
        }

        @Test
        void propagatesTheOutputTokenBudgetAsMaxTokens() {
            ChatOptions options = executeAndCapturePrompt().getOptions();

            assertThat(options.getMaxTokens()).isEqualTo(512);
        }

        @Test
        void neverSetsTheModelExplicitly() {
            ChatOptions options = executeAndCapturePrompt().getOptions();

            assertThat(options.getModel()).isNull();
        }

        @Test
        void rejectsNullRequest() {
            assertThatNullPointerException()
                    .isThrownBy(() -> adapter().execute(null))
                    .withMessageContaining("request");
        }
    }

    @Nested
    class ResponseMappingTest {

        @Test
        void mapsASuccessfulResponse() {
            respondWith("the model's decision");

            ModelResponse response = adapter().execute(REQUEST);

            assertThat(response.rawText()).isEqualTo("the model's decision");
        }

        @Test
        void deliversAnEmptyStringAnswerFaithfully() {
            respondWith("");

            assertThat(adapter().execute(REQUEST).rawText()).isEmpty();
        }

        @Test
        void takesOnlyTheFirstGeneration() {
            respondWith(
                    new ChatResponse(
                            List.of(
                                    new Generation(new AssistantMessage("first")),
                                    new Generation(new AssistantMessage("second")))));

            assertThat(adapter().execute(REQUEST).rawText()).isEqualTo("first");
        }

        @Test
        void failsWhenTheChatModelReturnsNoResponse() {
            respondWith((ChatResponse) null);

            assertThatExceptionOfType(ModelException.class)
                    .isThrownBy(() -> adapter().execute(REQUEST))
                    .withMessageContaining("no response");
        }

        @Test
        void failsWhenTheResponseCarriesNoResults() {
            ChatResponse response = mock(ChatResponse.class);
            when(response.getResults()).thenReturn(null);
            respondWith(response);

            assertThatExceptionOfType(ModelException.class)
                    .isThrownBy(() -> adapter().execute(REQUEST))
                    .withMessageContaining("no generation");
        }

        @Test
        void failsWhenTheResponseCarriesEmptyResults() {
            respondWith(new ChatResponse(Collections.emptyList()));

            assertThatExceptionOfType(ModelException.class)
                    .isThrownBy(() -> adapter().execute(REQUEST))
                    .withMessageContaining("no generation");
        }

        @Test
        void failsWhenTheFirstGenerationIsNull() {
            List<Generation> generations = new ArrayList<>();
            generations.add(null);
            ChatResponse response = mock(ChatResponse.class);
            when(response.getResults()).thenReturn(generations);
            respondWith(response);

            assertThatExceptionOfType(ModelException.class)
                    .isThrownBy(() -> adapter().execute(REQUEST))
                    .withMessageContaining("no generation");
        }

        @Test
        void failsWhenTheGenerationCarriesNoOutput() {
            Generation generation = mock(Generation.class);
            when(generation.getOutput()).thenReturn(null);
            ChatResponse response = mock(ChatResponse.class);
            when(response.getResults()).thenReturn(List.of(generation));
            respondWith(response);

            assertThatExceptionOfType(ModelException.class)
                    .isThrownBy(() -> adapter().execute(REQUEST))
                    .withMessageContaining("no output message");
        }

        @Test
        void failsWhenTheOutputMessageCarriesNoText() {
            respondWith(new ChatResponse(List.of(new Generation(new AssistantMessage(null)))));

            assertThatExceptionOfType(ModelException.class)
                    .isThrownBy(() -> adapter().execute(REQUEST))
                    .withMessageContaining("no text");
        }
    }

    @Nested
    class TokenUsageMappingTest {

        @Test
        void mapsPromptAndCompletionTokens() {
            respondWith(responseWithUsage(new DefaultUsage(31, 17)));

            ModelResponse response = adapter().execute(REQUEST);

            assertThat(response.tokenUsage().inputTokens()).isEqualTo(31);
            assertThat(response.tokenUsage().outputTokens()).isEqualTo(17);
        }

        @Test
        void reportsZeroForAnEmptyUsage() {
            respondWith(responseWithUsage(new EmptyUsage()));

            ModelResponse response = adapter().execute(REQUEST);

            assertThat(response.tokenUsage().inputTokens()).isZero();
            assertThat(response.tokenUsage().outputTokens()).isZero();
        }

        @Test
        void reportsZeroWhenTheMetadataIsAbsent() {
            ChatResponse response = mock(ChatResponse.class);
            when(response.getResults())
                    .thenReturn(List.of(new Generation(new AssistantMessage("text"))));
            when(response.getMetadata()).thenReturn(null);
            respondWith(response);

            ModelResponse mapped = adapter().execute(REQUEST);

            assertThat(mapped.rawText()).isEqualTo("text");
            assertThat(mapped.tokenUsage().inputTokens()).isZero();
            assertThat(mapped.tokenUsage().outputTokens()).isZero();
        }

        @Test
        void reportsZeroWhenTheUsageIsAbsent() {
            ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
            when(metadata.getUsage()).thenReturn(null);
            ChatResponse response = mock(ChatResponse.class);
            when(response.getResults())
                    .thenReturn(List.of(new Generation(new AssistantMessage("text"))));
            when(response.getMetadata()).thenReturn(metadata);
            respondWith(response);

            ModelResponse mapped = adapter().execute(REQUEST);

            assertThat(mapped.tokenUsage().inputTokens()).isZero();
            assertThat(mapped.tokenUsage().outputTokens()).isZero();
        }

        @Test
        void reportsZeroWhenACountIsAbsent() {
            Usage usage = mock(Usage.class);
            when(usage.getPromptTokens()).thenReturn(null);
            when(usage.getCompletionTokens()).thenReturn(null);
            respondWith(responseWithUsage(usage));

            ModelResponse mapped = adapter().execute(REQUEST);

            assertThat(mapped.tokenUsage().inputTokens()).isZero();
            assertThat(mapped.tokenUsage().outputTokens()).isZero();
        }

        @Test
        void normalizesANegativeCountToZero() {
            Usage usage = mock(Usage.class);
            when(usage.getPromptTokens()).thenReturn(-5);
            when(usage.getCompletionTokens()).thenReturn(-1);
            respondWith(responseWithUsage(usage));

            ModelResponse mapped = adapter().execute(REQUEST);

            assertThat(mapped.tokenUsage().inputTokens()).isZero();
            assertThat(mapped.tokenUsage().outputTokens()).isZero();
        }
    }

    /**
     * Verifies that a Spring AI failure is surfaced as a {@link ModelException} with its cause
     * intact.
     *
     * <p>Spring AI's own {@code TransientAiException} and {@code NonTransientAiException} live in
     * {@code spring-ai-retry}, which this module deliberately does not depend on: the adapter
     * depends on the provider-neutral abstractions alone, and it does not retry
     * (CONTRIBUTING_ARCHITECTURE.md, section 4.1.2). The adapter therefore wraps by the unchecked
     * type every Spring AI failure shares rather than by any particular class, and these stand-ins
     * exercise exactly that.
     */
    @Nested
    class FailureMappingTest {

        /** A stand-in for a retryable Spring AI failure, such as a rate limit. */
        private static final class StubTransientAiException extends RuntimeException {
            StubTransientAiException(String message) {
                super(message);
            }

            StubTransientAiException(String message, Throwable cause) {
                super(message, cause);
            }
        }

        /** A stand-in for a fatal Spring AI failure, such as bad credentials. */
        private static final class StubNonTransientAiException extends RuntimeException {
            StubNonTransientAiException(String message) {
                super(message);
            }
        }

        /** Fails the chat model with the given exception and returns the wrapper it produced. */
        private ModelException executeExpectingFailure(RuntimeException failure) {
            when(chatModel.call(any(Prompt.class))).thenThrow(failure);

            return catchThrowableOfType(ModelException.class, () -> adapter().execute(REQUEST));
        }

        @Test
        void wrapsATransientSpringAiFailure() {
            StubTransientAiException failure = new StubTransientAiException("rate limited");

            assertThat(executeExpectingFailure(failure)).hasCause(failure);
        }

        @Test
        void wrapsANonTransientSpringAiFailure() {
            StubNonTransientAiException failure =
                    new StubNonTransientAiException("bad credentials");

            assertThat(executeExpectingFailure(failure)).hasCause(failure);
        }

        @Test
        void wrapsAnyOtherRuntimeFailure() {
            IllegalStateException failure = new IllegalStateException("the client is closed");

            assertThat(executeExpectingFailure(failure)).hasCause(failure);
        }

        @Test
        void preservesTheOriginalCauseUnchanged() {
            Throwable root = new java.net.SocketTimeoutException("read timed out");
            StubTransientAiException failure =
                    new StubTransientAiException("upstream timed out", root);

            ModelException wrapped = executeExpectingFailure(failure);

            assertThat(wrapped.getCause()).isSameAs(failure);
            assertThat(wrapped.getCause().getCause()).isSameAs(root);
        }

        @Test
        void namesWhatWasBeingAttempted() {
            assertThat(executeExpectingFailure(new StubTransientAiException("boom")))
                    .hasMessageContaining("chunking request");
        }

        @Test
        void doesNotRetryAFailedExecution() {
            when(chatModel.call(any(Prompt.class))).thenThrow(new StubTransientAiException("boom"));

            assertThatExceptionOfType(ModelException.class)
                    .isThrownBy(() -> adapter().execute(REQUEST));

            verify(chatModel).call(any(Prompt.class));
        }

        @Test
        void doesNotSwallowAFailureAsAnEmptyAnswer() {
            when(chatModel.call(any(Prompt.class))).thenThrow(new StubTransientAiException("boom"));

            assertThatExceptionOfType(ModelException.class)
                    .isThrownBy(() -> adapter().execute(REQUEST));
        }

        @Test
        void doesNotWrapAnAbsenceFailureTwice() {
            respondWith(new ChatResponse(Collections.emptyList()));

            assertThatExceptionOfType(ModelException.class)
                    .isThrownBy(() -> adapter().execute(REQUEST))
                    .withNoCause();
        }
    }
}
