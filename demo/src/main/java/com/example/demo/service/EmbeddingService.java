package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    private final String baseUrl;
    private final String model;

    public EmbeddingService(
            ObjectMapper objectMapper,
            @Value("${rag.ollama.base-url}") String baseUrl,
            @Value("${rag.ollama.embedding-model}") String model
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.model = model;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public double[] generateQueryEmbedding(String question)
            throws Exception {

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", List.of("search_query: " + question)
        );

        String jsonRequest =
                objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/embed"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Embedding API failed: " + response.body()
            );
        }

        JsonNode jsonResponse =
                objectMapper.readTree(response.body());

        JsonNode embeddingNode =
                jsonResponse.path("embeddings").get(0);

        if (embeddingNode == null || !embeddingNode.isArray()) {
            throw new IllegalStateException(
                    "Invalid embedding response from Ollama"
            );
        }

        List<Double> values = new ArrayList<>();

        embeddingNode.forEach(
                value -> values.add(value.asDouble())
        );

        double[] embedding = new double[values.size()];

        for (int i = 0; i < values.size(); i++) {
            embedding[i] = values.get(i);
        }

        if (embedding.length != 768) {
            throw new IllegalStateException(
                    "Expected 768 dimensions but received "
                            + embedding.length
            );
        }

        return embedding;
    }

    /**
     * Embed text for STORAGE using the Nomic document prefix. Must be used
     * when inserting learned answers so their vectors live in the same space
     * as the ingested book chunks (which also used "search_document: ").
     */
    public double[] generateDocumentEmbedding(String content)
            throws Exception {

        long startedAt = System.nanoTime();
        log.debug("Embedding document chunk chars={} model={}", content.length(), model);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", List.of("search_document: " + content)
        );

        String jsonRequest =
                objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/embed"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Embedding API failed: " + response.body()
            );
        }

        JsonNode jsonResponse =
                objectMapper.readTree(response.body());

        JsonNode embeddingNode =
                jsonResponse.path("embeddings").get(0);

        if (embeddingNode == null || !embeddingNode.isArray()) {
            throw new IllegalStateException(
                    "Invalid embedding response from Ollama"
            );
        }

        double[] embedding = new double[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            embedding[i] = embeddingNode.get(i).asDouble();
        }

        if (embedding.length != 768) {
            throw new IllegalStateException(
                    "Expected 768 dimensions but received " + embedding.length
            );
        }

        log.debug("Embedded document chunk dims={} durationMs={}",
                embedding.length, (System.nanoTime() - startedAt) / 1_000_000);
        return embedding;
    }
}
