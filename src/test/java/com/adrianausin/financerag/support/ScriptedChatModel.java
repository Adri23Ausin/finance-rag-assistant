package com.adrianausin.financerag.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * A chat model for tests: replies with the answers queued by the test, in order, and records
 * every prompt it receives so tests can check what was sent to the model.
 */
public class ScriptedChatModel implements ChatModel {

    private static final String FAIL = "<fail>";

    private final Deque<String> replies = new ConcurrentLinkedDeque<>();
    private final List<Prompt> prompts = Collections.synchronizedList(new ArrayList<>());

    public void reply(String... answers) {
        replies.addAll(Arrays.asList(answers));
    }

    /** The next streamed reply emits {@code partial} and then fails, like a model server going away. */
    public void replyThenFail(String partial) {
        replies.add(partial);
        replies.add(FAIL);
    }

    public List<Prompt> prompts() {
        return List.copyOf(prompts);
    }

    public Prompt lastPrompt() {
        return prompts.getLast();
    }

    public void reset() {
        replies.clear();
        prompts.clear();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        return response(nextReply());
    }

    /** Streams the reply word by word, like a model emitting tokens. */
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        prompts.add(prompt);
        String reply = nextReply();
        Flux<ChatResponse> tokens = Flux.fromArray(reply.split("(?<= )")).map(ScriptedChatModel::response);
        if (FAIL.equals(replies.peek())) {
            replies.poll();
            return tokens.concatWith(Flux.error(new IllegalStateException("Connection to the model server lost")));
        }
        return tokens;
    }

    private String nextReply() {
        String reply = replies.poll();
        if (reply == null) {
            throw new IllegalStateException("The test did not script a reply for this call");
        }
        return reply;
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
