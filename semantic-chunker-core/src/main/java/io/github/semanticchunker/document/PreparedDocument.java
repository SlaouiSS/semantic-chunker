package io.github.semanticchunker.document;

import java.util.List;
import java.util.Map;

/**
 * The normalized representation of a document: the single, uniform form the semantic-chunking
 * pipeline consumes, independent of how the document was extracted (MODEL.md, section 3).
 *
 * <p>A prepared document is a flat, ordered sequence of typed {@link DocumentUnit} — the index
 * space over which chunk boundaries are expressed (CONTRIBUTING_ARCHITECTURE.md, section 7.2).
 * Order is intrinsic: the units appear in the sequence of the original document, and each unit's
 * global ordinal (its stable identity) is assigned once, in that order, and never changes.
 *
 * <p>It is immutable and self-contained; the units belong to it and do not exist independently of
 * it. The {@code metadata} map is document-level, peripheral extension space that core reasoning
 * does not read from.
 *
 * @param units the ordered document units; must not be {@code null}, defensively copied and exposed
 *     as an unmodifiable list
 * @param metadata document-level peripheral metadata; {@code null} is treated as empty
 */
public record PreparedDocument(List<DocumentUnit> units, Map<String, String> metadata) {

    /** Validates and defensively copies. */
    public PreparedDocument {
        units = List.copyOf(units);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
