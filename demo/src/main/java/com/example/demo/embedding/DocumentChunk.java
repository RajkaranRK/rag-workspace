package com.example.demo.embedding;

public record DocumentChunk(
        String documentName,
        int pageNumber,
        int chunkNumber,
        String content
) {}
