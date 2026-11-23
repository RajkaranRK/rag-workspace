package com.example.demo.service;

import com.example.demo.model.EvaluationQuestion;
import com.example.demo.model.EvaluationComparisonResult;
import com.example.demo.model.RerankedResult;
import com.example.demo.query.SearchResult;
import com.example.demo.repository.VectorRepository;
import com.example.demo.model.EvaluationResult;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class RagEvaluationService {

    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;
    private final RerankerService rerankerService;

    public RagEvaluationService(
            EmbeddingService embeddingService,
            VectorRepository vectorRepository,
            RerankerService rerankerService) {

        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
        this.rerankerService = rerankerService;
    }

    public EvaluationComparisonResult compare(
            EvaluationQuestion evaluationQuestion,
            int k) throws Exception {

        if (k <= 0) {
            throw new IllegalArgumentException("k must be greater than zero");
        }

        // 1. Generate the question embedding
        double[] queryEmbedding =
                embeddingService.generateQueryEmbedding(
                        evaluationQuestion.question()
                );

        // 2. Retrieve Top-K candidates using vector search
        List<SearchResult> vectorResults =
                vectorRepository.search(queryEmbedding, k);

        // 3. Rerank the SAME candidates
        List<RerankedResult> rerankedResults =
                rerankerService.rerankWithScores(
                        evaluationQuestion.question(),
                        vectorResults
                );

        // 4. Build the ground-truth relevant chunk ID set
        Set<Long> expectedIds =
                Set.copyOf(evaluationQuestion.expectedChunkIds());

        // 5. Evaluate original vector-search ranking
        EvaluationComparisonResult.RankingMetrics vectorMetrics =
                calculateMetrics(
                        vectorResults.stream()
                                .map(SearchResult::id)
                                .toList(),
                        expectedIds,
                        k
                );

        // 6. Evaluate reranked ranking
        EvaluationComparisonResult.RankingMetrics rerankerMetrics =
                calculateMetrics(
                        rerankedResults.stream()
                                .map(result -> result.searchResult().id())
                                .toList(),
                        expectedIds,
                        k
                );

        // 7. Return both results
        return new EvaluationComparisonResult(
                evaluationQuestion.question(),
                k,
                vectorMetrics,
                rerankerMetrics
        );
    }

    private EvaluationComparisonResult.RankingMetrics calculateMetrics(
            List<Long> retrievedIds,
            Set<Long> expectedIds,
            int k) {

        List<Long> relevantRetrievedIds = retrievedIds.stream()
                .filter(expectedIds::contains)
                .toList();

        double precision = (double) relevantRetrievedIds.size() / k;

        double recall = expectedIds.isEmpty()
                ? 0.0
                : (double) relevantRetrievedIds.size() / expectedIds.size();

        return new EvaluationComparisonResult.RankingMetrics(
                precision,
                recall,
                retrievedIds,
                relevantRetrievedIds
        );
    }


    public EvaluationResult evaluate(
            EvaluationQuestion evaluationQuestion,
            int k) throws Exception {

        // 1. Generate embedding for the question
        double[] queryEmbedding =
                embeddingService.generateQueryEmbedding(
                        evaluationQuestion.question()
                );

        // 2. Retrieve top K chunks
        List<SearchResult> results =
                vectorRepository.search(queryEmbedding, k);

        // 3. Collect retrieved chunk IDs
        List<Long> retrievedIds = results.stream()
                .map(SearchResult::id)
                .toList();

        // 4. Ground-truth relevant chunk IDs
        Set<Long> expectedIds =
                Set.copyOf(evaluationQuestion.expectedChunkIds());

        // 5. Find relevant retrieved chunks
        List<Long> relevantRetrievedIds = retrievedIds.stream()
                .filter(expectedIds::contains)
                .toList();

        // 6. Calculate Precision@K
        double precision = k == 0
                ? 0
                : (double) relevantRetrievedIds.size() / k;

        // 7. Calculate Recall@K
        double recall = expectedIds.isEmpty()
                ? 0
                : (double) relevantRetrievedIds.size()
                / expectedIds.size();

        return new EvaluationResult(
                evaluationQuestion.question(),
                k,
                precision,
                recall,
                retrievedIds,
                relevantRetrievedIds
        );
    }
}
