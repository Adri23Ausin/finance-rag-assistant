package com.adrianausin.financerag.chat;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Retrieval-augmented answers: the question is matched against the indexed chunks, the best ones
 * go to the model as numbered sources, and the answer cites them by number. Conversations are
 * remembered, so follow-up questions work.
 */
@Service
public class AssistantService {

    static final String NOT_FOUND = "I could not find information about that in the uploaded documents.";

    private final ChatClient chatClient;
    private final Retriever retriever;
    private final ChatMemory memory;
    private final QueryTransformer followUpRewriter;
    private final RetrievalProperties properties;
    private final Resource systemPrompt;

    AssistantService(ChatClient.Builder chatClientBuilder, Retriever retriever, ChatMemory memory,
                     QueryTransformer followUpRewriter, RetrievalProperties properties,
                     @Value("classpath:prompts/system.st") Resource systemPrompt) {
        this.chatClient = chatClientBuilder.build();
        this.retriever = retriever;
        this.memory = memory;
        this.followUpRewriter = followUpRewriter;
        this.properties = properties;
        this.systemPrompt = systemPrompt;
    }

    public Answer ask(Question question) {
        Turn turn = prepare(question);
        if (turn.sources().isEmpty()) {
            remember(turn, NOT_FOUND);
            return new Answer(question.conversationId(), NOT_FOUND, turn.searchQuery(), List.of());
        }

        String answer = Objects.requireNonNullElse(prompt(turn).call().content(), "");

        remember(turn, answer);
        return new Answer(question.conversationId(), answer, turn.searchQuery(), citedSources(answer, turn.sources()));
    }

    /**
     * The same answer, streamed as the model generates it. Retrieval happens before the stream
     * starts, so its errors are ordinary HTTP errors; the conversation is updated once the answer
     * is complete.
     */
    public Flux<AnswerEvent> stream(Question question) {
        Turn turn = prepare(question);
        var started = new AnswerEvent.Started(question.conversationId(), turn.searchQuery(),
                turn.sources().stream().map(source -> Answer.Reference.of(source, false)).toList());
        if (turn.sources().isEmpty()) {
            remember(turn, NOT_FOUND);
            return Flux.just(started, new AnswerEvent.Token(NOT_FOUND), new AnswerEvent.Completed(Set.of()));
        }

        StringBuilder answer = new StringBuilder();
        Flux<AnswerEvent> tokens = prompt(turn).stream().content()
                .doOnNext(answer::append)
                .map(AnswerEvent.Token::new);
        Mono<AnswerEvent> completed = Mono.fromCallable(() -> {
            remember(turn, answer.toString());
            return new AnswerEvent.Completed(Citations.cited(answer.toString(), turn.sources().size()));
        });
        return Flux.concat(Flux.just(started), tokens, completed);
    }

    public List<Message> history(UUID conversationId) {
        return memory.get(conversationId.toString());
    }

    public void forget(UUID conversationId) {
        memory.clear(conversationId.toString());
    }

    private Turn prepare(Question question) {
        String conversationId = question.conversationId().toString();
        List<Message> history = memory.get(conversationId);
        String searchQuery = searchQuery(question.text(), history);
        List<Source> sources = retriever.retrieve(searchQuery, question.documentIds());
        return new Turn(conversationId, question.text(), history, searchQuery, sources);
    }

    private ChatClient.ChatClientRequestSpec prompt(Turn turn) {
        return chatClient.prompt()
                .system(system -> system.text(systemPrompt).param("sources", format(turn.sources())))
                .messages(turn.history())
                .user(turn.question());
    }

    /** Follow-ups often only make sense with the conversation ("and in 2024?"), so they are searched as rewritten. */
    private String searchQuery(String question, List<Message> history) {
        if (history.isEmpty() || !properties.rewriteFollowUps()) {
            return question;
        }
        return followUpRewriter.transform(Query.builder().text(question).history(history).build()).text().strip();
    }

    /**
     * Nothing relevant is answered without calling the model: answering anyway would only invite
     * it to make something up. Citation markers are not remembered: [1] in an earlier answer points
     * at that turn's sources, and would be read as a reference to the current ones.
     */
    private void remember(Turn turn, String answer) {
        memory.add(turn.conversationId(), List.of(new UserMessage(turn.question()), new AssistantMessage(Citations.strip(answer))));
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

    private record Turn(String conversationId, String question, List<Message> history, String searchQuery,
                        List<Source> sources) {
    }

    /**
     * @param conversationId the conversation the question belongs to
     * @param documentIds    limit the search to these documents; empty for all
     */
    public record Question(UUID conversationId, String text, Set<UUID> documentIds) {

        public static Question of(@Nullable UUID conversationId, String text, @Nullable Set<UUID> documentIds) {
            return new Question(Objects.requireNonNullElseGet(conversationId, UUID::randomUUID), text,
                    Objects.requireNonNullElse(documentIds, Set.of()));
        }
    }
}
