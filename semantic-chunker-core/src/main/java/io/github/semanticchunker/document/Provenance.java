package io.github.semanticchunker.document;

/**
 * Where a {@link DocumentUnit} came from in the original source, carried unchanged from
 * normalization through to the final chunks (MODEL.md, section 10).
 *
 * <p>Provenance is mandatory and composed of exactly three facts (CONTRIBUTING_ARCHITECTURE.md,
 * section 7.2.3):
 *
 * <ul>
 *   <li>the {@code globalOrdinal} — the unit's stable identity and, because it is assigned in
 *       document order, its position in the sequence. This is the value boundary decisions are
 *       expressed against and the identity on which merging depends; it is assigned once at
 *       normalization and never changes (MODEL.md, section 5).
 *   <li>the {@code page} on which the material originated;
 *   <li>the character span {@code [startOffset, endOffset)} locating the unit within the document's
 *       <em>normalized text</em>, as defined below.
 * </ul>
 *
 * <p><strong>Normalized text.</strong> The normalized text of a {@link PreparedDocument} is the
 * concatenation, in global-ordinal order and with no separator between units, of the textual
 * content of every unit it contains — each unit contributing its {@link DocumentUnit#text()} where
 * it has text, and the empty string where it has none.
 *
 * <p><strong>What the offsets address.</strong> The offsets address that normalized text. They are
 * zero-based, half-open ({@code startOffset} inclusive, {@code endOffset} exclusive), and measured
 * in Java {@code String} UTF-16 code units, exactly as {@link String#substring(int, int)}
 * interprets them. <strong>They never address the original document's bytes.</strong> Every
 * extractor obeys this rule identically, whatever technology stands behind it: for a PDF, a scan,
 * or a photograph the original source is binary and a character span into it is undefined, so the
 * normalized text — which every extractor produces — is the one coordinate space in which the span
 * is meaningful for all of them.
 *
 * <p><strong>The governing invariant</strong>, for every unit of a prepared document:
 *
 * <pre>{@code
 * normalizedText.substring(startOffset, endOffset).equals(unit.text().orElse(""))
 * }</pre>
 *
 * <p>Three consequences follow, and an extractor must respect them:
 *
 * <ul>
 *   <li>a unit carrying no text has a zero-length span, {@code startOffset == endOffset};
 *   <li>the spans are contiguous and non-overlapping, so a unit's {@code startOffset} is the sum of
 *       the text lengths of every unit before it;
 *   <li>a {@link Table} contributes its Markdown serialization, that being its textual content.
 * </ul>
 *
 * <p>This record validates only what a single provenance value can see — that the offsets are
 * non-negative and correctly ordered. The invariant above spans a whole prepared document and is
 * therefore an obligation on the extractor that produced it (SPI.md, section 3).
 *
 * @param globalOrdinal the unit's stable identity and document-order position; must be non-negative
 * @param page the page the material originated on; must be non-negative
 * @param startOffset the inclusive start of the unit's span in the normalized text, in UTF-16 code
 *     units; must be non-negative
 * @param endOffset the exclusive end of the unit's span in the normalized text, in UTF-16 code
 *     units; must be at least {@code startOffset}
 */
public record Provenance(int globalOrdinal, int page, int startOffset, int endOffset) {

    /** Validates the provenance facts. */
    public Provenance {
        if (globalOrdinal < 0) {
            throw new IllegalArgumentException(
                    "globalOrdinal must be non-negative: " + globalOrdinal);
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative: " + page);
        }
        if (startOffset < 0) {
            throw new IllegalArgumentException("startOffset must be non-negative: " + startOffset);
        }
        if (endOffset < startOffset) {
            throw new IllegalArgumentException(
                    "endOffset (" + endOffset + ") must be >= startOffset (" + startOffset + ")");
        }
    }
}
