package com.example.demo.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class OllamaChatService {

    private static final String API_URL =
            "http://localhost:11434/api/chat";

    private static final String MODEL = "qwen3:8b";

    private final HttpClient httpClient =
            HttpClient.newHttpClient();

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    public String generateAnswer(String prompt) throws Exception {

        Map<String, Object> requestBody = Map.of(
                "model", MODEL,
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
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
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
                objectMapper.readTree(response.body());

        return jsonResponse
                .path("message")
                .path("content")
                .asText();
    }
}
