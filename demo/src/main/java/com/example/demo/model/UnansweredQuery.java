package com.example.demo.model;

/** A row in the unanswered_queries table. */
public record UnansweredQuery(
        long id,
        String question,
        String status,
        String answer,
        String sourceEventId,
        String createdAt
) {}
