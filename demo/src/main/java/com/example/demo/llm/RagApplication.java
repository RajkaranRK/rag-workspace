package com.example.demo.llm;

import com.example.demo.service.EmbeddingService;
import com.example.demo.query.SearchResult;
import com.example.demo.query.VectorSearchService;

import java.util.List;

public class RagApplication {

    public static void main(String[] args) throws Exception {

        String question =
                "Explain consistent hashing and why it is useful "
                        + "when adding or removing servers.";

        // 1. Generate query embedding
//        EmbeddingService embeddingService =
//                new EmbeddingService();
//
//        List<double[]> embeddings =
//                embeddingService.generateEmbeddings(
//                        List.of("search_query: " + question)
//                );

        double[] queryEmbedding = new double[0]; //embeddings.get(0);

        // 2. Retrieve relevant chunks from PostgreSQL
        VectorSearchService vectorSearchService =
                new VectorSearchService();

        List<SearchResult> results =
                vectorSearchService.search(queryEmbedding, 5);

        System.out.println("Retrieved chunks: " + results.size());

        // 3. Build prompt using retrieved context
        RagPromptBuilder promptBuilder =
                new RagPromptBuilder();

        String prompt =
                promptBuilder.buildPrompt(question, results);

        // 4. Generate answer using Qwen3
        OllamaChatService chatService =
                new OllamaChatService();

        String answer =
                chatService.generateAnswer(prompt);

        // 5. Display final answer
        System.out.println("\n========== RAG ANSWER ==========\n");

        System.out.println(answer);

        System.out.println("\n================================");
    }
}
