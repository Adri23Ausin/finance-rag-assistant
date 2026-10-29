package com.adrianausin.financerag.chat;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Retrieval-augmented answers: the question is matched against the indexed chunks, the best ones
 * go to the model as numbered sources, and the answer cites them by number.
 */
@Service
public class AssistantService {

    static final String NOT_FOUND = "I could not find information about that in the uploaded documents.";

    private final ChatClient chatClient;
    private final Retriever retriever;
    private final Resource systemPrompt;

    AssistantService(ChatClient.Builder chatClientBuilder, Retriever retriever,
                     @Value("classpath:prompts/system.st") Resource systemPrompt) {
        this.chatClient = chatClientBuilder.build();
        this.retriever = retriever;
        this.systemPrompt = systemPrompt;
    }

    public Answer ask(Question question) {
        List<Source> sources = retriever.retrieve(question.text(), question.documentIds());
        if (sources.isEmpty()) {
            // Nothing relevant: answering anyway would only invite the model to make something up
            return new Answer(NOT_FOUND, List.of());
        }

        String answer = Objects.requireNonNullElse(chatClient.prompt()
                .system(system -> system.text(systemPrompt).param("sources", format(sources)))
                .user(question.text())
                .call()
                .content(), "");

        return new Answer(answer, citedSources(answer, sources));
    }

    static String format(List<Source> sources) {
        return sources.stream()
                .map(source -> "[" + source.number() + "] " + source.label() + "\n" + source.text())
                .collect(Collectors.joining("\n\n"));
    }

    static List<Answer.Reference> citedSources(String answer, List<Source> sources) {
        Set<Integer> cited = Citations.cited(answer, sources.size());
        return sources.stream()
                .map(source -> Answer.Reference.of(source, cited.contains(source.number())))
                .toList();
    }

    /** @param documentIds limit the search to these documents; empty for all */
    public record Question(String text, Set<UUID> documentIds) {
    }
}
