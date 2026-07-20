/**
 * The public entry point to the library and the values a chunking run produces.
 *
 * <p>{@link io.github.semanticchunker.chunker.SemanticChunker} is the single entry point (API.md,
 * section 2): a caller builds it once with a {@link
 * io.github.semanticchunker.extraction.DocumentExtractor} and a {@link
 * io.github.semanticchunker.model.ChunkingModel}, then chunks documents. A run returns a {@link
 * io.github.semanticchunker.chunker.ChunkingResult} carrying the {@link
 * io.github.semanticchunker.chunker.SemanticChunk}s in order together with an honest account of how
 * they were produced.
 *
 * <p>The internal pipeline collaborators live in {@code io.github.semanticchunker.chunker.internal}
 * and are not part of the public surface.
 */
package io.github.semanticchunker.chunker;
