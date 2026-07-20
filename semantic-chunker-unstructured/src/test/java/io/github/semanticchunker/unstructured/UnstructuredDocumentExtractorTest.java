package io.github.semanticchunker.unstructured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import io.github.semanticchunker.document.DocumentSource;
import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Heading;
import io.github.semanticchunker.document.Image;
import io.github.semanticchunker.document.ListItem;
import io.github.semanticchunker.document.PageBreak;
import io.github.semanticchunker.document.PreparedDocument;
import io.github.semanticchunker.document.Provenance;
import io.github.semanticchunker.document.Table;
import io.github.semanticchunker.extraction.ExtractionException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tests {@link UnstructuredDocumentExtractor} against a JDK {@link HttpServer} mock. */
class UnstructuredDocumentExtractorTest {

    private static final String SAMPLE_JSON =
            """
[
  {"type":"Title","element_id":"1","text":"Doc Title",
   "metadata":{"page_number":1,"category_depth":0,"languages":["eng"],
               "filetype":"application/pdf"}},
  {"type":"NarrativeText","element_id":"2","text":"A paragraph.",
   "metadata":{"page_number":1}},
  {"type":"ListItem","element_id":"3","text":"An item","metadata":{"page_number":1}},
  {"type":"Table","element_id":"4","text":"a b c d",
   "metadata":{"page_number":2,
     "text_as_html":"<table><tr><td>a</td><td>b</td></tr><tr><td>c</td><td>d</td></tr></table>"}},
  {"type":"Image","element_id":"5","text":"A caption","metadata":{"page_number":2}},
  {"type":"PageBreak","element_id":"6","metadata":{"page_number":2}}
]
""";

    private HttpServer server;
    private ExecutorService serverExecutor;
    private volatile byte[] capturedBody;
    private volatile Headers capturedHeaders;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    // --- mock server ---------------------------------------------------------

    private String start(int status, String body, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        serverExecutor = Executors.newFixedThreadPool(16);
        server.setExecutor(serverExecutor);
        server.createContext(
                "/general/v0/general",
                exchange -> {
                    capturedBody = exchange.getRequestBody().readAllBytes();
                    capturedHeaders = exchange.getRequestHeaders();
                    if (delayMillis > 0) {
                        try {
                            Thread.sleep(delayMillis);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    if (bytes.length == 0) {
                        exchange.sendResponseHeaders(status, -1);
                    } else {
                        exchange.sendResponseHeaders(status, bytes.length);
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(bytes);
                        }
                    }
                    exchange.close();
                });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    private UnstructuredDocumentExtractor extractorFor(String baseUrl) {
        return UnstructuredDocumentExtractor.builder()
                .apiUrl(baseUrl)
                .apiKey("test-key")
                .timeout(Duration.ofSeconds(10))
                .build();
    }

    private static DocumentSource pdf() {
        return DocumentSource.of(
                "raw bytes".getBytes(StandardCharsets.UTF_8), "application/pdf", "doc.pdf");
    }

    private static void assertProvenanceInvariant(PreparedDocument document) {
        String normalized =
                document.units().stream()
                        .map(u -> u.text().orElse(""))
                        .collect(Collectors.joining());
        int ordinal = 0;
        for (DocumentUnit unit : document.units()) {
            Provenance provenance = unit.provenance();
            assertThat(provenance.globalOrdinal()).isEqualTo(ordinal++);
            assertThat(normalized.substring(provenance.startOffset(), provenance.endOffset()))
                    .isEqualTo(unit.text().orElse(""));
        }
    }

    // --- Builder -------------------------------------------------------------

    @Test
    void buildsWithDefaults() {
        assertThat(UnstructuredDocumentExtractor.builder().build()).isNotNull();
    }

    @Test
    void rejectsNullApiUrl() {
        assertThatNullPointerException()
                .isThrownBy(() -> UnstructuredDocumentExtractor.builder().apiUrl(null))
                .withMessageContaining("apiUrl");
    }

    @Test
    void rejectsBlankApiUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UnstructuredDocumentExtractor.builder().apiUrl("  "))
                .withMessageContaining("apiUrl");
    }

    @Test
    void rejectsNullStrategy() {
        assertThatNullPointerException()
                .isThrownBy(() -> UnstructuredDocumentExtractor.builder().strategy(null))
                .withMessageContaining("strategy");
    }

    @Test
    void rejectsBlankStrategy() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UnstructuredDocumentExtractor.builder().strategy(""))
                .withMessageContaining("strategy");
    }

    @Test
    void rejectsNullTimeout() {
        assertThatNullPointerException()
                .isThrownBy(() -> UnstructuredDocumentExtractor.builder().timeout(null))
                .withMessageContaining("timeout");
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UnstructuredDocumentExtractor.builder().timeout(Duration.ZERO))
                .withMessageContaining("timeout");
    }

    @Test
    void rejectsNullHttpClient() {
        assertThatNullPointerException()
                .isThrownBy(() -> UnstructuredDocumentExtractor.builder().httpClient(null))
                .withMessageContaining("httpClient");
    }

    @Test
    void acceptsNullApiKeyForSelfHosting() {
        assertThat(UnstructuredDocumentExtractor.builder().apiKey(null).build()).isNotNull();
    }

    // --- supportedMediaTypes -------------------------------------------------

    @Test
    void reportsSupportedMediaTypes() {
        assertThat(UnstructuredDocumentExtractor.builder().build().supportedMediaTypes())
                .contains("application/pdf", "text/html", "text/plain")
                .doesNotContain("");
    }

    @Test
    void supportedMediaTypesIsUnmodifiable() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(
                        () ->
                                UnstructuredDocumentExtractor.builder()
                                        .build()
                                        .supportedMediaTypes()
                                        .add("x/y"));
    }

    // --- successful extraction -----------------------------------------------

    @Test
    void extractsAllElementTypes() throws IOException {
        PreparedDocument document = extractorFor(start(200, SAMPLE_JSON, 0)).extract(pdf());

        assertThat(document.units()).hasSize(6);
        assertThat(document.units().get(0))
                .isInstanceOfSatisfying(
                        Heading.class,
                        h -> {
                            assertThat(h.content()).isEqualTo("Doc Title");
                            assertThat(h.metadata()).containsEntry("level", "0");
                        });
        assertThat(document.units().get(2)).isInstanceOf(ListItem.class);
        assertThat(document.units().get(3))
                .isInstanceOfSatisfying(
                        Table.class,
                        t ->
                                assertThat(t.cells())
                                        .isEqualTo(List.of(List.of("a", "b"), List.of("c", "d"))));
        assertThat(document.units().get(4))
                .isInstanceOfSatisfying(
                        Image.class, i -> assertThat(i.caption()).isEqualTo("A caption"));
        assertThat(document.units().get(5)).isInstanceOf(PageBreak.class);
        assertThat(document.units().get(3).provenance().page()).isEqualTo(2);
        assertProvenanceInvariant(document);
    }

    @Test
    void recordsDocumentMetadata() throws IOException {
        PreparedDocument document = extractorFor(start(200, "[]", 0)).extract(pdf());

        assertThat(document.metadata())
                .containsEntry("mediaType", "application/pdf")
                .containsEntry("filename", "doc.pdf");
    }

    @Test
    void sendsTheExpectedMultipartFieldsAndKey() throws IOException {
        extractorFor(start(200, "[]", 0)).extract(pdf());

        String body = new String(capturedBody, StandardCharsets.UTF_8);
        assertThat(body)
                .contains("name=\"strategy\"")
                .contains("auto")
                .contains("name=\"include_page_breaks\"")
                .contains("name=\"pdf_infer_table_structure\"")
                .contains("name=\"files\"; filename=\"doc.pdf\"")
                .contains("application/pdf");
        assertThat(capturedHeaders.getFirst("unstructured-api-key")).isEqualTo("test-key");
    }

    @Test
    void omitsTheKeyHeaderWhenNoKeyIsConfigured() throws IOException {
        String baseUrl = start(200, "[]", 0);

        UnstructuredDocumentExtractor.builder()
                .apiUrl(baseUrl)
                .timeout(Duration.ofSeconds(10))
                .build()
                .extract(pdf());

        assertThat(capturedHeaders.getFirst("unstructured-api-key")).isNull();
    }

    @Test
    void returnsAnEmptyDocumentForAnEmptyResponse() throws IOException {
        PreparedDocument document = extractorFor(start(200, "[]", 0)).extract(pdf());

        assertThat(document.units()).isEmpty();
    }

    @Test
    void returnsAnEmptyDocumentForANullResponseBody() throws IOException {
        PreparedDocument document = extractorFor(start(200, "null", 0)).extract(pdf());

        assertThat(document.units()).isEmpty();
    }

    // --- failure mapping -----------------------------------------------------

    @Test
    void mapsBadRequest() throws IOException {
        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> extractorFor(start(400, "bad", 0)).extract(pdf()))
                .withMessageContaining("HTTP 400");
    }

    @Test
    void mapsAuthenticationFailure() throws IOException {
        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> extractorFor(start(401, "no key", 0)).extract(pdf()))
                .withMessageContaining("authentication");
    }

    @Test
    void mapsUnprocessableDocument() throws IOException {
        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> extractorFor(start(422, "encrypted", 0)).extract(pdf()))
                .withMessageContaining("encrypted");
    }

    @Test
    void mapsProcessingFailure() throws IOException {
        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> extractorFor(start(500, "", 0)).extract(pdf()))
                .withMessageContaining("HTTP 500");
    }

    @Test
    void mapsServiceUnavailable() throws IOException {
        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> extractorFor(start(503, "busy", 0)).extract(pdf()))
                .withMessageContaining("unavailable");
    }

    @Test
    void mapsUnexpectedStatus() throws IOException {
        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> extractorFor(start(418, "teapot", 0)).extract(pdf()))
                .withMessageContaining("HTTP 418");
    }

    @Test
    void mapsMalformedJson() throws IOException {
        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> extractorFor(start(200, "{not valid json", 0)).extract(pdf()))
                .withMessageContaining("malformed");
    }

    @Test
    void mapsTimeout() throws IOException {
        String baseUrl = start(200, "[]", 500);
        UnstructuredDocumentExtractor extractor =
                UnstructuredDocumentExtractor.builder()
                        .apiUrl(baseUrl)
                        .timeout(Duration.ofMillis(100))
                        .build();

        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> extractor.extract(pdf()))
                .withMessageContaining("timed out");
    }

    @Test
    void mapsConnectionFailure() {
        // Nothing is listening on this port, so the connection is refused.
        UnstructuredDocumentExtractor extractor =
                UnstructuredDocumentExtractor.builder()
                        .apiUrl("http://localhost:1")
                        .timeout(Duration.ofSeconds(2))
                        .build();

        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> extractor.extract(pdf()))
                .withMessageContaining("reach");
    }

    @Test
    void rejectsNullSource() {
        UnstructuredDocumentExtractor extractor =
                UnstructuredDocumentExtractor.builder().apiUrl("http://localhost:1").build();

        assertThatNullPointerException()
                .isThrownBy(() -> extractor.extract(null))
                .withMessageContaining("source");
    }

    @Test
    void configuresTheStrategy() throws IOException {
        String baseUrl = start(200, "[]", 0);
        UnstructuredDocumentExtractor.builder()
                .apiUrl(baseUrl)
                .strategy("fast")
                .timeout(Duration.ofSeconds(10))
                .build()
                .extract(pdf());

        assertThat(new String(capturedBody, StandardCharsets.UTF_8)).contains("fast");
    }

    @Test
    void rejectsNegativeTimeout() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                UnstructuredDocumentExtractor.builder()
                                        .timeout(Duration.ofSeconds(-1)))
                .withMessageContaining("timeout");
    }

    @Test
    void normalizesTrailingSlashInApiUrl() throws IOException {
        String baseUrl = start(200, SAMPLE_JSON, 0);

        PreparedDocument document =
                UnstructuredDocumentExtractor.builder()
                        .apiUrl(baseUrl + "/")
                        .timeout(Duration.ofSeconds(10))
                        .build()
                        .extract(pdf());

        assertThat(document.units()).hasSize(6);
    }

    @Test
    void handlesSourceWithoutAFilename() throws IOException {
        String baseUrl = start(200, "[]", 0);
        DocumentSource noName =
                DocumentSource.of("x".getBytes(StandardCharsets.UTF_8), "text/plain");

        PreparedDocument document = extractorFor(baseUrl).extract(noName);

        assertThat(document.metadata())
                .containsEntry("mediaType", "text/plain")
                .doesNotContainKey("filename");
        assertThat(new String(capturedBody, StandardCharsets.UTF_8))
                .contains("filename=\"document\"");
    }

    @Test
    void stripsCrlfFromTheMediaTypeToPreventInjection() throws IOException {
        String baseUrl = start(200, "[]", 0);
        DocumentSource injected =
                DocumentSource.of(
                        "x".getBytes(StandardCharsets.UTF_8),
                        "application/pdf\r\nX-Injected: evil");

        extractorFor(baseUrl).extract(injected);

        String body = new String(capturedBody, StandardCharsets.UTF_8);
        assertThat(body).contains("Content-Type: application/pdfX-Injected: evil");
        assertThat(body).doesNotContain("\r\nX-Injected");
    }

    @Test
    void omitsKeyHeaderWhenKeyIsBlank() throws IOException {
        String baseUrl = start(200, "[]", 0);

        UnstructuredDocumentExtractor.builder()
                .apiUrl(baseUrl)
                .apiKey("   ")
                .timeout(Duration.ofSeconds(10))
                .build()
                .extract(pdf());

        assertThat(capturedHeaders.getFirst("unstructured-api-key")).isNull();
    }

    @Test
    void includesTruncatedBodyDetailForErrors() throws IOException {
        String longBody = "x".repeat(300);

        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> extractorFor(start(400, longBody, 0)).extract(pdf()))
                .withMessageContaining("HTTP 400")
                .withMessageContaining("…");
    }

    // --- concurrency ---------------------------------------------------------

    @Test
    void isSafeForConcurrentUse() throws Exception {
        UnstructuredDocumentExtractor extractor = extractorFor(start(200, SAMPLE_JSON, 0));
        int expected = extractor.extract(pdf()).units().size();

        int threads = 8;
        int tasksPerThread = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> tasks =
                    IntStream.range(0, threads * tasksPerThread)
                            .<Callable<Integer>>mapToObj(
                                    i -> () -> extractor.extract(pdf()).units().size())
                            .toList();

            for (Future<Integer> result : pool.invokeAll(tasks)) {
                assertThat(result.get()).isEqualTo(expected);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
