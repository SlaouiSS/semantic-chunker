package io.github.semanticchunker.chunker.internal;

import io.github.semanticchunker.document.DocumentUnit;
import java.util.List;

/**
 * A temporary processing view over a contiguous run of a document's units (MODEL.md §6).
 *
 * <p>A window is a view, not a fragment: it references units that belong to the {@link
 * io.github.semanticchunker.document.PreparedDocument} rather than owning content of its own.
 * Because the units it references are the very same immutable {@link DocumentUnit} instances the
 * prepared document holds, they keep the identity (global ordinal) they were given at
 * normalization, which is what later allows decisions drawn over separate windows to be reconciled.
 *
 * <p>Windows are internal scaffolding; nothing in the final output records that they ever existed.
 * The overlap between adjacent windows is a property of how a set of windows relates, not a field
 * on a single window.
 *
 * <p>Each window is marked as either admissible or {@code overBudget} (WINDOW_PLANNING.md §3, §12).
 * An over-budget window is a single unit whose content representation alone exceeds the maximum
 * usable input; it cannot be presented to the model and is handed to the recovery path. An
 * admissible window fits within the model's context-window limit with room reserved for the
 * response.
 *
 * <p>A window also records whether it contains the document's first unit ({@code
 * containsDocumentFirstUnit}). Only the window that begins at the document's first unit does;
 * because windows are contiguous runs in document order, that unit, when present, is the window's
 * first unit. Response validation needs this to enforce that the document's first unit is never a
 * boundary (RESPONSE_VALIDATION.md §3–§4), since it receives only a window and cannot otherwise
 * tell whether a named unit is the document's first.
 *
 * @param units the contiguous run of units this window views, in document order; defensively copied
 *     and exposed as an unmodifiable list
 * @param overBudget whether this window could not be made admissible (a single oversized unit)
 * @param containsDocumentFirstUnit whether this window contains the document's first unit (its own
 *     first unit is then the document's first unit)
 */
record Window(List<DocumentUnit> units, boolean overBudget, boolean containsDocumentFirstUnit) {

    /** Validates and defensively copies. */
    Window {
        units = List.copyOf(units);
    }

    /**
     * Creates a window that is neither over-budget nor known to contain the document's first unit.
     *
     * @param units the contiguous run of units this window views, in document order
     */
    Window(List<DocumentUnit> units) {
        this(units, false, false);
    }

    /**
     * Creates a window that is not known to contain the document's first unit.
     *
     * @param units the contiguous run of units this window views, in document order
     * @param overBudget whether the window could not be made admissible
     */
    Window(List<DocumentUnit> units, boolean overBudget) {
        this(units, overBudget, false);
    }
}
