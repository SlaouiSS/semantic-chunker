package io.github.semanticchunker.unstructured.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One element of an Unstructured partition response — the JSON objects the endpoint returns as a
 * flat array (Phase U1 architecture review).
 *
 * <p>Only the documented fields the adapter uses are mapped; any others are ignored, so a schema
 * addition upstream does not break deserialization.
 *
 * @param type the Unstructured element category, e.g. {@code "Title"}, {@code "NarrativeText"},
 *     {@code "Table"}; may be {@code null} for a malformed element
 * @param elementId Unstructured's identifier for the element; may be {@code null}
 * @param text the element's text; may be {@code null}
 * @param metadata the element's metadata; may be {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnstructuredElement(
        @JsonProperty("type") String type,
        @JsonProperty("element_id") String elementId,
        @JsonProperty("text") String text,
        @JsonProperty("metadata") UnstructuredMetadata metadata) {}
