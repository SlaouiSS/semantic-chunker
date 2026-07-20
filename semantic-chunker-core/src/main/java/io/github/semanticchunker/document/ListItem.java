package io.github.semanticchunker.document;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link DocumentUnit} representing a single entry within an enumerated or bulleted list — one
 * element of a sequence of related points (MODEL.md, section 4).
 *
 * @param provenance the unit's provenance; must not be {@code null}
 * @param content the list-item text; must not be {@code null}
 * @param metadata peripheral metadata; {@code null} is treated as empty
 */
public record ListItem(Provenance provenance, String content, Map<String, String> metadata)
        implements DocumentUnit {

    /** Validates and defensively copies. */
    public ListItem {
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(content, "content");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public Optional<String> text() {
        return Optional.of(content);
    }
}
