package io.github.semanticchunker.tika;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.semanticchunker.document.DocumentSource;
import io.github.semanticchunker.document.DocumentUnit;
import io.github.semanticchunker.document.Heading;
import io.github.semanticchunker.document.Image;
import io.github.semanticchunker.document.ListItem;
import io.github.semanticchunker.document.PreparedDocument;
import io.github.semanticchunker.document.Provenance;
import io.github.semanticchunker.document.Table;
import io.github.semanticchunker.extraction.ExtractionException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.Parser;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

/** Tests {@link TikaDocumentExtractor}. */
class TikaDocumentExtractorTest {

    private static final String HTML =
            "<html><body>"
                    + "<h1>Main Title</h1>"
                    + "<p>An introductory paragraph.</p>"
                    + "<ul><li>First item</li><li>Second item</li></ul>"
                    + "<table><tr><td>a</td><td>b</td></tr><tr><td>c</td><td>d</td></tr></table>"
                    + "</body></html>";

    private final TikaDocumentExtractor extractor = TikaDocumentExtractor.builder().build();

    private static DocumentSource html(String html) {
        return DocumentSource.of(html.getBytes(StandardCharsets.UTF_8), "text/html", "doc.html");
    }

    private static DocumentSource text(String text) {
        return DocumentSource.of(text.getBytes(StandardCharsets.UTF_8), "text/plain", "doc.txt");
    }

    private static void assertProvenanceInvariant(PreparedDocument document) {
        String normalized =
                document.units().stream()
                        .map(u -> u.text().orElse(""))
                        .collect(Collectors.joining());
        int expectedOrdinal = 0;
        for (DocumentUnit unit : document.units()) {
            Provenance provenance = unit.provenance();
            assertThat(provenance.globalOrdinal()).isEqualTo(expectedOrdinal++);
            assertThat(normalized.substring(provenance.startOffset(), provenance.endOffset()))
                    .isEqualTo(unit.text().orElse(""));
        }
    }

    // --- Builder -------------------------------------------------------------

    @Test
    void buildsWithDefaults() {
        assertThat(TikaDocumentExtractor.builder().build()).isNotNull();
    }

    @Test
    void rejectsNullParser() {
        assertThatNullPointerException()
                .isThrownBy(() -> TikaDocumentExtractor.builder().parser(null))
                .withMessageContaining("parser");
    }

    @Test
    void rejectsWriteLimitBelowMinusOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TikaDocumentExtractor.builder().writeLimit(-2))
                .withMessageContaining("writeLimit");
    }

    @Test
    void acceptsUnlimitedAndZeroAndPositiveWriteLimits() {
        assertThatCode(
                        () -> {
                            TikaDocumentExtractor.builder().writeLimit(-1).build();
                            TikaDocumentExtractor.builder().writeLimit(0).build();
                            TikaDocumentExtractor.builder().writeLimit(1000).build();
                        })
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsOcrToggle() {
        assertThatCode(() -> TikaDocumentExtractor.builder().ocrEnabled(true).build())
                .doesNotThrowAnyException();
    }

    @Test
    void extractsPdfWithOcrEnabled() {
        TikaDocumentExtractor ocr = TikaDocumentExtractor.builder().ocrEnabled(true).build();

        PreparedDocument document =
                ocr.extract(
                        DocumentSource.of(
                                TestDocuments.pdf("Text with OCR enabled."), "application/pdf"));

        assertThat(
                        document.units().stream()
                                .map(u -> u.text().orElse(""))
                                .collect(Collectors.joining(" ")))
                .contains("Text with OCR enabled.");
    }

    // --- supportedMediaTypes -------------------------------------------------

    @Test
    void reportsSupportedMediaTypes() {
        Set<String> types = extractor.supportedMediaTypes();

        assertThat(types).contains("application/pdf", "text/html", "text/plain").doesNotContain("");
    }

    @Test
    void supportedMediaTypesIsUnmodifiable() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> extractor.supportedMediaTypes().add("x/y"));
    }

    // --- Successful extraction per format ------------------------------------

    @Test
    void extractsPlainText() {
        PreparedDocument document = extractor.extract(text("A single line of text."));

        assertThat(document.units()).isNotEmpty();
        assertThat(document.units().get(0).text()).get().asString().contains("A single line");
        assertProvenanceInvariant(document);
    }

    @Test
    void extractsHtmlHeadingParagraphListAndTable() {
        PreparedDocument document = extractor.extract(html(HTML));

        assertThat(document.units()).anySatisfy(u -> assertThat(u).isInstanceOf(Heading.class));
        assertThat(document.units())
                .filteredOn(u -> u instanceof Heading)
                .first()
                .satisfies(h -> assertThat(h.text()).contains("Main Title"));
        assertThat(document.units()).anySatisfy(u -> assertThat(u).isInstanceOf(ListItem.class));
        assertThat(document.units()).filteredOn(u -> u instanceof ListItem).hasSize(2);
        assertThat(document.units())
                .filteredOn(u -> u instanceof Table)
                .first()
                .satisfies(
                        t -> {
                            Table table = (Table) t;
                            assertThat(table.cells())
                                    .isEqualTo(List.of(List.of("a", "b"), List.of("c", "d")));
                            assertThat(table.markdown()).contains("| a | b |");
                        });
        assertProvenanceInvariant(document);
    }

    @Test
    void extractsHtmlHeadingContent() {
        PreparedDocument document =
                extractor.extract(html("<html><body><h2>Just A Heading</h2></body></html>"));

        assertThat(document.units())
                .filteredOn(u -> u instanceof Heading)
                .first()
                .satisfies(h -> assertThat(h.text()).contains("Just A Heading"));
    }

    @Test
    void extractsPdfTextWithPageProvenanceAndNoImages() {
        PreparedDocument document =
                extractor.extract(
                        DocumentSource.of(TestDocuments.pdf("Hello from PDF."), "application/pdf"));

        assertThat(document.units()).isNotEmpty();
        assertThat(document.units()).noneMatch(u -> u instanceof Image);
        assertThat(document.units())
                .allSatisfy(u -> assertThat(u.provenance().page()).isGreaterThanOrEqualTo(1));
        assertThat(
                        document.units().stream()
                                .map(u -> u.text().orElse(""))
                                .collect(Collectors.joining(" ")))
                .contains("Hello from PDF.");
        assertProvenanceInvariant(document);
    }

    @Test
    void emitsPageBreaksForMultiPagePdf() {
        PreparedDocument document =
                extractor.extract(DocumentSource.of(TestDocuments.twoPagePdf(), "application/pdf"));

        assertThat(document.units().stream().mapToInt(u -> u.provenance().page()).max().orElse(0))
                .isEqualTo(2);
        assertProvenanceInvariant(document);
    }

    @Test
    void extractsDocxParagraphAndTable() {
        PreparedDocument document =
                extractor.extract(
                        DocumentSource.of(
                                TestDocuments.docxWithParagraphAndTable(),
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

        assertThat(
                        document.units().stream()
                                .map(u -> u.text().orElse(""))
                                .collect(Collectors.joining(" ")))
                .contains("Hello from Word.");
        assertThat(document.units())
                .filteredOn(u -> u instanceof Table)
                .first()
                .satisfies(
                        t ->
                                assertThat(((Table) t).cells())
                                        .isEqualTo(List.of(List.of("a", "b"), List.of("c", "d"))));
        assertProvenanceInvariant(document);
    }

    // --- Fidelity losses -----------------------------------------------------

    @Test
    void preservesDocumentMetadata() {
        PreparedDocument document = extractor.extract(html(HTML));

        assertThat(document.metadata()).containsKey("Content-Type");
    }

    // --- Edge cases ----------------------------------------------------------

    @Test
    void yieldsAnEmptyDocumentForWhitespaceOnlyContent() {
        PreparedDocument document = extractor.extract(text("   \n   "));

        assertThat(document.units()).isEmpty();
    }

    @Test
    void handlesZeroByteInputWithoutLeakingATikaException() {
        DocumentSource empty = DocumentSource.of(new byte[0], "text/plain");

        assertThatCode(
                        () -> {
                            try {
                                assertThat(extractor.extract(empty).units()).isEmpty();
                            } catch (ExtractionException expected) {
                                // A reported failure is also acceptable; a raw Tika exception is
                                // not.
                            }
                        })
                .doesNotThrowAnyException();
    }

    @Test
    void yieldsAnEmptyDocumentForUnrecognisedContent() {
        DocumentSource garbage =
                DocumentSource.of(
                        new byte[] {0x1, 0x2, 0x3, 0x4, 0x5}, "application/x-unknown-type");

        PreparedDocument document = extractor.extract(garbage);

        assertThat(document.units()).isEmpty();
    }

    @Test
    void failsOnEncryptedPdf() {
        DocumentSource encrypted =
                DocumentSource.of(TestDocuments.encryptedPdf(), "application/pdf");

        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> extractor.extract(encrypted))
                .withMessageContaining("encrypted");
    }

    @Test
    void failsOnCorruptedPdf() {
        DocumentSource corrupted =
                DocumentSource.of(TestDocuments.corruptedPdf(), "application/pdf");

        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> extractor.extract(corrupted));
    }

    @Test
    void failsWhenTheWriteLimitIsExceeded() {
        TikaDocumentExtractor limited = TikaDocumentExtractor.builder().writeLimit(5).build();

        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> limited.extract(text("This text is clearly longer than five.")))
                .withMessageContaining("write limit");
    }

    @Test
    void rejectsNullSource() {
        assertThatNullPointerException()
                .isThrownBy(() -> extractor.extract(null))
                .withMessageContaining("source");
    }

    // --- Exception mapping (deterministic, via a stubbed parser) -------------

    private static TikaDocumentExtractor extractorThatFailsWith(Throwable failure)
            throws Exception {
        Parser parser = mock(Parser.class);
        when(parser.getSupportedTypes(any())).thenReturn(Set.of());
        doThrow(failure).when(parser).parse(any(), any(), any(), any());
        return TikaDocumentExtractor.builder().parser(parser).build();
    }

    @Test
    void wrapsIoException() throws Exception {
        TikaDocumentExtractor failing = extractorThatFailsWith(new IOException("io boom"));

        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> failing.extract(text("x")))
                .withCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsTikaException() throws Exception {
        TikaDocumentExtractor failing = extractorThatFailsWith(new TikaException("tika boom"));

        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> failing.extract(text("x")))
                .withCauseInstanceOf(TikaException.class);
    }

    @Test
    void wrapsSaxException() throws Exception {
        TikaDocumentExtractor failing = extractorThatFailsWith(new SAXException("sax boom"));

        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> failing.extract(text("x")))
                .withCauseInstanceOf(SAXException.class);
    }

    @Test
    void wrapsEncryptedDocumentException() throws Exception {
        TikaDocumentExtractor failing =
                extractorThatFailsWith(new EncryptedDocumentException("locked"));

        assertThatExceptionOfType(ExtractionException.class)
                .isThrownBy(() -> failing.extract(text("x")))
                .withMessageContaining("encrypted")
                .withCauseInstanceOf(EncryptedDocumentException.class);
    }

    // --- Thread safety -------------------------------------------------------

    @Test
    void isSafeForConcurrentUse() throws Exception {
        PreparedDocument reference = extractor.extract(html(HTML));
        int expectedUnitCount = reference.units().size();

        int threads = 8;
        int tasksPerThread = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> tasks =
                    IntStream.range(0, threads * tasksPerThread)
                            .<Callable<Integer>>mapToObj(
                                    i -> () -> extractor.extract(html(HTML)).units().size())
                            .toList();

            List<Future<Integer>> results = pool.invokeAll(tasks);
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(expectedUnitCount);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
