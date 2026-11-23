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
import java.util.List;
import java.util.Map;

@Service
public class OllamaChatService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final String baseUrl;
    private final String model;

    public OllamaChatService(
            ObjectMapper objectMapper,
            @Value("${rag.ollama.base-url}") String baseUrl,
            @Value("${rag.ollama.chat-model}") String model
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.model = model;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String generateAnswer(String prompt) throws Exception {

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "stream", false,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                )
        );

        String jsonRequest =
                objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Ollama chat API failed: " + response.body()
            );
        }

        JsonNode jsonResponse =
                objectMapper.readTree(response.body());

        String answer = jsonResponse
                .path("message")
                .path("content")
                .asText();

        if (answer.isBlank()) {
            throw new IllegalStateException(
                    "Ollama returned an empty answer"
            );
        }

        return answer;
    }
}
