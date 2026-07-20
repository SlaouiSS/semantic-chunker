package io.github.semanticchunker.document;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link DocumentUnit} representing a run of prose: the ordinary body text of a document, and the
 * most common kind of unit (MODEL.md, section 4).
 *
 * @param provenance the unit's provenance; must not be {@code null}
 * @param content the paragraph text; must not be {@code null}
 * @param metadata peripheral metadata; {@code null} is treated as empty
 */
public record Paragraph(Provenance provenance, String content, Map<String, String> metadata)
        implements DocumentUnit {

    /** Validates and defensively copies. */
    public Paragraph {
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(content, "content");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public Optional<String> text() {
        return Optional.of(content);
    }
}
