package com.adrianausin.financerag.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrianausin.financerag.support.Fixtures;
import com.adrianausin.financerag.support.HashingEmbeddingModel;
import com.adrianausin.financerag.support.IntegrationTest;
import com.adrianausin.financerag.support.ScriptedChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class ModelUnavailableIT {

    private static final String QUESTION = """
            {"question": "What was the CET1 ratio of Aurelia?"}""";

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private HashingEmbeddingModel embeddingModel;
    @Autowired
    private ScriptedChatModel chatModel;
    @Autowired
    private Fixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures.deleteAll();
        chatModel.reset();
        fixtures.indexed("aurelia-annual-report-2025.pdf", Fixtures.AURELIA_ANNUAL_REPORT);
        embeddingModel.unavailable(true);
    }

    @AfterEach
    void restore() {
        embeddingModel.unavailable(false);
    }

    @Test
    void questionsGetA503WithTheReason() {
        assertThat(mvc.post().uri("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyJson()
                .hasPathSatisfying("$.title", title -> title.assertThat().isEqualTo("Model unavailable"))
                .hasPathSatisfying("$.detail", detail -> detail.assertThat().asString().contains("not found, try pulling it first"));
    }

    @Test
    void streamedQuestionsGetA503BeforeTheStreamStarts() {
        assertThat(mvc.post().uri("/api/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_PROBLEM_JSON)
                .content(QUESTION))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
