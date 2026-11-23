package com.example.demo.embedding;

public record EmbeddedChunk(
        DocumentChunk documentChunk,
        double[] embedding
) {}
