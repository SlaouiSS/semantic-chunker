package io.github.semanticchunker.document;

import java.util.Map;
import java.util.Optional;

/**
 * The atomic, addressable element of a {@link PreparedDocument} — the smallest thing the library
 * reasons about, refers to, and expresses boundary decisions in terms of (MODEL.md, section 4).
 *
 * <p>This is a sealed hierarchy so that consumers can switch over a closed, exhaustively known set
 * of kinds (CONTRIBUTING_ARCHITECTURE.md, section 7.2.2). The permitted kinds are {@link Heading},
 * {@link Paragraph}, {@link ListItem}, {@link Table}, {@link Image}, and {@link PageBreak}. Not
 * every extraction technology recovers every kind; a prepared document composed only of the kinds a
 * particular technology could recover is complete and valid.
 *
 * <p>Every unit is immutable and carries:
 *
 * <ul>
 *   <li>mandatory {@link #provenance()} — including the global ordinal that is the unit's stable
 *       identity;
 *   <li>{@link #text()} — the unit's textual content, which may be absent for units that are not
 *       themselves textual (a boundary decision is purely textual, so a unit with no text does not
 *       influence chunking);
 *   <li>peripheral {@link #metadata()} — extractor-specific richness that core reasoning does not
 *       depend on.
 * </ul>
 */
public sealed interface DocumentUnit permits Heading, Paragraph, ListItem, Table, Image, PageBreak {

    /**
     * The unit's provenance, including the global ordinal that is its stable identity.
     *
     * @return the mandatory provenance, never {@code null}
     */
    Provenance provenance();

    /**
     * The unit's textual content, if any.
     *
     * @return the text, or an empty optional for a unit that carries no text
     */
    Optional<String> text();

    /**
     * Peripheral, extractor-specific descriptive information about the unit.
     *
     * <p>Metadata is deliberately marginal: core reasoning does not read from it
     * (CONTRIBUTING_ARCHITECTURE.md, section 7.2.7).
     *
     * @return an unmodifiable metadata map, never {@code null}, possibly empty
     */
    Map<String, String> metadata();
}
