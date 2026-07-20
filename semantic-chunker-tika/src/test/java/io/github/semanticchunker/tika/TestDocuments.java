package io.github.semanticchunker.tika;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;

/**
 * Builds small, real documents in memory for the Tika extractor tests, using the PDFBox and POI
 * libraries Tika already depends on. Keeping the fixtures generated (rather than checked-in
 * binaries) makes them transparent and version-aligned with the parser stack under test.
 */
final class TestDocuments {

    private TestDocuments() {}

    static byte[] pdf(String... lines) {
        try (PDDocument document = new PDDocument()) {
            addPage(document, lines);
            return save(document);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build test PDF", e);
        }
    }

    static byte[] twoPagePdf() {
        try (PDDocument document = new PDDocument()) {
            addPage(document, "First page paragraph.");
            addPage(document, "Second page paragraph.");
            return save(document);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build two-page test PDF", e);
        }
    }

    static byte[] encryptedPdf() {
        try (PDDocument document = new PDDocument()) {
            addPage(document, "Secret content.");
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-pw", "user-pw", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            return save(document);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build encrypted test PDF", e);
        }
    }

    /** A valid PDF header followed by truncation — enough to be detected as PDF but not parsed. */
    static byte[] corruptedPdf() {
        byte[] valid = pdf("Whole document.");
        return Arrays.copyOf(valid, 32);
    }

    static byte[] docxWithParagraphAndTable() {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Hello from Word.");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("a");
            table.getRow(0).getCell(1).setText("b");
            table.getRow(1).getCell(0).setText("c");
            table.getRow(1).getCell(1).setText("d");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build test DOCX", e);
        }
    }

    private static void addPage(PDDocument document, String... lines) throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(72, 720);
            content.setLeading(16);
            for (String line : lines) {
                content.showText(line);
                content.newLine();
            }
            content.endText();
        }
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }
}
