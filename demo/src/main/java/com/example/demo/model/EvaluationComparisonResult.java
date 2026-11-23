package com.example.demo.model;

import java.util.List;

public record EvaluationComparisonResult(
        String question,
        int k,
        RankingMetrics vectorSearch,
        RankingMetrics reranking
) {
    public record RankingMetrics(
            double precisionAtK,
            double recallAtK,
            List<Long> retrievedChunkIds,
            List<Long> relevantRetrievedChunkIds
    ) {}
}

