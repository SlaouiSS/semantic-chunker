/**
 * Root package of the {@code semantic-chunker} core module.
 *
 * <p>The core carries the pipeline, the domain model, and the two extension contracts, and it
 * depends on no extraction technology and no model provider. The concepts are organized by feature
 * rather than by technical layer:
 *
 * <ul>
 *   <li>{@link io.github.semanticchunker.chunker} — the {@code SemanticChunker} entry point and the
 *       values a run produces.
 *   <li>{@link io.github.semanticchunker.document} — the normalized {@code PreparedDocument} and
 *       its sealed {@code DocumentUnit} hierarchy, plus the {@code DocumentSource} input type.
 *   <li>{@link io.github.semanticchunker.model} — the {@code ChunkingModel} SPI and its request and
 *       response value types.
 *   <li>{@link io.github.semanticchunker.extraction} — the {@code DocumentExtractor} SPI.
 * </ul>
 *
 * <p>This root package holds only {@link io.github.semanticchunker.SemanticChunkerException}, the
 * common supertype of the library's typed, unchecked exception hierarchy.
 */
package io.github.semanticchunker;
