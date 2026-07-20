package io.github.semanticchunker.document;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link DocumentUnit} representing the transition from one page of the source to the next.
 *
 * <p>It carries little or no content of its own but marks a structural fact about the document that
 * may matter both to boundary reasoning and to provenance (MODEL.md, section 4). Its {@link
 * #text()} is always empty.
 *
 * @param provenance the unit's provenance; must not be {@code null}
 * @param metadata peripheral metadata; {@code null} is treated as empty
 */
public record PageBreak(Provenance provenance, Map<String, String> metadata)
        implements DocumentUnit {

    /** Validates and defensively copies. */
    public PageBreak {
        Objects.requireNonNull(provenance, "provenance");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public Optional<String> text() {
        return Optional.empty();
    }
}
