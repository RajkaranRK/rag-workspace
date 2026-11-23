package com.example.demo.model;

import java.util.List;

public record EvaluationResult(
        String question,
        int k,
        double precisionAtK,
        double recallAtK,
        List<Long> retrievedChunkIds,
        List<Long> relevantRetrievedChunkIds
) {}
