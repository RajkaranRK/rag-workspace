package com.example.demo.query;

public record SearchResult(
        long id,
        String documentName,
        int pageNumber,
        int chunkNumber,
        String content,
        double cosineSimilarity
) {}
