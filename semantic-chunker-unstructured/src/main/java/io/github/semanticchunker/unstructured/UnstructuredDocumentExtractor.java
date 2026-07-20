package io.github.semanticchunker.unstructured;

import io.github.semanticchunker.document.DocumentSource;
import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.PreparedDocument;
import io.github.semanticchunker.extraction.DocumentExtractor;
import io.github.semanticchunker.extraction.ExtractionException;
import io.github.semanticchunker.unstructured.internal.ElementMapper;
import io.github.semanticchunker.unstructured.internal.UnstructuredClient;
import io.github.semanticchunker.unstructured.internal.UnstructuredElement;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A {@link DocumentExtractor} backed by the Unstructured partition API: it sends a document to the
 * endpoint and folds the returned elements into the library's normalized representation, so
 * documents can be extracted by that technology without the core knowing anything about it
 * (ARCHITECTURE.md, section 5).
 *
 * <p><strong>How it works.</strong> The extractor POSTs the document to {@code
 * {apiUrl}/general/v0/general} as {@code multipart/form-data}, parses the JSON array of elements
 * the endpoint returns, and maps each element to one of the core's {@link DocumentUnit} kinds. The
 * HTTP transport is the JDK {@link HttpClient}; no HTTP framework is used.
 *
 * <p><strong>Deployment.</strong> Two deployments are supported, selected purely by configuration:
 * the hosted service (default {@code https://api.unstructuredapp.io}, which requires an {@code
 * apiKey}), and a self-hosted instance (for example {@code http://localhost:8000}, which needs no
 * key). The {@code apiKey} is nullable for exactly this reason.
 *
 * <p><strong>Fidelity.</strong> The adapter faithfully represents Unstructured's output. Element
 * types collapse into the six canonical unit kinds (Phase U1 mapping): {@code Title} → {@link
 * io.github.semanticchunker.document.Heading}, {@code ListItem} → {@link
 * io.github.semanticchunker.document.ListItem} (preserved exactly, unlike the Tika adapter), {@code
 * Table} → {@link io.github.semanticchunker.document.Table}, {@code Image} → {@link
 * io.github.semanticchunker.document.Image}, {@code PageBreak} → {@link
 * io.github.semanticchunker.document.PageBreak}, and every other text-bearing type → {@link
 * io.github.semanticchunker.document.Paragraph}. A table's cells and Markdown come from its {@code
 * text_as_html} when present (structure-aware strategies), otherwise from its plain text.
 *
 * <p><strong>Provenance.</strong> Unstructured returns no source offsets, so — per the frozen
 * contract — offsets address the normalized text (the concatenation of the units' textual content),
 * computed by construction. Page numbers come from each element's {@code page_number}, or {@code 0}
 * for unpaged formats.
 *
 * <p><strong>Failures.</strong> Every failure — transport, timeout, interruption, a non-200 status,
 * or a malformed body — becomes an {@link ExtractionException} with a clear message and the cause
 * preserved; nothing from the HTTP or JSON layers leaks out. The adapter does not retry: extraction
 * runs once and the library owns recovery.
 *
 * <p><strong>Thread safety.</strong> An instance is immutable and safe for concurrent use. It holds
 * a shared {@link HttpClient}; each {@link #extract(DocumentSource)} call builds its own request
 * and a fresh mapper, so no mutable state is shared between calls.
 *
 * <p>This class is {@code final} and constructed only through its {@link #builder() builder}.
 */
public final class UnstructuredDocumentExtractor implements DocumentExtractor {

    private static final String DEFAULT_API_URL = "https://api.unstructuredapp.io";
    private static final String PARTITION_PATH = "/general/v0/general";
    private static final String DEFAULT_STRATEGY = "auto";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    /**
     * A curated set of the media types Unstructured commonly handles. Unlike the Tika adapter,
     * which introspects its parsers, the partition API does not report its accepted types, so this
     * list is maintained by hand and documented in the module README.
     */
    private static final Set<String> SUPPORTED_MEDIA_TYPES =
            Set.of(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.oasis.opendocument.text",
                    "application/rtf",
                    "application/epub+zip",
                    "application/xml",
                    "text/html",
                    "text/plain",
                    "text/markdown",
                    "text/csv",
                    "message/rfc822",
                    "application/vnd.ms-outlook",
                    "image/png",
                    "image/jpeg",
                    "image/tiff");

    private final UnstructuredClient client;

    private UnstructuredDocumentExtractor(Builder builder) {
        HttpClient httpClient =
                builder.httpClient != null
                        ? builder.httpClient
                        : HttpClient.newBuilder().connectTimeout(builder.timeout).build();
        this.client =
                new UnstructuredClient(
                        httpClient,
                        resolveEndpoint(builder.apiUrl),
                        builder.apiKey,
                        builder.strategy,
                        builder.timeout);
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
     * Extracts the given source by partitioning it through the Unstructured API.
     *
     * <p>A document that partitions cleanly but yields no textual content produces an empty {@link
     * PreparedDocument}; that is an honest empty result, not a failure.
     *
     * @param source the raw document to interpret; must not be {@code null}
     * @return the normalized prepared document
     * @throws NullPointerException if {@code source} is {@code null}
     * @throws ExtractionException if the service cannot be reached, times out, returns an error
     *     status, or returns a malformed response
     */
    @Override
    public PreparedDocument extract(DocumentSource source) {
        Objects.requireNonNull(source, "source");
        List<UnstructuredElement> elements =
                client.partition(
                        source.content(), source.filename().orElse("document"), source.mediaType());
        List<DocumentUnit> units = new ElementMapper().map(elements);
        return new PreparedDocument(units, documentMetadata(source));
    }

    /**
     * The media types this adapter declares support for.
     *
     * @return an unmodifiable set of supported media types
     */
    @Override
    public Set<String> supportedMediaTypes() {
        return SUPPORTED_MEDIA_TYPES;
    }

    private static URI resolveEndpoint(String apiUrl) {
        String base = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        return URI.create(base + PARTITION_PATH);
    }

    private static Map<String, String> documentMetadata(DocumentSource source) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("mediaType", source.mediaType());
        source.filename().ifPresent(name -> metadata.put("filename", name));
        return Map.copyOf(metadata);
    }

    /** Builds an {@link UnstructuredDocumentExtractor}. */
    public static final class Builder {

        private String apiUrl = DEFAULT_API_URL;
        private String apiKey;
        private String strategy = DEFAULT_STRATEGY;
        private Duration timeout = DEFAULT_TIMEOUT;
        private HttpClient httpClient;

        private Builder() {}

        /**
         * Sets the base URL of the Unstructured service. Defaults to the hosted service; point it
         * at a self-hosted instance (for example {@code http://localhost:8000}) to run without a
         * key. The partition path is appended automatically.
         *
         * @param apiUrl the base URL; must not be {@code null} or blank
         * @return this builder
         * @throws NullPointerException if {@code apiUrl} is {@code null}
         * @throws IllegalArgumentException if {@code apiUrl} is blank
         */
        public Builder apiUrl(String apiUrl) {
            Objects.requireNonNull(apiUrl, "apiUrl");
            if (apiUrl.isBlank()) {
                throw new IllegalArgumentException("apiUrl must not be blank");
            }
            this.apiUrl = apiUrl;
            return this;
        }

        /**
         * Sets the API key for the hosted service. Leave unset (or {@code null}) for a keyless
         * self-hosted instance.
         *
         * @param apiKey the API key, or {@code null}
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the partition strategy (for example {@code auto}, {@code fast}, {@code hi_res}).
         * Defaults to {@code auto}.
         *
         * @param strategy the strategy; must not be {@code null} or blank
         * @return this builder
         * @throws NullPointerException if {@code strategy} is {@code null}
         * @throws IllegalArgumentException if {@code strategy} is blank
         */
        public Builder strategy(String strategy) {
            Objects.requireNonNull(strategy, "strategy");
            if (strategy.isBlank()) {
                throw new IllegalArgumentException("strategy must not be blank");
            }
            this.strategy = strategy;
            return this;
        }

        /**
         * Sets the per-request timeout. Defaults to two minutes, since structure-aware strategies
         * can be slow.
         *
         * @param timeout the timeout; must not be {@code null} and must be positive
         * @return this builder
         * @throws NullPointerException if {@code timeout} is {@code null}
         * @throws IllegalArgumentException if {@code timeout} is not positive
         */
        public Builder timeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive: " + timeout);
            }
            this.timeout = timeout;
            return this;
        }

        /**
         * Overrides the HTTP client. Intended for tests; production callers should rely on the
         * default client the builder creates.
         *
         * @param httpClient the client to use; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code httpClient} is {@code null}
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
            return this;
        }

        /**
         * Completes construction.
         *
         * @return a new extractor
         */
        public UnstructuredDocumentExtractor build() {
            return new UnstructuredDocumentExtractor(this);
        }
    }
}
