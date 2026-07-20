package io.github.semanticchunker.unstructured.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests that the partition JSON deserializes into the response records. */
class JsonMappingTest {

    private static final TypeReference<List<UnstructuredElement>> ELEMENT_LIST =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<UnstructuredElement> parse(String json) throws Exception {
        return objectMapper.readValue(json, ELEMENT_LIST);
    }

    @Test
    void deserializesTheDocumentedFields() throws Exception {
        String json =
                """
                [
                  {
                    "type": "Title",
                    "element_id": "abc",
                    "text": "The Title",
                    "metadata": {
                      "page_number": 3,
                      "category_depth": 1,
                      "languages": ["eng", "fra"],
                      "filename": "doc.pdf",
                      "filetype": "application/pdf"
                    }
                  }
                ]
                """;

        List<UnstructuredElement> elements = parse(json);

        assertThat(elements).hasSize(1);
        UnstructuredElement element = elements.get(0);
        assertThat(element.type()).isEqualTo("Title");
        assertThat(element.elementId()).isEqualTo("abc");
        assertThat(element.text()).isEqualTo("The Title");
        assertThat(element.metadata().pageNumber()).isEqualTo(3);
        assertThat(element.metadata().categoryDepth()).isEqualTo(1);
        assertThat(element.metadata().languages()).containsExactly("eng", "fra");
        assertThat(element.metadata().filename()).isEqualTo("doc.pdf");
        assertThat(element.metadata().filetype()).isEqualTo("application/pdf");
    }

    @Test
    void ignoresUnknownFields() throws Exception {
        String json =
                """
                [
                  {
                    "type": "NarrativeText",
                    "text": "Body",
                    "some_future_field": 42,
                    "metadata": { "page_number": 1, "detection_class_prob": 0.98 }
                  }
                ]
                """;

        List<UnstructuredElement> elements = parse(json);

        assertThat(elements).hasSize(1);
        assertThat(elements.get(0).type()).isEqualTo("NarrativeText");
        assertThat(elements.get(0).metadata().pageNumber()).isEqualTo(1);
    }

    @Test
    void keepsTextAsHtmlForTables() throws Exception {
        String json =
                """
                [
                  {
                    "type": "Table",
                    "text": "a b",
                    "metadata": { "text_as_html": "<table><tr><td>a</td><td>b</td></tr></table>" }
                  }
                ]
                """;

        UnstructuredElement table = parse(json).get(0);

        assertThat(table.metadata().textAsHtml()).contains("<td>a</td>");
    }

    @Test
    void keepsCoordinatesAsARawNode() throws Exception {
        String json =
                """
                [
                  {
                    "type": "Image",
                    "text": "cap",
                    "metadata": { "coordinates": { "system": "PixelSpace", "layout_width": 100 } }
                  }
                ]
                """;

        UnstructuredElement image = parse(json).get(0);

        assertThat(image.metadata().coordinates()).isNotNull();
        assertThat(image.metadata().coordinates().toString()).contains("PixelSpace");
    }

    @Test
    void toleratesAbsentMetadata() throws Exception {
        List<UnstructuredElement> elements =
                parse("[ { \"type\": \"PageBreak\", \"text\": \"\" } ]");

        assertThat(elements.get(0).metadata()).isNull();
    }

    @Test
    void parsesAnEmptyArray() throws Exception {
        assertThat(parse("[]")).isEmpty();
    }
}
