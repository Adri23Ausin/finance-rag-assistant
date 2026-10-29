package com.adrianausin.financerag.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.adrianausin.financerag.TestDocuments;
import com.adrianausin.financerag.ingestion.ChunkMetadata;
import com.adrianausin.financerag.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class DocumentIndexingIT {

    private static final byte[] ANNUAL_REPORT = TestDocuments.pdf(
            "Aurelia Bank Group - Annual Report 2025\nLetter from the chair",
            "Results\nNet interest income rose 8% to EUR 4.1 billion.\nCost of risk fell to 41 basis points.",
            "Capital\nThe CET1 ratio closed the year at 13.2%.");

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private DocumentRepository documents;
    @Autowired
    private DocumentService service;

    @BeforeEach
    void cleanUp() {
        jdbc.sql("DELETE FROM documents").update();
        jdbc.sql("DELETE FROM vector_store").update();
    }

    @Test
    void uploadedPdfIsIndexedInTheBackgroundWithPageNumbers() {
        MvcTestResult upload = upload("annual-report-2025.pdf", ANNUAL_REPORT);

        assertThat(upload).hasStatus(HttpStatus.ACCEPTED);
        assertThat(upload).bodyJson().extractingPath("$.status").isEqualTo("INDEXING");
        UUID id = idOf(upload);
        assertThat(upload.getResponse().getHeader("Location")).isEqualTo("/api/v1/documents/" + id);

        awaitStatus(id, DocumentStatus.INDEXED);
        assertThat(mvc.get().uri("/api/v1/documents/{id}", id)).bodyJson()
                .hasPathSatisfying("$.pages", pages -> pages.assertThat().isEqualTo(3))
                .hasPathSatisfying("$.chunks", chunks -> chunks.assertThat().isEqualTo(3))
                .hasPathSatisfying("$.contentType", type -> type.assertThat().isEqualTo("application/pdf"));

        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder().query("CET1 ratio capital").topK(1).build());
        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.getText()).contains("13.2%");
            assertThat(hit.getMetadata())
                    .containsEntry(ChunkMetadata.DOCUMENT_ID, id.toString())
                    .containsEntry(ChunkMetadata.FILENAME, "annual-report-2025.pdf")
                    .containsEntry(ChunkMetadata.PAGE, 3);
        });
    }

    @Test
    void theSameFileIsStoredOnce() {
        UUID first = idOf(upload("report.pdf", ANNUAL_REPORT));
        awaitStatus(first, DocumentStatus.INDEXED);

        MvcTestResult again = upload("copy-of-report.pdf", ANNUAL_REPORT);

        assertThat(again).hasStatusOk();
        assertThat(idOf(again)).isEqualTo(first);
        assertThat(documents.findAll()).hasSize(1);
        assertThat(chunkCount()).isEqualTo(3);
    }

    @Test
    void unsupportedAndEmptyFilesAreRejected() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};

        assertThat(upload("chart.pdf", png)).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .bodyJson().extractingPath("$.detail").asString().contains("image/png");
        assertThat(upload("empty.pdf", new byte[0])).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("The file is empty");
        assertThat(documents.findAll()).isEmpty();
    }

    @Test
    void documentWithoutTextFailsWithAReason() {
        UUID id = idOf(upload("scanned.pdf", TestDocuments.pdf("", "")));

        awaitStatus(id, DocumentStatus.FAILED);
        assertThat(service.get(id).error()).contains("No text found");
        assertThat(chunkCount()).isZero();
    }

    @Test
    void plainTextDocumentsHaveNoPages() {
        UUID id = idOf(upload("notes.txt", "Board approved a dividend of EUR 0.20 per share.".getBytes(StandardCharsets.UTF_8)));

        awaitStatus(id, DocumentStatus.INDEXED);
        assertThat(service.get(id).pages()).isNull();
        assertThat(service.get(id).contentType()).isEqualTo("text/plain");
    }

    @Test
    void deletingADocumentRemovesItsChunks() {
        UUID keep = idOf(upload("notes.txt", "Dividend of EUR 0.20 per share.".getBytes(StandardCharsets.UTF_8)));
        UUID delete = idOf(upload("report.pdf", ANNUAL_REPORT));
        awaitStatus(keep, DocumentStatus.INDEXED);
        awaitStatus(delete, DocumentStatus.INDEXED);

        assertThat(mvc.delete().uri("/api/v1/documents/{id}", delete)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(mvc.get().uri("/api/v1/documents/{id}", delete)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.delete().uri("/api/v1/documents/{id}", delete)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(chunkCount()).isEqualTo(1);
    }

    @Test
    void reindexingReplacesTheChunks() {
        UUID id = idOf(upload("report.pdf", ANNUAL_REPORT));
        awaitStatus(id, DocumentStatus.INDEXED);
        List<UUID> before = chunkIds();

        assertThat(mvc.post().uri("/api/v1/documents/{id}/reindex", id)).hasStatus(HttpStatus.ACCEPTED);

        await().atMost(Duration.ofSeconds(10)).until(() -> !chunkIds().isEmpty() && !chunkIds().equals(before));
        awaitStatus(id, DocumentStatus.INDEXED);
        assertThat(chunkCount()).isEqualTo(3);
    }

    @Test
    void theOriginalFileCanBeDownloaded() {
        UUID id = idOf(upload("annual report.pdf", ANNUAL_REPORT));

        MvcTestResult file = mvc.get().uri("/api/v1/documents/{id}/file", id).exchange();

        assertThat(file).hasStatusOk().hasContentType(MediaType.APPLICATION_PDF);
        assertThat(file.getResponse().getContentAsByteArray()).isEqualTo(ANNUAL_REPORT);
        assertThat(file.getResponse().getHeader("Content-Disposition")).startsWith("inline").contains("annual report.pdf");
        awaitStatus(id, DocumentStatus.INDEXED);
    }

    @Test
    void documentsInterruptedByARestartAreIndexedAgain() {
        UUID id = UUID.randomUUID();
        byte[] content = "Interim dividend of EUR 0.10.".getBytes(StandardCharsets.UTF_8);
        documents.insert(new StoredDocument(id, "interim.txt", "text/plain", content.length, "a".repeat(64),
                DocumentStatus.INDEXING, null, null, null, Instant.now(), null), content);

        service.resumeInterruptedIndexing();

        awaitStatus(id, DocumentStatus.INDEXED);
        assertThat(chunkCount()).isEqualTo(1);
    }

    private MvcTestResult upload(String filename, byte[] content) {
        return mvc.post().uri("/api/v1/documents")
                .multipart()
                .file(new MockMultipartFile("file", filename, MediaType.APPLICATION_OCTET_STREAM_VALUE, content))
                .exchange();
    }

    private static UUID idOf(MvcTestResult result) {
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private void awaitStatus(UUID id, DocumentStatus status) {
        await().atMost(Duration.ofSeconds(10)).until(() -> service.get(id).status() == status);
    }

    private int chunkCount() {
        return jdbc.sql("SELECT count(*) FROM vector_store").query(Integer.class).single();
    }

    private List<UUID> chunkIds() {
        return jdbc.sql("SELECT id FROM vector_store ORDER BY id").query(UUID.class).list();
    }
}
