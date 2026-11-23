package local.agent;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class SystemDesignRagTool {
    private static final Logger log = LoggerFactory.getLogger(SystemDesignRagTool.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private final WebClient webClient;
    private final String ragApiUrl;

    public SystemDesignRagTool(WebClient.Builder webClientBuilder,
                               @Value("${agent.rag-api-url}") String ragApiUrl) {
        this.webClient = webClientBuilder.build();
        this.ragApiUrl = ragApiUrl;
    }

    @Tool(description = "Search the existing RAG application for a system design or architecture question. "
            + "Call this for system design questions, including questions that apply design concepts to a domain.")
    public String systemDesignRag(
            @ToolParam(description = "The user's complete system design question") String question) {
        long startedAt = System.nanoTime();
        log.info("Tool invoked tool=systemDesignRag question={}", question);
        try {
            String result = webClient.post()
                    .uri(ragApiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new RagQuestion(question))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(REQUEST_TIMEOUT);
            String output = result == null || result.isBlank()
                    ? "The RAG service returned an empty response." : result;
            log.info("Tool completed tool=systemDesignRag durationMs={} output={}", elapsedMillis(startedAt), output);
            return output;
        } catch (WebClientResponseException e) {
            String output = "RAG service returned HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString();
            log.warn("Tool failed tool=systemDesignRag durationMs={} output={}", elapsedMillis(startedAt), output, e);
            return output;
        } catch (RuntimeException e) {
            String output = "Could not reach the RAG service at " + ragApiUrl + ". Check that it is running. Details: "
                    + e.getMessage();
            log.warn("Tool failed tool=systemDesignRag durationMs={} output={}", elapsedMillis(startedAt), output, e);
            return output;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record RagQuestion(String question) {}
}
