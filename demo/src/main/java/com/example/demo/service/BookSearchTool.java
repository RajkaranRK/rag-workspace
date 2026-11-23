package com.example.demo.service;

import com.example.demo.query.SearchResult;
import com.example.demo.repository.VectorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The agent's read-only tool for finding relevant passages in the book.
 * It owns retrieval and ranking; it does not generate an answer.
 */
@Service
public class BookSearchTool {

    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;
    private final RerankerService rerankerService;
    private final int topK;
    private final int finalTopK;
    private final double similarityThreshold;

    public BookSearchTool(
            EmbeddingService embeddingService,
            VectorRepository vectorRepository,
            RerankerService rerankerService,
            @Value("${rag.retrieval.top-k}") int topK,
            @Value("${rag.retrieval.final-top-k:3}") int finalTopK,
            @Value("${rag.retrieval.similarity-threshold:0.70}") double similarityThreshold
    ) {
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
        this.rerankerService = rerankerService;
        this.topK = topK;
        this.finalTopK = finalTopK;
        this.similarityThreshold = similarityThreshold;
    }

    public List<SearchResult> search(String query) throws Exception {
        double[] queryEmbedding =
                embeddingService.generateQueryEmbedding(query);

        List<SearchResult> candidates =
                vectorRepository.search(queryEmbedding, topK);

        List<SearchResult> eligible = candidates.stream()
                .filter(result ->
                        result.cosineSimilarity() >= similarityThreshold)
                .toList();

        if (eligible.isEmpty()) {
            return List.of();
        }

        return rerankerService.rerank(query, eligible).stream()
                .limit(finalTopK)
                .toList();
    }
}
