package com.adrianausin.financerag.chat;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * @param searchQuery what was searched for: the question, or its standalone rewrite for a follow-up
 * @param sources     every source given to the model, flagged when the answer cites it
 */
public record Answer(UUID conversationId, String text, String searchQuery, List<Reference> sources) {

    public record Reference(int number, UUID documentId, String filename, @Nullable Integer page, String excerpt,
                            double score, boolean cited) {

        static Reference of(Source source, boolean cited) {
            return new Reference(source.number(), source.documentId(), source.filename(), source.page(), source.text(),
                    Math.round(source.score() * 1000) / 1000.0, cited);
        }
    }
}
