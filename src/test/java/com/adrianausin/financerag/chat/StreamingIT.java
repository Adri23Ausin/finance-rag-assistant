package com.adrianausin.financerag.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.adrianausin.financerag.support.Fixtures;
import com.adrianausin.financerag.support.IntegrationTest;
import com.adrianausin.financerag.support.ScriptedChatModel;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class StreamingIT {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ScriptedChatModel model;
    @Autowired
    private Fixtures fixtures;
    @Autowired
    private AssistantService assistant;

    @BeforeEach
    void setUp() {
        fixtures.deleteAll();
        model.reset();
        fixtures.indexed("aurelia-annual-report-2025.pdf", Fixtures.AURELIA_ANNUAL_REPORT);
    }

    @Test
    void streamsTheSourcesThenTheTokensThenTheCitations() {
        model.reply("The CET1 ratio closed 2025 at 13.2% [1].");

        List<Event> events = stream("What was the CET1 ratio of Aurelia?");

        assertThat(events.getFirst().name()).isEqualTo("sources");
        assertThat(JsonPath.<String>read(events.getFirst().data(), "$.sources[0].filename")).isEqualTo("aurelia-annual-report-2025.pdf");
        assertThat(JsonPath.<Integer>read(events.getFirst().data(), "$.sources[0].page")).isEqualTo(3);

        List<Event> tokens = events.stream().filter(event -> event.name().equals("token")).toList();
        assertThat(tokens).hasSizeGreaterThan(5);
        // Tokens keep their leading spaces because they travel as JSON
        assertThat(tokens.stream().map(token -> JsonPath.<String>read(token.data(), "$.text")).collect(Collectors.joining()))
                .isEqualTo("The CET1 ratio closed 2025 at 13.2% [1].");

        assertThat(events.getLast().name()).isEqualTo("done");
        assertThat(JsonPath.<List<Integer>>read(events.getLast().data(), "$.cited")).containsExactly(1);
    }

    @Test
    void theCompleteAnswerIsRememberedWhenTheStreamEnds() {
        model.reply("The CET1 ratio closed 2025 at 13.2% [1].");

        List<Event> events = stream("What was the CET1 ratio of Aurelia?");

        String conversationId = JsonPath.read(events.getFirst().data(), "$.conversationId");
        assertThat(assistant.history(UUID.fromString(conversationId)))
                .extracting(message -> message.getText())
                .containsExactly("What was the CET1 ratio of Aurelia?", "The CET1 ratio closed 2025 at 13.2%.");
    }

    @Test
    void nothingRelevantIsStreamedAsTheNotFoundAnswer() {
        List<Event> events = stream("Will it rain in Madrid tomorrow?");

        assertThat(events).extracting(Event::name).containsExactly("sources", "token", "done");
        assertThat(JsonPath.<List<?>>read(events.get(0).data(), "$.sources")).isEmpty();
        assertThat(JsonPath.<String>read(events.get(1).data(), "$.text")).isEqualTo(AssistantService.NOT_FOUND);
        assertThat(model.prompts()).isEmpty();
    }

    @Test
    void aFailureMidStreamEndsWithAnErrorEvent() {
        model.replyThenFail("The CET1 ratio");

        List<Event> events = stream("What was the CET1 ratio of Aurelia?");

        assertThat(events.getLast().name()).isEqualTo("error");
        assertThat(events).extracting(Event::name).doesNotContain("done");
    }

    private List<Event> stream(String question) {
        MvcTestResult result = mvc.post().uri("/api/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""
                        {"question": "%s"}""".formatted(question))
                .exchange();
        assertThat(result).hasStatusOk();
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        return Arrays.stream(body.split("\n\n"))
                .filter(block -> !block.isBlank())
                .map(Event::parse)
                .toList();
    }

    private record Event(String name, String data) {

        static Event parse(String block) {
            String name = "";
            StringBuilder data = new StringBuilder();
            for (String line : block.split("\n")) {
                if (line.startsWith("event:")) {
                    name = line.substring("event:".length());
                }
                else if (line.startsWith("data:")) {
                    data.append(line.substring("data:".length()));
                }
            }
            return new Event(name, data.toString());
        }
    }
}
