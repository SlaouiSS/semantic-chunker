package io.github.semanticchunker.chunker.internal;

import io.github.semanticchunker.document.DocumentUnit;
import java.util.List;
import java.util.Objects;

/**
 * The single canonical textual representation of the units and the boundary decision, shared by
 * window planning and prompt construction so that the size a window is planned against is the size
 * it is later presented at (PROMPT_SPECIFICATION.md §5; WINDOW_PLANNING.md §1, §8).
 *
 * <p>This component owns the canonical rendering and serialization, and only that:
 *
 * <ul>
 *   <li>the <strong>content representation</strong> ({@link #renderContent(List)}) — the units of a
 *       window presented in document order, each as its global ordinal associated with its textual
 *       representation (PROMPT_SPECIFICATION.md §4, §5). Window planning estimates its size as the
 *       {@code contentCost} (WINDOW_PLANNING.md §8).
 *   <li>the <strong>maximal valid response</strong> ({@link #renderMaximalResponse(List)}) — the
 *       largest response validation could accept for a window: every unit of the window except its
 *       first, named by global ordinal (WINDOW_PLANNING.md §1, §8). Window planning estimates its
 *       size as the {@code outputReservation}.
 * </ul>
 *
 * <p>It also exposes the canonical serialization of a boundary decision ({@link
 * #renderResponse(List)}) and the rendering of a single unit ({@link #renderUnit(DocumentUnit)}),
 * which the renderings above are built from so that the format is defined in exactly one place.
 *
 * <p>This component contains no model-facing instruction: it holds no task description, no boundary
 * guidance, and no prompt text. It produces only the canonical texts. It performs no token
 * estimation and never touches the model; it plans no windows, builds no request, and executes
 * nothing. It is stateless, immutable, and therefore safe for concurrent use.
 *
 * <p>The representation is deterministic: identical inputs always produce identical text. It uses
 * only fixed markers and {@link Integer#toString(int)} for ordinals, so it depends on no locale, no
 * randomness, and no environment. It never includes a unit's provenance internals (page reference,
 * character offsets) or its metadata (PROMPT_SPECIFICATION.md §5, §7).
 */
final class RequestRepresentation {

    private static final String UNIT_MARKER_PREFIX = "[unit ";
    private static final String UNIT_MARKER_SUFFIX = "]";
    private static final String LINE_BREAK = "\n";

    private static final String RESPONSE_OPEN = "[";
    private static final String RESPONSE_CLOSE = "]";
    private static final String RESPONSE_SEPARATOR = ",";

    RequestRepresentation() {}

    /**
     * Renders one unit as its global ordinal associated with its textual representation
     * (PROMPT_SPECIFICATION.md §5).
     *
     * <p>The textual representation is the unit's own text as the domain model defines it: the text
     * for a textual unit, the canonical serialization for a table, and an explicit empty
     * representation for a unit that carries no textual content. Provenance internals and metadata
     * are never included.
     *
     * @param unit the unit to render; must not be {@code null}
     * @return the rendered unit
     */
    String renderUnit(DocumentUnit unit) {
        Objects.requireNonNull(unit, "unit");
        return UNIT_MARKER_PREFIX
                + Integer.toString(unit.provenance().globalOrdinal())
                + UNIT_MARKER_SUFFIX
                + LINE_BREAK
                + unit.text().orElse("");
    }

    /**
     * Renders the content representation of a contiguous run of units — the units presented in the
     * given order, each as {@link #renderUnit(DocumentUnit)}, separated by a single line break
     * (PROMPT_SPECIFICATION.md §4, §5).
     *
     * <p>The caller supplies the units in document order (a window's coverage is a contiguous
     * document-order run); this method preserves that order and neither reorders nor omits any
     * unit.
     *
     * @param units the units to render, in document order; must not be {@code null}
     * @return the content representation, or the empty string when there are no units
     */
    String renderContent(List<DocumentUnit> units) {
        Objects.requireNonNull(units, "units");
        StringBuilder rendered = new StringBuilder();
        for (int index = 0; index < units.size(); index++) {
            if (index > 0) {
                rendered.append(LINE_BREAK);
            }
            rendered.append(renderUnit(units.get(index)));
        }
        return rendered.toString();
    }

    /**
     * Renders the canonical serialization of a boundary decision: the given global ordinals, in the
     * given order, as a list of integers (PROMPT_SPECIFICATION.md §8).
     *
     * <p>This is a pure serializer; it emits the ordinals exactly as given and imposes no ordering
     * or de-duplication of its own.
     *
     * @param boundaryOrdinals the global ordinals to serialize; must not be {@code null}
     * @return the serialized list, {@code []} when empty
     */
    String renderResponse(List<Integer> boundaryOrdinals) {
        Objects.requireNonNull(boundaryOrdinals, "boundaryOrdinals");
        StringBuilder rendered = new StringBuilder(RESPONSE_OPEN);
        for (int index = 0; index < boundaryOrdinals.size(); index++) {
            if (index > 0) {
                rendered.append(RESPONSE_SEPARATOR);
            }
            rendered.append(Integer.toString(boundaryOrdinals.get(index)));
        }
        return rendered.append(RESPONSE_CLOSE).toString();
    }

    /**
     * Renders the maximal valid response for a contiguous run of units: every unit except the
     * first, named by global ordinal, serialized by {@link #renderResponse(List)}
     * (WINDOW_PLANNING.md §1, §8).
     *
     * <p>The first unit is excluded because it can never be a boundary within the run: a boundary
     * is a division between two units, and there is no unit before the first
     * (PROMPT_SPECIFICATION.md §6). A run of fewer than two units therefore has no valid boundary,
     * and its maximal response is empty.
     *
     * @param units the units of the run, in document order; must not be {@code null}
     * @return the serialized maximal valid response, {@code []} when the run has fewer than two
     *     units
     */
    String renderMaximalResponse(List<DocumentUnit> units) {
        Objects.requireNonNull(units, "units");
        List<Integer> ordinals =
                units.stream().skip(1).map(unit -> unit.provenance().globalOrdinal()).toList();
        return renderResponse(ordinals);
    }
}
