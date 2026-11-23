package com.example.demo.archived;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class EmbeddingDemo {

    private static final String OLLAMA_URL =
            "http://localhost:11434/api/embeddings";

    private static final String MODEL = "nomic-embed-text";

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final HttpClient httpClient =
            HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {

        String document1 =
                "search_document: Spring Boot is a Java framework for building microservices.";

        String document2 =
                "search_document: Java supports object oriented programming.";

        String document3 =
                "search_document: Pizza is a popular Italian food.";


        String query =
                "search_query: How can I build microservices using Java?";


        // Generate embeddings
        double[] document1Vector = generateEmbedding(document1);
        double[] document2Vector = generateEmbedding(document2);
        double[] document3Vector = generateEmbedding(document3);

        double[] queryVector = generateEmbedding(query);

        double score1 = cosineSimilarity(queryVector, document1Vector);
        double score2 = cosineSimilarity(queryVector, document2Vector);
        double score3 = cosineSimilarity(queryVector, document3Vector);

        System.out.println("Document 1: " + score1);
        System.out.println("Document 2: " + score2);
        System.out.println("Document 3: " + score3);
    }

    private static double[] generateEmbedding(String text)
            throws Exception {

        ObjectNode requestBody = mapper.createObjectNode();

        requestBody.put("model", MODEL);
        requestBody.put("prompt", text);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Ollama API failed: " + response.body()
            );
        }

        JsonNode jsonResponse =
                mapper.readTree(response.body());

        JsonNode embeddingNode =
                jsonResponse.get("embedding");

        List<Double> values = new ArrayList<>();

        for (JsonNode node : embeddingNode) {
            values.add(node.asDouble());
        }

        double[] embedding = new double[values.size()];

        for (int i = 0; i < values.size(); i++) {
            embedding[i] = values.get(i);
        }

        return embedding;
    }

    private static double cosineSimilarity(
            double[] vectorA,
            double[] vectorB) {

        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException(
                    "Vector dimensions must match"
            );
        }

        double dotProduct = 0.0;

        double magnitudeA = 0.0;
        double magnitudeB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {

            dotProduct += vectorA[i] * vectorB[i];

            magnitudeA += vectorA[i] * vectorA[i];

            magnitudeB += vectorB[i] * vectorB[i];
        }

        return dotProduct /
                (Math.sqrt(magnitudeA) * Math.sqrt(magnitudeB));
    }
}