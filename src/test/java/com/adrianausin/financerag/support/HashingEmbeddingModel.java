package com.adrianausin.financerag.support;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.retry.NonTransientAiException;

/**
 * A deterministic stand-in for a real embedding model: a bag of words hashed into 768
 * dimensions. Texts sharing words end up close in cosine distance, so similarity search in
 * PGVector behaves meaningfully in tests without a model server.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 768;

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "was", "were", "what", "which", "how", "did", "does", "its", "our", "with", "this",
            "that", "from", "are", "has", "have", "had", "about", "in", "of", "to", "is", "a", "an", "on", "at", "by");

    private volatile boolean unavailable;

    /** Simulates a model server that is down or does not have the model. */
    public void unavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        failIfUnavailable();
        List<String> texts = request.getInstructions();
        return new EmbeddingResponse(IntStream.range(0, texts.size())
                .mapToObj(i -> new Embedding(embed(texts.get(i)), i))
                .toList());
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        failIfUnavailable();
        float[] vector = new float[DIMENSIONS];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() > 1 && !STOP_WORDS.contains(word)) {
                vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1;
            }
        }
        double norm = Math.sqrt(IntStream.range(0, DIMENSIONS).mapToDouble(i -> vector[i] * vector[i]).sum());
        if (norm == 0) {
            vector[0] = 1; // cosine distance is undefined for the zero vector
            return vector;
        }
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] /= (float) norm;
        }
        return vector;
    }

    private void failIfUnavailable() {
        if (unavailable) {
            throw new NonTransientAiException("HTTP 404 - {\"error\":\"model \\\"nomic-embed-text\\\" not found, try pulling it first\"}");
        }
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }
}
