package com.adrianausin.financerag.chat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** What a streamed answer is made of: the sources first, then the text as it is generated, then the citations. */
public sealed interface AnswerEvent {

    record Started(UUID conversationId, String searchQuery, List<Answer.Reference> sources) implements AnswerEvent {
    }

    record Token(String text) implements AnswerEvent {
    }

    /** @param cited the numbers of the sources the complete answer cites */
    record Completed(Set<Integer> cited) implements AnswerEvent {
    }
}
