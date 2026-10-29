package com.adrianausin.financerag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;

class DocumentChunkerTest {

    private static final UUID DOCUMENT_ID = UUID.fromString("7d0f6f4e-2b47-4c8e-9a51-3c1d2b5e8f10");

    private final DocumentChunker chunker = new DocumentChunker(new IngestionProperties(100, 200, 1));

    @Test
    void chunksCarryTheDocumentFilenamePageAndPosition() {
        List<Document> chunks = chunker.chunk(List.of(page(1, "Net profit grew 12%."), page(2, "Total assets: EUR 84.2bn.")),
                DOCUMENT_ID, "annual-report.pdf");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata()).containsExactlyInAnyOrderEntriesOf(Map.of(
                ChunkMetadata.DOCUMENT_ID, DOCUMENT_ID.toString(),
                ChunkMetadata.FILENAME, "annual-report.pdf",
                ChunkMetadata.PAGE, 1,
                ChunkMetadata.CHUNK, 0));
        assertThat(chunks.get(1).getMetadata())
                .containsEntry(ChunkMetadata.PAGE, 2)
                .containsEntry(ChunkMetadata.CHUNK, 1);
    }

    @Test
    void longPagesAreSplitWithinTheTokenBudgetAndNeverAcrossPages() {
        String longPage = IntStream.rangeClosed(1, 60)
                .mapToObj(i -> "Note " + i + ": the group recognised impairments on its loan book in this quarter.")
                .collect(Collectors.joining(" "));

        List<Document> chunks = chunker.chunk(List.of(page(4, longPage), page(5, "Short page.")), DOCUMENT_ID, "r.pdf");

        JTokkitTokenCountEstimator tokens = new JTokkitTokenCountEstimator();
        List<Document> fromPage4 = chunks.stream().filter(c -> c.getMetadata().get(ChunkMetadata.PAGE).equals(4)).toList();
        assertThat(fromPage4).hasSizeGreaterThan(5);
        assertThat(fromPage4).allSatisfy(chunk -> assertThat(tokens.estimate(chunk.getText())).isLessThanOrEqualTo(100));
        assertThat(chunks.getLast().getText()).isEqualTo("Short page.");
        assertThat(chunks).extracting(c -> c.getMetadata().get(ChunkMetadata.CHUNK))
                .containsExactlyElementsOf(IntStream.range(0, chunks.size()).boxed().toList());
    }

    @Test
    void sectionsWithoutPagesHaveNoPageMetadata() {
        List<Document> chunks = chunker.chunk(List.of(new Document("Quarterly update for investors.")), DOCUMENT_ID, "q3.html");

        assertThat(chunks).singleElement().satisfies(chunk -> assertThat(chunk.getMetadata()).doesNotContainKey(ChunkMetadata.PAGE));
    }

    @Test
    void textIsCleanedAndBlankSectionsDropped() {
        String layoutText = "Revenue        2025        2024\r\n  Net sales    1,204.5     1,100.2  \u0000\n\n\n\nEnd";

        List<Document> chunks = chunker.chunk(List.of(page(1, layoutText), page(2, " \n\t ")), DOCUMENT_ID, "r.pdf");

        assertThat(chunks).singleElement()
                .extracting(Document::getText)
                .isEqualTo("Revenue 2025 2024\nNet sales 1,204.5 1,100.2\n\nEnd");
    }

    private static Document page(int number, String text) {
        return new Document(text, Map.of(ChunkMetadata.PAGE, number));
    }
}
