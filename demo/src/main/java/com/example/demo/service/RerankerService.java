package com.example.demo.service;



import com.example.demo.model.RerankedResult;
import com.example.demo.query.SearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class RerankerService {

    private final RestClient restClient;

    public RerankerService(
            @Value("${rag.reranker.base-url}") String baseUrl) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    // Existing method: preserves compatibility with RagService
    public List<SearchResult> rerank(
            String question,
            List<SearchResult> candidates) {

        return rerankWithScores(question, candidates)
                .stream()
                .map(RerankedResult::searchResult)
                .toList();
    }

    // New method: retains the cross-encoder score
    public List<RerankedResult> rerankWithScores(
            String question,
            List<SearchResult> candidates) {

        if (candidates.isEmpty()) {
            return List.of();
        }

        RerankRequest request = new RerankRequest(
                question,
                candidates.stream()
                        .map(SearchResult::content)
                        .toList()
        );

        RerankResponse response = restClient.post()
                .uri("/rerank")
                .body(request)
                .retrieve()
                .body(RerankResponse.class);

        if (response == null || response.results() == null) {
            throw new IllegalStateException(
                    "Reranker returned an empty response");
        }

        return response.results().stream()
                .map(result -> {
                    int index = result.documentIndex();

                    if (index < 0 || index >= candidates.size()) {
                        throw new IllegalStateException(
                                "Invalid document index from reranker: " + index);
                    }

                    return new RerankedResult(
                            candidates.get(index),
                            result.score()
                    );
                })
                .toList();
    }

    private record RerankRequest(
            String question,
            List<String> documents) {}

    private record RerankResponse(
            List<RerankItem> results) {}

    private record RerankItem(
            int documentIndex,
            double score,
            String content) {}
}
