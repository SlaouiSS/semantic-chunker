package io.github.semanticchunker.document;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link DocumentUnit} representing a graphical element.
 *
 * <p>Because the pipeline reasons over text, an image participates only through whatever textual
 * rendering stands in for it — a caption, a description, or recovered text — and where no such
 * rendering exists it contributes nothing to boundary reasoning (MODEL.md, section 4). Its textual
 * content is therefore optional (CONTRIBUTING_ARCHITECTURE.md, section 7.2.6).
 *
 * @param provenance the unit's provenance; must not be {@code null}
 * @param caption the caption, alt text, or recovered text standing in for the image; may be {@code
 *     null} when none exists
 * @param metadata peripheral metadata; {@code null} is treated as empty
 */
public record Image(Provenance provenance, String caption, Map<String, String> metadata)
        implements DocumentUnit {

    /** Validates and defensively copies. */
    public Image {
        Objects.requireNonNull(provenance, "provenance");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public Optional<String> text() {
        return Optional.ofNullable(caption);
    }
}
