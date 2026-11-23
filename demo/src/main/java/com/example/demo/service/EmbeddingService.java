package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}
