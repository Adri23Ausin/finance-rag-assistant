package com.adrianausin.financerag.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import com.adrianausin.financerag.TestDocuments;
import com.adrianausin.financerag.document.DocumentService;
import com.adrianausin.financerag.document.DocumentStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Documents used across integration tests, uploaded through the service and indexed. */
@Component
public class Fixtures {

    public static final byte[] AURELIA_ANNUAL_REPORT = TestDocuments.pdf(
            "Aurelia Bank Group - Annual Report 2025\nLetter from the chair",
            "Results\nNet interest income rose 8% to EUR 4.1 billion.\nCost of risk fell to 41 basis points.",
            "Capital\nThe CET1 ratio closed the year at 13.2%.\nThe board proposes a dividend of EUR 0.48 per share.");

    public static final byte[] MERIDIAN_Q3_RELEASE = TestDocuments.pdf(
            "Meridian Retail - Q3 2026 results\nRevenue grew 5% to EUR 2.3 billion.",
            "Outlook\nMeridian keeps its interim dividend at EUR 0.15 per share.");

    private final DocumentService documents;
    private final JdbcClient jdbc;

    Fixtures(DocumentService documents, JdbcClient jdbc) {
        this.documents = documents;
        this.jdbc = jdbc;
    }

    public UUID indexed(String filename, byte[] content) {
        UUID id = documents.upload(filename, content).document().id();
        await().atMost(Duration.ofSeconds(10)).until(() -> documents.get(id).status() == DocumentStatus.INDEXED);
        return id;
    }

    public void deleteAll() {
        jdbc.sql("DELETE FROM documents").update();
        jdbc.sql("DELETE FROM vector_store").update();
    }
}
