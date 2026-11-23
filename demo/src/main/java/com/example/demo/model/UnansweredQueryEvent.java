package com.example.demo.model;

/** Phase-1 Kafka event: a question the book could not answer. */
public record UnansweredQueryEvent(
        String eventId,
        String question,
        String createdAt
) {}
