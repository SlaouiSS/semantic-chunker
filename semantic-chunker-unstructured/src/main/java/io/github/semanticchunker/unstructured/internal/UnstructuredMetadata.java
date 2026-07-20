package io.github.semanticchunker.unstructured.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * The {@code metadata} object of one Unstructured partition element, limited to the documented
 * fields this adapter consumes (Phase U1 mapping specification).
 *
 * <p>Unknown fields are ignored, so the adapter tolerates the many metadata keys Unstructured may
 * add for particular formats without breaking. {@code coordinates} is kept as a raw {@link
 * JsonNode} because the adapter only needs its string form for peripheral metadata, not its
 * structure.
 *
 * @param pageNumber the 1-based page the element originated on, or {@code null} for unpaged formats
 * @param textAsHtml a table's HTML serialization, present only for tables under a structure-aware
 *     strategy; {@code null} otherwise
 * @param languages the detected languages, most probable first; may be {@code null}
 * @param categoryDepth the element's depth within its category (a heading's level); may be {@code
 *     null}
 * @param filename the source filename Unstructured recorded; may be {@code null}
 * @param filetype the detected media type; may be {@code null}
 * @param coordinates the raw bounding-box node; may be {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnstructuredMetadata(
        @JsonProperty("page_number") Integer pageNumber,
        @JsonProperty("text_as_html") String textAsHtml,
        @JsonProperty("languages") List<String> languages,
        @JsonProperty("category_depth") Integer categoryDepth,
        @JsonProperty("filename") String filename,
        @JsonProperty("filetype") String filetype,
        @JsonProperty("coordinates") JsonNode coordinates) {}
