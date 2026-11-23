package com.example.demo.model;


import com.example.demo.query.SearchResult;

public record RerankedResult(
        SearchResult searchResult,
        double rerankerScore
) {}
