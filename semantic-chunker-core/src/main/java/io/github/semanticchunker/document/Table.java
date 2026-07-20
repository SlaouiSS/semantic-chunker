package io.github.semanticchunker.document;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link DocumentUnit} representing tabular content: information arranged in rows and columns.
 *
 * <p>A table has a decided representation (CONTRIBUTING_ARCHITECTURE.md, section 7.2.5). Its {@link
 * #markdown()} serialization is simultaneously what the language model sees and what lands in the
 * resulting chunk, and its structured {@link #cells()} are retained so downstream consumers can
 * re-render it. The table's {@link #text()} is its Markdown serialization.
 *
 * @param provenance the unit's provenance; must not be {@code null}
 * @param markdown the canonical Markdown serialization; must not be {@code null}
 * @param cells the retained structured cell data as rows of cell text; {@code null} is treated as
 *     empty and the contents are defensively copied
 * @param metadata peripheral metadata; {@code null} is treated as empty
 */
public record Table(
        Provenance provenance,
        String markdown,
        List<List<String>> cells,
        Map<String, String> metadata)
        implements DocumentUnit {

    /** Validates and defensively (deeply) copies. */
    public Table {
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(markdown, "markdown");
        cells =
                cells == null
                        ? List.of()
                        : cells.stream()
                                .map(List::copyOf)
                                .collect(java.util.stream.Collectors.toUnmodifiableList());
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public Optional<String> text() {
        return Optional.of(markdown);
    }
}
