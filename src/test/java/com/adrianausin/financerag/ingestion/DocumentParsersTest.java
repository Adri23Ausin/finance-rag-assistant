package com.adrianausin.financerag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.adrianausin.financerag.TestDocuments;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;

class DocumentParsersTest {

    private final DocumentParsers parsers = new DocumentParsers(List.of(new PdfDocumentParser(), new TikaDocumentParser()));
    private final ContentTypeDetector detector = new ContentTypeDetector();

    @Test
    void pdfIsParsedPageByPageAndBlankPagesAreSkipped() {
        byte[] pdf = TestDocuments.pdf(
                "Letter to shareholders\nNet profit grew 12% in 2025.",
                "",
                "Balance sheet\nTotal assets: EUR 84.2 billion");

        List<Document> pages = parsers.parse(new ByteArrayResource(pdf), "application/pdf");

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).getMetadata()).containsEntry(ChunkMetadata.PAGE, 1);
        assertThat(pages.get(0).getText()).contains("Net profit grew 12% in 2025.");
        assertThat(pages.get(1).getMetadata()).containsEntry(ChunkMetadata.PAGE, 3);
        assertThat(pages.get(1).getText()).contains("Total assets: EUR 84.2 billion");
    }

    @Test
    void htmlIsReducedToItsText() {
        byte[] html = "<html><body><h1>Q3 results</h1><p>Revenue rose to <b>EUR 1.2bn</b>.</p></body></html>"
                .getBytes(StandardCharsets.UTF_8);

        List<Document> sections = parsers.parse(new ByteArrayResource(html), "text/html");

        assertThat(sections).singleElement().satisfies(section -> {
            assertThat(section.getText()).contains("Q3 results").contains("Revenue rose to EUR 1.2bn.");
            assertThat(section.getText()).doesNotContain("<p>");
            assertThat(section.getMetadata()).doesNotContainKey(ChunkMetadata.PAGE);
        });
    }

    @Test
    void markdownAndPlainTextAreSupported() {
        assertThat(parsers.supports("text/markdown")).isTrue();
        assertThat(parsers.supports("text/plain")).isTrue();

        byte[] text = "Dividend per share: EUR 0.20".getBytes(StandardCharsets.UTF_8);
        assertThat(parsers.parse(new ByteArrayResource(text), "text/plain"))
                .singleElement()
                .extracting(Document::getText)
                .asString()
                .contains("Dividend per share: EUR 0.20");
    }

    @Test
    void unsupportedFormatsAreRejected() {
        assertThat(parsers.supports("image/png")).isFalse();
        assertThatThrownBy(() -> parsers.parse(new ByteArrayResource(new byte[] {1, 2, 3}), "image/png"))
                .isInstanceOf(UnsupportedFormatException.class)
                .hasMessageContaining("image/png");
    }

    @Test
    void formatIsDetectedFromTheContentNotTheName() {
        byte[] pdf = TestDocuments.pdf("Annual report");

        assertThat(detector.detect(pdf, "report.bin")).isEqualTo("application/pdf");
        assertThat(detector.detect(pdf, "report.txt")).isEqualTo("application/pdf");
        assertThat(detector.detect("<html><body>hi</body></html>".getBytes(StandardCharsets.UTF_8), "page.html"))
                .isEqualTo("text/html");
        assertThat(detector.detect("# Title".getBytes(StandardCharsets.UTF_8), "notes.md")).isEqualTo("text/markdown");
        assertThat(detector.detect("plain".getBytes(StandardCharsets.UTF_8), "notes.txt")).isEqualTo("text/plain");
    }
}
