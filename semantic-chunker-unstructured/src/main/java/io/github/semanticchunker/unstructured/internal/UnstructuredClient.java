package io.github.semanticchunker.unstructured.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.semanticchunker.extraction.ExtractionException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The HTTP client for the Unstructured partition endpoint: it builds the multipart request, sends
 * it, and translates the response — or any transport failure — into either the parsed elements or
 * an {@link ExtractionException}.
 *
 * <p>All HTTP concerns live here, including the status-to-message mapping, because the status is
 * known here. This differs deliberately from the Tika adapter, whose failures are classified in the
 * extractor: there the failure is a local exception, here it is an HTTP status.
 *
 * <p>The transport is the JDK {@link HttpClient}; no HTTP framework is used. Instances are
 * immutable and hold a shared {@link HttpClient} and a shared {@link ObjectMapper}, both safe for
 * concurrent use, so one client serves many concurrent extractions.
 */
public final class UnstructuredClient {

    private static final TypeReference<List<UnstructuredElement>> ELEMENT_LIST =
            new TypeReference<>() {};
    private static final int DETAIL_LIMIT = 200;

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String strategy;
    private final Duration timeout;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates a client bound to one endpoint and configuration.
     *
     * @param httpClient the shared HTTP client
     * @param endpoint the fully-resolved partition endpoint
     * @param apiKey the API key, or {@code null} for a keyless self-hosted service
     * @param strategy the partition strategy
     * @param timeout the per-request timeout
     */
    public UnstructuredClient(
            HttpClient httpClient, URI endpoint, String apiKey, String strategy, Duration timeout) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.strategy = strategy;
        this.timeout = timeout;
    }

    /**
     * Partitions one document and returns its elements.
     *
     * @param content the raw document bytes
     * @param filename the filename to present to Unstructured
     * @param contentType the media type of the content
     * @return the partition elements, in document order; never {@code null}
     * @throws ExtractionException if the service cannot be reached, times out, returns a non-200
     *     status, or returns a malformed body
     */
    public List<UnstructuredElement> partition(
            byte[] content, String filename, String contentType) {
        HttpRequest request = buildRequest(content, filename, contentType);

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            throw new ExtractionException("The Unstructured request timed out after " + timeout, e);
        } catch (IOException e) {
            throw new ExtractionException(
                    "Could not reach the Unstructured service at " + endpoint, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExtractionException("The Unstructured request was interrupted", e);
        }

        int status = response.statusCode();
        if (status != 200) {
            throw new ExtractionException(messageForStatus(status, response.body()));
        }

        try {
            List<UnstructuredElement> elements =
                    objectMapper.readValue(response.body(), ELEMENT_LIST);
            return elements == null ? List.of() : elements;
        } catch (IOException e) {
            throw new ExtractionException("Unstructured returned a malformed JSON response", e);
        }
    }

    private HttpRequest buildRequest(byte[] content, String filename, String contentType) {
        String boundary = "SemanticChunkerBoundary" + UUID.randomUUID();
        byte[] body = multipartBody(boundary, content, filename, contentType);
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(endpoint)
                        .timeout(timeout)
                        .header("Accept", "application/json")
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("unstructured-api-key", apiKey);
        }
        return builder.build();
    }

    private byte[] multipartBody(
            String boundary, byte[] content, String filename, String contentType) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTextField(out, boundary, "strategy", strategy);
        writeTextField(out, boundary, "include_page_breaks", "true");
        writeTextField(out, boundary, "pdf_infer_table_structure", "true");
        out.writeBytes(utf8("--" + boundary + "\r\n"));
        out.writeBytes(
                utf8(
                        "Content-Disposition: form-data; name=\"files\"; filename=\""
                                + sanitizeFilename(filename)
                                + "\"\r\n"));
        out.writeBytes(utf8("Content-Type: " + sanitizeContentType(contentType) + "\r\n\r\n"));
        out.writeBytes(content);
        out.writeBytes(utf8("\r\n"));
        out.writeBytes(utf8("--" + boundary + "--\r\n"));
        return out.toByteArray();
    }

    private static void writeTextField(
            ByteArrayOutputStream out, String boundary, String name, String value) {
        out.writeBytes(utf8("--" + boundary + "\r\n"));
        out.writeBytes(utf8("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"));
        out.writeBytes(utf8(value));
        out.writeBytes(utf8("\r\n"));
    }

    private static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document";
        }
        return filename.replace("\"", "").replace("\r", " ").replace("\n", " ");
    }

    /**
     * Removes CR and LF from a media type before it is interpolated into the multipart body, so a
     * caller-supplied value cannot inject headers or extra parts. A well-formed media type never
     * contains line breaks, so stripping them cannot change a legitimate value.
     */
    private static String sanitizeContentType(String contentType) {
        return contentType.replace("\r", "").replace("\n", "");
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String messageForStatus(int status, byte[] body) {
        String base =
                switch (status) {
                    case 400 ->
                            "Unstructured rejected the request (HTTP 400): unsupported document"
                                    + " type or parameters";
                    case 401 ->
                            "Unstructured authentication failed (HTTP 401): the API key is missing"
                                    + " or invalid";
                    case 422 ->
                            "Unstructured could not process the document (HTTP 422): it may be"
                                    + " encrypted, corrupted, or invalid";
                    case 500 -> "Unstructured failed to process the document (HTTP 500)";
                    case 503 -> "The Unstructured service is unavailable (HTTP 503)";
                    default -> "Unstructured returned an unexpected status (HTTP " + status + ")";
                };
        String detail = snippet(body);
        return detail.isEmpty() ? base : base + ": " + detail;
    }

    private static String snippet(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        String text = new String(body, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        if (text.length() <= DETAIL_LIMIT) {
            return text;
        }
        return text.substring(0, DETAIL_LIMIT) + "…";
    }
}
