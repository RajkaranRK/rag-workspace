package com.example.demo.model;

import java.util.List;

/**
 * Event carried over Kafka from the agent (producer) to this app (consumer).
 * It describes one web-sourced, already-validated answer that should be
 * embedded and stored in the vector database so future questions are served
 * from the book/vector store instead of the internet.
 */
public record LearnedAnswerEvent(
        String eventId,
        String question,
        String answer,
        List<String> sourceUrls,
        String provider,
        String createdAt,
        Long sourceId
) {}
