package com.adrianausin.financerag.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.adrianausin.financerag.support.Fixtures;
import com.adrianausin.financerag.support.IntegrationTest;
import com.adrianausin.financerag.support.ScriptedChatModel;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class ConversationIT {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ScriptedChatModel model;
    @Autowired
    private Fixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures.deleteAll();
        model.reset();
        fixtures.indexed("aurelia-annual-report-2025.pdf", Fixtures.AURELIA_ANNUAL_REPORT);
        fixtures.indexed("meridian-q3-2026.pdf", Fixtures.MERIDIAN_Q3_RELEASE);
    }

    @Test
    void followUpsAreRewrittenWithTheConversationBeforeSearching() {
        model.reply("Aurelia's CET1 ratio closed 2025 at 13.2% [1].");
        UUID conversation = conversationOf(ask(null, "What was the CET1 ratio of Aurelia?"));

        model.reply("What dividend per share does the Aurelia board propose?", "EUR 0.48 per share [1].");
        MvcTestResult followUp = ask(conversation, "And the dividend?");

        assertThat(followUp).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.conversationId", id -> id.assertThat().isEqualTo(conversation.toString()))
                .hasPathSatisfying("$.searchQuery", q -> q.assertThat().isEqualTo("What dividend per share does the Aurelia board propose?"))
                .hasPathSatisfying("$.sources[0].filename", f -> f.assertThat().isEqualTo("aurelia-annual-report-2025.pdf"))
                .hasPathSatisfying("$.sources[0].cited", c -> c.assertThat().isEqualTo(true));

        List<Prompt> prompts = model.prompts();
        assertThat(prompts).hasSize(3);
        assertThat(prompts.get(1).getUserMessage().getText())
                .contains("USER: What was the CET1 ratio of Aurelia?")
                .contains("ASSISTANT: Aurelia's CET1 ratio closed 2025 at 13.2%.")
                .contains("And the dividend?");
    }

    @Test
    void theModelSeesThePreviousTurnsWithoutTheirCitationMarkers() {
        model.reply("Aurelia's CET1 ratio closed 2025 at 13.2% [1].");
        UUID conversation = conversationOf(ask(null, "What was the CET1 ratio of Aurelia?"));
        model.reply("What dividend per share does Aurelia propose?", "EUR 0.48 [1].");

        ask(conversation, "And the dividend?");

        List<Message> messages = model.lastPrompt().getInstructions();
        assertThat(messages).extracting(Message::getMessageType).containsExactly(
                MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT, MessageType.USER);
        assertThat(messages.get(1).getText()).isEqualTo("What was the CET1 ratio of Aurelia?");
        assertThat(messages.get(2).getText()).isEqualTo("Aurelia's CET1 ratio closed 2025 at 13.2%.");
        assertThat(messages.get(3).getText()).isEqualTo("And the dividend?");
    }

    @Test
    void historyCanBeReadAndForgotten() {
        model.reply("Revenue grew 5% to EUR 2.3 billion [1].", "Will it rain in Madrid tomorrow?");
        UUID conversation = conversationOf(ask(null, "How much did Meridian revenue grow?"));
        ask(conversation, "Will it rain tomorrow?"); // rewritten, nothing found, no answer generated, still remembered

        assertThat(model.prompts()).hasSize(2);

        assertThat(mvc.get().uri("/api/v1/conversations/{id}/messages", conversation)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$[*].role", roles -> roles.assertThat().asArray()
                        .containsExactly("user", "assistant", "user", "assistant"))
                .hasPathSatisfying("$[3].text", text -> text.assertThat().isEqualTo(AssistantService.NOT_FOUND));

        assertThat(mvc.delete().uri("/api/v1/conversations/{id}", conversation)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/v1/conversations/{id}/messages", conversation)).bodyJson()
                .hasPathSatisfying("$", messages -> messages.assertThat().asArray().isEmpty());
    }

    @Test
    void conversationsAreKeptApart() {
        model.reply("13.2% [1].", "EUR 2.3 billion [1].");
        conversationOf(ask(null, "What was the CET1 ratio of Aurelia?"));

        ask(null, "What was Meridian revenue?");

        assertThat(model.prompts()).hasSize(2); // no rewrite: the second question starts a new conversation
        assertThat(model.lastPrompt().getInstructions()).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.USER);
    }

    private MvcTestResult ask(UUID conversationId, String question) {
        String body = conversationId == null
                ? """
                {"question": "%s"}""".formatted(question)
                : """
                {"question": "%s", "conversationId": "%s"}""".formatted(question, conversationId);
        return mvc.post().uri("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private static UUID conversationOf(MvcTestResult result) {
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        return UUID.fromString(JsonPath.read(body, "$.conversationId"));
    }
}
