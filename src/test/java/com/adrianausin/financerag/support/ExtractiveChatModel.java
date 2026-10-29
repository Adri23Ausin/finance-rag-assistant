package com.adrianausin.financerag.support;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * A stand-in model for running the application without Ollama: it "answers" by quoting the
 * best source and citing it, and leaves follow-up questions as they are. Good enough to work on
 * the UI and the API; it does not understand anything.
 */
class ExtractiveChatModel implements ChatModel {

    private static final Pattern FIRST_SOURCE = Pattern.compile("<sources>\\s*\\[1] [^\\n]*\\n(.+?)(?:\\n\\n\\[2]|\\s*</sources>)", Pattern.DOTALL);
    private static final Pattern FOLLOW_UP = Pattern.compile("Follow-up query:\\s*(.+?)\\s*Standalone query:", Pattern.DOTALL);

    @Override
    public ChatResponse call(Prompt prompt) {
        return response(reply(prompt));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.fromArray(reply(prompt).split("(?<= )"))
                .delayElements(Duration.ofMillis(30))
                .map(ExtractiveChatModel::response);
    }

    private static String reply(Prompt prompt) {
        Matcher followUp = FOLLOW_UP.matcher(prompt.getUserMessage().getText());
        if (followUp.find()) {
            return followUp.group(1);
        }
        Matcher source = FIRST_SOURCE.matcher(prompt.getSystemMessage().getText().replace("\r\n", "\n"));
        if (!source.find()) {
            return "I could not find that in the documents.";
        }
        return "The most relevant passage says:\n\n" + source.group(1).strip().replace('\n', ' ') + " [1]";
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
