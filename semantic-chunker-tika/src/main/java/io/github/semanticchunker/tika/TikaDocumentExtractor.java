package io.github.semanticchunker.tika;

import io.github.semanticchunker.document.DocumentSource;
import io.github.semanticchunker.document.PreparedDocument;
import io.github.semanticchunker.extraction.DocumentExtractor;
import io.github.semanticchunker.extraction.ExtractionException;
import io.github.semanticchunker.tika.internal.UnitAssemblingContentHandler;
import io.github.semanticchunker.tika.internal.WriteLimitExceededException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.xml.sax.SAXException;

/**
 * A {@link DocumentExtractor} backed by Apache Tika: it translates Tika's output into the library's
 * normalized representation, so documents can be extracted by that technology without the core
 * knowing anything about it (ARCHITECTURE.md, section 5).
 *
 * <p><strong>How it works.</strong> Tika parses a document into an XHTML SAX event stream; this
 * adapter consumes that stream with an internal handler that folds it into the core's ordered
 * {@link io.github.semanticchunker.document.DocumentUnit}s. An {@link AutoDetectParser} is used so
 * the adapter stays format-agnostic and {@link #supportedMediaTypes()} can report exactly what the
 * configured parsers handle. Which formats are recoverable is determined by the Tika parser modules
 * on the classpath.
 *
 * <p><strong>Fidelity.</strong> The adapter faithfully represents Tika's output; it does not try to
 * improve on it. Because Tika's XHTML is format-dependent, some structure is not recoverable and
 * the loss is deliberate, not a defect (Phase T1 mapping specification):
 *
 * <ul>
 *   <li>Word and PowerPoint list items arrive as paragraphs with the bullet in the text, so they
 *       map to {@link io.github.semanticchunker.document.Paragraph}, not {@link
 *       io.github.semanticchunker.document.ListItem}; only HTML, RTF, EPUB, and legacy PowerPoint
 *       yield true list items.
 *   <li>PDF emits no image markup, so PDF images are not represented; Word and HTML images become
 *       {@link io.github.semanticchunker.document.Image} with the {@code alt} text as caption.
 *   <li>Page breaks are recovered only for formats that emit page/section containers (PDF, Excel,
 *       PowerPoint); Word and HTML have no page markers, so their units carry page {@code 0}.
 * </ul>
 *
 * <p><strong>OCR.</strong> OCR is disabled by default and this module bundles no OCR engine. When
 * disabled, PDF text extraction is forced to text-only ({@code NO_OCR}), so extraction never
 * depends on a native binary and is deterministic. Enabling OCR merely lifts that restriction; it
 * has no effect unless an OCR-capable parser and a Tesseract binary are separately present.
 *
 * <p><strong>Write limit.</strong> The write limit bounds how much text may be accumulated. It is
 * unlimited by default. When a positive limit is exceeded, extraction fails with a clear {@link
 * ExtractionException} rather than silently truncating.
 *
 * <p><strong>Thread safety.</strong> An instance is immutable and safe for concurrent use. Each
 * {@link #extract(DocumentSource)} call builds its own {@link Metadata}, {@link ParseContext}, and
 * content handler, so no mutable parsing state is shared between calls.
 *
 * <p>This class is {@code final} and constructed only through its {@link #builder() builder}.
 */
public final class TikaDocumentExtractor implements DocumentExtractor {

    private final Parser parser;
    private final int writeLimit;
    private final boolean ocrEnabled;
    private final Set<String> supportedMediaTypes;

    private TikaDocumentExtractor(Builder builder) {
        this.parser = builder.parser;
        this.writeLimit = builder.writeLimit;
        this.ocrEnabled = builder.ocrEnabled;
        this.supportedMediaTypes =
                parser.getSupportedTypes(new ParseContext()).stream()
                        .map(MediaType::toString)
                        .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Starts building an extractor.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Extracts the given source into the library's normalized representation.
     *
     * <p>A source that parses cleanly but yields no textual content produces an empty {@link
     * PreparedDocument}; that is an honest empty result, not a failure. A source that cannot be
     * parsed fails with {@link ExtractionException}, its Tika cause preserved.
     *
     * @param source the raw document to interpret; must not be {@code null}
     * @return the normalized prepared document
     * @throws NullPointerException if {@code source} is {@code null}
     * @throws ExtractionException if Tika cannot interpret the source, the document is encrypted,
     *     or the configured write limit is exceeded
     */
    @Override
    public PreparedDocument extract(DocumentSource source) {
        Objects.requireNonNull(source, "source");

        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, source.mediaType());
        source.filename()
                .ifPresent(name -> metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name));

        ParseContext context = new ParseContext();
        configureOcr(context);
        UnitAssemblingContentHandler handler = new UnitAssemblingContentHandler(writeLimit);

        try (InputStream stream = TikaInputStream.get(source.content(), metadata)) {
            parser.parse(stream, handler, metadata, context);
        } catch (TikaException | SAXException | IOException | RuntimeException e) {
            throw toExtractionException(source, e);
        }

        return new PreparedDocument(handler.units(), toMetadataMap(metadata));
    }

    /**
     * Classifies a parse failure and wraps it in an {@link ExtractionException}, preserving the
     * original as the cause. The write-limit and encryption signals are recognised anywhere in the
     * cause chain, because Tika parsers may wrap a handler's exception before it surfaces.
     */
    private ExtractionException toExtractionException(DocumentSource source, Exception failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof WriteLimitExceededException) {
                return new ExtractionException(
                        "Extraction stopped: the document exceeds the configured write limit of "
                                + writeLimit
                                + " characters",
                        failure);
            }
            if (cause instanceof EncryptedDocumentException) {
                return new ExtractionException(
                        "The document is encrypted and cannot be extracted by Apache Tika",
                        failure);
            }
        }
        return new ExtractionException(
                "Apache Tika could not extract the document (" + source.mediaType() + ")", failure);
    }

    /**
     * The media types the configured Tika parsers declare support for.
     *
     * @return an unmodifiable set of supported media types
     */
    @Override
    public Set<String> supportedMediaTypes() {
        return supportedMediaTypes;
    }

    private void configureOcr(ParseContext context) {
        if (!ocrEnabled) {
            PDFParserConfig pdfConfig = new PDFParserConfig();
            pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
            context.set(PDFParserConfig.class, pdfConfig);
        }
    }

    private static Map<String, String> toMetadataMap(Metadata metadata) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : metadata.names()) {
            String[] values = metadata.getValues(name);
            if (values.length > 0) {
                result.put(name, String.join(", ", values));
            }
        }
        return Map.copyOf(result);
    }

    /** Builds a {@link TikaDocumentExtractor}. */
    public static final class Builder {

        private Parser parser = new AutoDetectParser();
        private int writeLimit = -1;
        private boolean ocrEnabled = false;

        private Builder() {}

        /**
         * Sets the Tika parser to use. Defaults to a fresh {@link AutoDetectParser}, which detects
         * the format and delegates to the appropriate parser.
         *
         * @param parser the parser; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code parser} is {@code null}
         */
        public Builder parser(Parser parser) {
            this.parser = Objects.requireNonNull(parser, "parser");
            return this;
        }

        /**
         * Sets the maximum number of characters to accumulate before extraction fails. Use {@code
         * -1} (the default) for no limit. Exceeding a positive limit throws {@link
         * ExtractionException} rather than silently truncating.
         *
         * @param writeLimit the limit in characters, or {@code -1} for unlimited
         * @return this builder
         * @throws IllegalArgumentException if {@code writeLimit} is less than {@code -1}
         */
        public Builder writeLimit(int writeLimit) {
            if (writeLimit < -1) {
                throw new IllegalArgumentException(
                        "writeLimit must be -1 (unlimited) or non-negative: " + writeLimit);
            }
            this.writeLimit = writeLimit;
            return this;
        }

        /**
         * Enables or disables OCR. Disabled by default; when disabled, PDF extraction is forced to
         * text-only so it never depends on a native OCR engine.
         *
         * @param ocrEnabled whether to enable OCR
         * @return this builder
         */
        public Builder ocrEnabled(boolean ocrEnabled) {
            this.ocrEnabled = ocrEnabled;
            return this;
        }

        /**
         * Completes construction.
         *
         * @return a new extractor
         */
        public TikaDocumentExtractor build() {
            return new TikaDocumentExtractor(this);
        }
    }
}
