/**
 * The Unstructured adapter: a {@link io.github.semanticchunker.extraction.DocumentExtractor}
 * implementation that turns documents into the library's normalized representation by way of the
 * Unstructured partition API (ARCHITECTURE.md, section 5).
 *
 * <p>This module depends on the core and satisfies one of the core's two abstractions; the core
 * does not depend on it. Everything specific to Unstructured is confined to this module and never
 * surfaces in the library's terms.
 */
package io.github.semanticchunker.unstructured;
