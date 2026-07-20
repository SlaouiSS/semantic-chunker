package io.github.semanticchunker.tika.internal;

import org.xml.sax.SAXException;

/**
 * Raised by {@link UnitAssemblingContentHandler} when the extracted text would exceed the caller's
 * configured write limit.
 *
 * <p>It is a {@link SAXException} so that it can be thrown from a SAX callback and abort parsing at
 * once — the library never silently truncates. The adapter catches it and surfaces a {@link
 * io.github.semanticchunker.extraction.ExtractionException} that names the limit, so the caller
 * learns the extraction was stopped rather than quietly cut short.
 */
public final class WriteLimitExceededException extends SAXException {

    private static final long serialVersionUID = 1L;

    private final int writeLimit;

    /**
     * Creates an exception reporting the limit that was exceeded.
     *
     * @param writeLimit the configured maximum number of characters
     */
    public WriteLimitExceededException(int writeLimit) {
        super(
                "Extracted text exceeded the configured write limit of "
                        + writeLimit
                        + " characters");
        this.writeLimit = writeLimit;
    }

    /**
     * The configured maximum number of characters that was exceeded. Package-private: consumed only
     * by tests; the adapter recognises this exception by type, not by this value.
     *
     * @return the write limit
     */
    int writeLimit() {
        return writeLimit;
    }
}
