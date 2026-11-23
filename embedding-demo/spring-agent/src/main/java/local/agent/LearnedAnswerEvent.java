package local.agent;

import java.util.List;

/** Validated, web-sourced answer published to Kafka for the RAG app to learn. */
public record LearnedAnswerEvent(
        String eventId,
        String question,
        String answer,
        List<String> sourceUrls,
        String provider,
        String createdAt
) {}
