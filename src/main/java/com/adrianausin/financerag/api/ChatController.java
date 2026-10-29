package com.adrianausin.financerag.api;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.adrianausin.financerag.chat.Answer;
import com.adrianausin.financerag.chat.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "Ask questions about the uploaded documents")
class ChatController {

    private final AssistantService assistant;

    ChatController(AssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping
    @Operation(summary = "Ask a question", description = "The answer cites its sources as [n]; sources lists them with file, page and excerpt")
    Answer ask(@Valid @RequestBody QuestionRequest request) {
        return assistant.ask(request.toQuestion());
    }

    /** @param documentIds optional: only search these documents */
    record QuestionRequest(
            @NotBlank @Size(max = 2000) String question,
            @Nullable Set<UUID> documentIds) {

        AssistantService.Question toQuestion() {
            return new AssistantService.Question(question.strip(), Objects.requireNonNullElse(documentIds, Set.of()));
        }
    }
}
