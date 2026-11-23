package com.example.demo.query;

import com.example.demo.service.EmbeddingService;

import java.util.ArrayList;
import java.util.List;

public class QueryEmbeddingDemo {

    public static void main(String[] args) throws Exception {

        String question =
                "How does consistent hashing work?";

        // 1. Generate query embedding
        EmbeddingService embeddingService = null ;
                //new EmbeddingService();

        List<double[]> embeddings = new ArrayList<>();
//                embeddingService.generateEmbeddings(
//                        List.of("search_query: " + question)
//                );

        double[] queryEmbedding = embeddings.get(0);

        System.out.println(
                "Query embedding dimensions: "
                        + queryEmbedding.length
        );

        // 2. Search PostgreSQL
        VectorSearchService searchService =
                new VectorSearchService();

        List<SearchResult> results =
                searchService.search(queryEmbedding, 5);

        // 3. Print retrieved chunks
        System.out.println("\nTop 5 retrieved chunks:");

        for (SearchResult result : results) {

            System.out.println("\n----------------------------");

            System.out.println("ID: " + result.id());
            System.out.println("Page: " + result.pageNumber());

            System.out.println(
                    "Similarity: " + result.cosineSimilarity()
            );

            System.out.println("Content:");
            System.out.println(result.content());
        }
    }
}