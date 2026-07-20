package io.github.semanticchunker.document;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The honest input type for extraction: the raw content of a document together with its media type
 * and an optional filename (CONTRIBUTING_ARCHITECTURE.md, section 6.3.1).
 *
 * <p>A {@code DocumentSource} exists because extraction cannot route an input without knowing its
 * media type; a bare stream is never enough. It is what a {@link
 * io.github.semanticchunker.extraction.DocumentExtractor} consumes.
 *
 * <p>This type is a final, immutable class rather than a record because it holds raw bytes, which
 * must be defensively copied on the way in and out, and exposes an optional filename as an {@link
 * Optional} accessor. It has value semantics: two sources are equal when their content, media type,
 * and filename are equal.
 */
public final class DocumentSource {

    private final byte[] content;
    private final String mediaType;
    private final String filename;

    private DocumentSource(byte[] content, String mediaType, String filename) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(mediaType, "mediaType");
        if (mediaType.isBlank()) {
            throw new IllegalArgumentException(
                    "mediaType must not be blank; it is the value extraction routes on");
        }
        this.content = content.clone();
        this.mediaType = mediaType;
        this.filename = filename;
    }

    /**
     * Creates a source from content and its media type, with no filename.
     *
     * @param content the raw document bytes; must not be {@code null}
     * @param mediaType the media type of the content (for example {@code "application/pdf"}); must
     *     not be {@code null} or blank
     * @return a new document source
     * @throws NullPointerException if {@code content} or {@code mediaType} is {@code null}
     * @throws IllegalArgumentException if {@code mediaType} is blank
     */
    public static DocumentSource of(byte[] content, String mediaType) {
        return new DocumentSource(content, mediaType, null);
    }

    /**
     * Creates a source from content, its media type, and a filename.
     *
     * @param content the raw document bytes; must not be {@code null}
     * @param mediaType the media type of the content; must not be {@code null} or blank
     * @param filename the originating filename; may be {@code null}
     * @return a new document source
     * @throws NullPointerException if {@code content} or {@code mediaType} is {@code null}
     * @throws IllegalArgumentException if {@code mediaType} is blank
     */
    public static DocumentSource of(byte[] content, String mediaType, String filename) {
        return new DocumentSource(content, mediaType, filename);
    }

    /**
     * The raw document bytes.
     *
     * @return a defensive copy of the content, never {@code null}
     */
    public byte[] content() {
        return content.clone();
    }

    /**
     * The media type of the content.
     *
     * @return the media type, never {@code null} or blank
     */
    public String mediaType() {
        return mediaType;
    }

    /**
     * The originating filename, if one was supplied.
     *
     * @return the filename, or an empty optional if none was supplied
     */
    public Optional<String> filename() {
        return Optional.ofNullable(filename);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DocumentSource other)) {
            return false;
        }
        return Arrays.equals(content, other.content)
                && mediaType.equals(other.mediaType)
                && Objects.equals(filename, other.filename);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(content);
        result = 31 * result + mediaType.hashCode();
        result = 31 * result + Objects.hashCode(filename);
        return result;
    }

    /**
     * A description that omits the raw bytes (which may be large or binary), reporting only the
     * content length, media type, and filename.
     *
     * @return a diagnostic string
     */
    @Override
    public String toString() {
        return "DocumentSource{mediaType='"
                + mediaType
                + "', filename="
                + (filename == null ? "(none)" : "'" + filename + "'")
                + ", contentLength="
                + content.length
                + "}";
    }
}
