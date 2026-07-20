/**
 * Non-public API. Every type in this package is internal and MAY change or be deleted in any
 * release without notice (CONTRIBUTING_ARCHITECTURE.md, sections 4.2.2–4.2.3).
 *
 * <p>This package holds the pipeline's internal components: the {@code ChunkingPipeline}
 * orchestrator and the stage collaborators it drives — window planning, prompt rendering, model
 * execution, response validation, boundary merging, chunk construction, and result assembly —
 * together with the internal representations that flow between them ({@code Window}, {@code
 * ValidatedBoundaryDecision}, {@code MergedBoundaries}). These are not part of the public surface
 * and are not extension points.
 *
 * <p>Only {@code ChunkingPipeline} is Java-public, so that the {@link
 * io.github.semanticchunker.chunker.SemanticChunker} facade in the parent package can construct and
 * delegate to it; it remains non-public API all the same. Every other type here is package-private.
 * The stages that carry out documented but unspecified-in-code algorithms are wired and
 * orchestrated but left intentionally unfinished until the algorithm phase.
 */
package io.github.semanticchunker.chunker.internal;
