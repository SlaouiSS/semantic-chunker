/**
 * The {@link io.github.semanticchunker.extraction.DocumentExtractor} extension contract.
 *
 * <p>{@code DocumentExtractor} is one of the library's two SPIs: the adapter for an extraction
 * technology, which turns a {@link io.github.semanticchunker.document.DocumentSource} into a {@link
 * io.github.semanticchunker.document.PreparedDocument} expressed entirely in the library's own
 * terms (SPI.md, section 3). No extraction-technology concept is exposed through this package.
 */
package io.github.semanticchunker.extraction;
