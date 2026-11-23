package com.example.demo.embedding;

import com.example.demo.service.EmbeddingService;

import java.util.ArrayList;
import java.util.List;

public class BatchEmbeddingProcessor {

    private static final int BATCH_SIZE = 16;

    private final EmbeddingService embeddingService;

    public BatchEmbeddingProcessor(
            EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    public List<EmbeddedChunk> process(
            List<DocumentChunk> chunks) throws Exception {

        List<EmbeddedChunk> results = new ArrayList<>();

        int totalChunks = chunks.size();

        for (int start = 0; start < totalChunks;
             start += BATCH_SIZE) {

            int end = Math.min(
                    start + BATCH_SIZE,
                    totalChunks
            );

            List<DocumentChunk> batch =
                    chunks.subList(start, end);

            // Add Nomic document prefix
            List<String> texts = batch.stream()
                    .map(chunk ->
                            "search_document: " + chunk.content())
                    .toList();

            // Generate embeddings for this batch
            List<double[]> vectors = new ArrayList<>();
//                    embeddingService.generateEmbeddings(texts);

            // Associate each vector with its original chunk
            for (int i = 0; i < batch.size(); i++) {

                results.add(new EmbeddedChunk(
                        batch.get(i),
                        vectors.get(i)
                ));
            }

            System.out.println(
                    "Processed " + end + "/" + totalChunks
                            + " chunks"
            );
        }

        return results;
    }
}
