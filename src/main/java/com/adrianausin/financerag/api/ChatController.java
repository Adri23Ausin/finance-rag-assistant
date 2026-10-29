package com.adrianausin.financerag.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.adrianausin.financerag.chat.Answer;
import com.adrianausin.financerag.chat.AnswerEvent;
import com.adrianausin.financerag.chat.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@Tag(name = "Chat", description = "Ask questions about the uploaded documents")
class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AssistantService assistant;

    ChatController(AssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/api/v1/chat")
    @Operation(summary = "Ask a question",
            description = "The answer cites its sources as [n]. Send the returned conversationId with follow-up questions.")
    Answer ask(@Valid @RequestBody QuestionRequest request) {
        return assistant.ask(request.toQuestion());
    }

    /**
     * Server-Sent Events: {@code sources}, then {@code token} events as the answer is generated,
     * then {@code done} with the cited source numbers (or {@code error}). Tokens are JSON objects
     * because SSE drops a leading space from each data line, and tokens often start with one.
     */
    @PostMapping(value = "/api/v1/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Ask a question and stream the answer",
            description = "Events: sources, token (repeated), done or error")
    Flux<ServerSentEvent<Object>> stream(@Valid @RequestBody QuestionRequest request) {
        return assistant.stream(request.toQuestion())
                .map(event -> ServerSentEvent.<Object>builder(event).event(switch (event) {
                    case AnswerEvent.Started started -> "sources";
                    case AnswerEvent.Token token -> "token";
                    case AnswerEvent.Completed completed -> "done";
                }).build())
                .onErrorResume(e -> {
                    log.warn("Streaming an answer failed", e);
                    return Flux.just(ServerSentEvent.<Object>builder(Map.of("message", "The answer could not be completed"))
                            .event("error")
                            .build());
                });
    }

    @GetMapping("/api/v1/conversations/{id}/messages")
    @Operation(summary = "Messages of a conversation, oldest first")
    List<MessageResponse> history(@PathVariable UUID id) {
        return assistant.history(id).stream().map(MessageResponse::from).toList();
    }

    @DeleteMapping("/api/v1/conversations/{id}")
    @Operation(summary = "Forget a conversation")
    ResponseEntity<Void> forget(@PathVariable UUID id) {
        assistant.forget(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * @param conversationId optional: continue this conversation; a new one is started without it
     * @param documentIds    optional: only search these documents
     */
    record QuestionRequest(
            @NotBlank @Size(max = 2000) String question,
            @Nullable UUID conversationId,
            @Nullable Set<UUID> documentIds) {

        AssistantService.Question toQuestion() {
            return AssistantService.Question.of(conversationId, question.strip(), documentIds);
        }
    }

    record MessageResponse(String role, String text) {

        static MessageResponse from(Message message) {
            return new MessageResponse(message.getMessageType().getValue(), message.getText());
        }
    }
}
