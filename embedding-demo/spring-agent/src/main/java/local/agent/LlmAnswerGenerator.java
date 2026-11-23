package local.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Generates an answer from the local model's own knowledge (qwen3:8b) when the
 * book/vector store cannot answer. Uses a tool-less ChatClient so it only
 * generates text. Returns null when the model declines (NO_ANSWER), so the
 * pipeline never stores a non-answer.
 */
@Component
public class LlmAnswerGenerator {

    private static final Logger log = LoggerFactory.getLogger(LlmAnswerGenerator.class);
    private static final String DECLINE = "NO_ANSWER";

    private final ChatClient chatClient;

    public LlmAnswerGenerator(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /** Returns a grounded-in-model-knowledge answer, or null if the model declines. */
    public String generate(String question) {
        String prompt = """
                You are a senior software/system-design expert. Answer the QUESTION accurately
                and concisely from your own knowledge, focusing on the core technical reasons.
                Use a few short paragraphs or bullet points. Do not add a preamble.
                If you genuinely do not know the answer, reply with exactly: %s

                QUESTION:
                %s
                """.formatted(DECLINE, question);
        try {
            long startedAt = System.nanoTime();
            String answer = chatClient.prompt().user(prompt).call().content();
            long ms = (System.nanoTime() - startedAt) / 1_000_000;
            if (answer == null || answer.isBlank() || answer.trim().contains(DECLINE)) {
                log.info("LLM declined/empty answer durationMs={}", ms);
                return null;
            }
            String trimmed = answer.trim();
            log.info("LLM generated answer durationMs={} length={}", ms, trimmed.length());
            return trimmed;
        } catch (Exception e) {
            log.warn("LLM answer generation failed: {}", e.getMessage());
            return null;
        }
    }
}
