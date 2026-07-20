/**
 * The {@link io.github.semanticchunker.model.ChunkingModel} extension contract and its value types.
 *
 * <p>{@code ChunkingModel} is one of the library's two SPIs: the core-owned abstraction over a
 * language model (SPI.md, section 4). An adapter implements it to execute a {@link
 * io.github.semanticchunker.model.ChunkingRequest} against a provider and return a {@link
 * io.github.semanticchunker.model.ModelResponse}, and to report the model's context-window limit
 * and token estimates that window planning consults. No provider concept is exposed through this
 * package.
 */
package io.github.semanticchunker.model;
