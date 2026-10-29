package com.adrianausin.financerag.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.adrianausin.financerag.support.Fixtures;
import com.adrianausin.financerag.support.IntegrationTest;
import com.adrianausin.financerag.support.ScriptedChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class AssistantIT {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ScriptedChatModel model;
    @Autowired
    private Fixtures fixtures;

    private UUID aurelia;
    private UUID meridian;

    @BeforeEach
    void setUp() {
        fixtures.deleteAll();
        model.reset();
        aurelia = fixtures.indexed("aurelia-annual-report-2025.pdf", Fixtures.AURELIA_ANNUAL_REPORT);
        meridian = fixtures.indexed("meridian-q3-2026.pdf", Fixtures.MERIDIAN_Q3_RELEASE);
    }

    @Test
    void answersFromTheSourcesAndFlagsTheOnesItCites() {
        model.reply("Aurelia's CET1 ratio closed 2025 at 13.2% [1].");

        MvcTestResult result = ask("""
                {"question": "What was the CET1 ratio of Aurelia?"}""");

        assertThat(result).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.text", text -> text.assertThat().isEqualTo("Aurelia's CET1 ratio closed 2025 at 13.2% [1]."))
                .hasPathSatisfying("$.sources[0].number", n -> n.assertThat().isEqualTo(1))
                .hasPathSatisfying("$.sources[0].filename", f -> f.assertThat().isEqualTo("aurelia-annual-report-2025.pdf"))
                .hasPathSatisfying("$.sources[0].page", p -> p.assertThat().isEqualTo(3))
                .hasPathSatisfying("$.sources[0].documentId", id -> id.assertThat().isEqualTo(aurelia.toString()))
                .hasPathSatisfying("$.sources[0].excerpt", e -> e.assertThat().asString().contains("13.2%"))
                .hasPathSatisfying("$.sources[0].cited", c -> c.assertThat().isEqualTo(true))
                .hasPathSatisfying("$.sources[?(@.number > 1)].cited", c -> c.assertThat().asArray().containsOnly(false));
    }

    @Test
    void theModelGetsNumberedSourcesInTheSystemPromptAndTheQuestionAsUserMessage() {
        model.reply("13.2% [1].");

        ask("""
                {"question": "What was the CET1 ratio of Aurelia?"}""");

        Prompt prompt = model.lastPrompt();
        assertThat(prompt.getInstructions()).extracting(message -> message.getMessageType())
                .containsExactly(MessageType.SYSTEM, MessageType.USER);
        // StringTemplate writes the template's line breaks with the platform separator
        assertThat(prompt.getSystemMessage().getText().replace("\r\n", "\n"))
                .contains("Cite every fact")
                .contains("<sources>\n[1] aurelia-annual-report-2025.pdf, page 3\nCapital\nThe CET1 ratio closed the year at 13.2%.");
        assertThat(prompt.getUserMessage().getText()).isEqualTo("What was the CET1 ratio of Aurelia?");
    }

    @Test
    void questionsWithNothingRelevantAreAnsweredWithoutCallingTheModel() {
        MvcTestResult result = ask("""
                {"question": "Will it rain in Madrid tomorrow?"}""");

        assertThat(result).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.text", text -> text.assertThat().isEqualTo(AssistantService.NOT_FOUND))
                .hasPathSatisfying("$.sources", sources -> sources.assertThat().asArray().isEmpty());
        assertThat(model.prompts()).isEmpty();
    }

    @Test
    void searchCanBeLimitedToSomeDocuments() {
        model.reply("Meridian keeps its interim dividend at EUR 0.15 per share [1].");

        MvcTestResult result = ask("""
                {"question": "What dividend per share was announced?", "documentIds": ["%s"]}""".formatted(meridian));

        assertThat(result).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.sources[*].documentId", ids -> ids.assertThat().asArray().containsOnly(meridian.toString()));
        assertThat(model.lastPrompt().getSystemMessage().getText()).doesNotContain("Aurelia");
    }

    @Test
    void citationsOfSourcesThatDoNotExistAreIgnored() {
        model.reply("Revenue grew 5% [1], see also [9].");

        MvcTestResult result = ask("""
                {"question": "How much did Meridian revenue grow?"}""");

        assertThat(result).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.sources[?(@.cited == true)].number", n -> n.assertThat().asArray().containsExactly(1));
    }

    @Test
    void blankAndOversizedQuestionsAreRejected() {
        assertThat(ask("""
                {"question": "  "}""")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(ask("""
                {"question": "%s"}""".formatted("a".repeat(2001)))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(model.prompts()).isEmpty();
    }

    private MvcTestResult ask(String body) {
        return mvc.post().uri("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }
}
