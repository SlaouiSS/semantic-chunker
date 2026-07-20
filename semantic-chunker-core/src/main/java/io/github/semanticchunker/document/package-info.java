/**
 * The normalized document model: the single, uniform representation the semantic-chunking pipeline
 * consumes, independent of how a document was extracted (MODEL.md, sections 3–6).
 *
 * <p>The central type is {@link io.github.semanticchunker.document.PreparedDocument}, a flat,
 * ordered sequence of {@link io.github.semanticchunker.document.DocumentUnit} — a sealed hierarchy
 * whose members are {@link io.github.semanticchunker.document.Heading}, {@link
 * io.github.semanticchunker.document.Paragraph}, {@link
 * io.github.semanticchunker.document.ListItem}, {@link io.github.semanticchunker.document.Table},
 * {@link io.github.semanticchunker.document.Image}, and {@link
 * io.github.semanticchunker.document.PageBreak}. Every unit carries mandatory {@link
 * io.github.semanticchunker.document.Provenance}.
 *
 * <p>{@link io.github.semanticchunker.document.DocumentSource} is the honest input type an
 * extractor consumes. Every type in this package is immutable.
 */
package io.github.semanticchunker.document;
