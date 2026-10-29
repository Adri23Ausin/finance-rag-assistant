package com.adrianausin.financerag.chat;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A retrieved chunk given to the model, numbered so the answer can cite it as [n].
 *
 * @param page  1-based page, null for formats without pages
 * @param score cosine similarity to the question, 0..1
 */
public record Source(int number, UUID documentId, String filename, @Nullable Integer page, String text, double score) {

    String label() {
        return page == null ? filename : filename + ", page " + page;
    }
}
