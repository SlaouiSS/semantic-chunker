package io.github.semanticchunker.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DocumentSourceTest {

    private static byte[] bytes() {
        return "hello".getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void createsFromContentAndMediaTypeWithoutFilename() {
        DocumentSource source = DocumentSource.of(bytes(), "text/plain");

        assertThat(source.content()).isEqualTo(bytes());
        assertThat(source.mediaType()).isEqualTo("text/plain");
        assertThat(source.filename()).isEmpty();
    }

    @Test
    void createsWithFilename() {
        DocumentSource source = DocumentSource.of(bytes(), "text/plain", "note.txt");

        assertThat(source.filename()).contains("note.txt");
    }

    @Test
    void doesNotExposeTheCallersArrayOnInput() {
        byte[] original = bytes();
        DocumentSource source = DocumentSource.of(original, "text/plain");

        original[0] = 'X';

        assertThat(source.content()).isEqualTo(bytes());
    }

    @Test
    void doesNotExposeItsInternalArrayOnOutput() {
        DocumentSource source = DocumentSource.of(bytes(), "text/plain");

        source.content()[0] = 'X';

        assertThat(source.content()).isEqualTo(bytes());
    }

    @Test
    void rejectsNullContent() {
        assertThatNullPointerException().isThrownBy(() -> DocumentSource.of(null, "text/plain"));
    }

    @Test
    void rejectsNullMediaType() {
        assertThatNullPointerException().isThrownBy(() -> DocumentSource.of(bytes(), null));
    }

    @Test
    void rejectsBlankMediaType() {
        assertThatIllegalArgumentException().isThrownBy(() -> DocumentSource.of(bytes(), "   "));
        assertThatIllegalArgumentException().isThrownBy(() -> DocumentSource.of(bytes(), ""));
    }

    @Test
    void hasValueEquality() {
        DocumentSource one = DocumentSource.of(bytes(), "text/plain", "a.txt");
        DocumentSource same = DocumentSource.of(bytes(), "text/plain", "a.txt");
        DocumentSource differentBytes = DocumentSource.of(new byte[] {1, 2}, "text/plain", "a.txt");
        DocumentSource differentType = DocumentSource.of(bytes(), "application/pdf", "a.txt");
        DocumentSource differentFilename = DocumentSource.of(bytes(), "text/plain", "b.txt");

        assertThat(one)
                .isEqualTo(one)
                .isEqualTo(same)
                .hasSameHashCodeAs(same)
                .isNotEqualTo(differentBytes)
                .isNotEqualTo(differentType)
                .isNotEqualTo(differentFilename)
                .isNotEqualTo(null)
                .isNotEqualTo("not a source");
    }

    @Test
    void toStringReportsMetadataButNotRawBytes() {
        String text = DocumentSource.of(bytes(), "text/plain", "a.txt").toString();

        assertThat(text).contains("text/plain").contains("a.txt").contains("contentLength=5");
        assertThat(text).doesNotContain("hello");
    }

    @Test
    void toStringMarksAnAbsentFilename() {
        assertThat(DocumentSource.of(bytes(), "text/plain").toString()).contains("filename=(none)");
    }
}
