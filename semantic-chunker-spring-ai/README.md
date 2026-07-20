# semantic-chunker-spring-ai

A Spring AI–backed `ChunkingModel` for `semantic-chunker`. It translates between the
core's model contract and Spring AI, so a language model reachable through Spring AI
can drive semantic chunking. It depends on the core; the core does not depend on it.

```java
ChatModel chatModel = /* your configured Spring AI ChatModel */;

ChunkingModel model = SpringAiChunkingModel.builder()
        .chatModel(chatModel)
        .tokenCountEstimator(new JTokkitTokenCountEstimator())
        .maxInputTokens(128_000)
        .build();

SemanticChunker chunker = SemanticChunker.builder()
        .documentExtractor(extractor)
        .chunkingModel(model)
        .build();
```

## Provider neutrality

The adapter is written against Spring AI's provider-neutral abstractions alone —
`ChatModel`, `Prompt`, `ChatResponse`, and `TokenCountEstimator` — and nothing else.
It names no provider and takes no part in choosing or configuring one: you supply a
`ChatModel` that is already configured, and the adapter executes requests against it.
Which providers you can reach is therefore whatever your Spring AI setup supports
(OpenAI, Anthropic, Gemini, and others).

The higher-level `ChatClient` is deliberately not used: its advisors, memory,
templating, and structured-output conversion are the interpretation and recovery that
the core reserves to itself.

## Configuration

Everything is configured through the builder; every collaborator is required.

| Option | Purpose |
| --- | --- |
| `chatModel(ChatModel)` | The already-configured Spring AI model that answers requests. |
| `tokenCountEstimator(TokenCountEstimator)` | Estimates token counts for window planning. Any Spring AI `TokenCountEstimator` works. |
| `maxInputTokens(int)` | The context-window size of the model being wrapped. |

**You own the correctness of `maxInputTokens`.** No provider-neutral Spring AI type
reports a model's context window, so the caller supplies it. A value larger than the
model's true limit silently breaks window planning by overflowing the context; a
smaller one merely wastes budget.

## Request and response mapping

- The rendered prompt becomes a single `UserMessage`; the requested temperature and
  output-token budget become the neutral `ChatOptions`. The model name is never set —
  which model answers is a property of the `ChatModel` you supplied.
- The first generation of the `ChatResponse` becomes the raw answer. An empty-string
  answer is delivered faithfully (the core judges it); a structurally absent answer —
  no response, no generation, no output, or no text — fails with `ModelException`.
- Token usage becomes the response's `TokenUsage`; when usage is absent it is reported
  as zero rather than as a failure, since usage describes the execution, not the answer.

## Failure handling

Every Spring AI failure is wrapped in a `ModelException` with the cause preserved; the
adapter does not retry, recover, or interpret the answer — those remain the library's
work. Nothing provider-specific leaks out.

## Thread safety

An instance is immutable and safe for concurrent use, provided the `ChatModel` and
`TokenCountEstimator` you supply are (both are contractually expected to be).

## Dependency choices

The adapter depends only on Spring AI's provider-neutral artifacts — `spring-ai-model`
(the `ChatModel` and neutral chat types) and `spring-ai-commons` (the
`TokenCountEstimator`), managed by the Spring AI 2.0.x BOM. It declares no provider
artifact, no `spring-ai-client-chat`, and no Spring Boot: a provider artifact on this
classpath would let provider-specific code compile, and the adapter avoids that by
construction. Spring AI 2.0.x requires Spring Framework 7, and Spring Boot 4 for
consumers who use Spring Boot; the adapter itself takes no Spring Boot dependency.
