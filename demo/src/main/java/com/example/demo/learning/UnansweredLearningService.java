package com.example.demo.learning;

import com.example.demo.model.LearnedAnswerEvent;
import com.example.demo.model.UnansweredQuery;
import com.example.demo.service.OllamaChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2: on request, generate an answer for a recorded unanswered query using
 * the local model (qwen3:8b), publish it to the learned-answers topic, and mark
 * the row GENERATED. The existing consumer then embeds and stores it, flipping
 * the row to STORED.
 */
@Service
public class UnansweredLearningService {

    private static final Logger log = LoggerFactory.getLogger(UnansweredLearningService.class);

    private final UnansweredQueryRepository repository;
    private final OllamaChatService chatService;
    private final LearnedAnswerPublisher publisher;

    public UnansweredLearningService(
            UnansweredQueryRepository repository,
            OllamaChatService chatService,
            LearnedAnswerPublisher publisher
    ) {
        this.repository = repository;
        this.chatService = chatService;
        this.publisher = publisher;
    }

    public Optional<GenerateResult> generate(long id) throws Exception {
        Optional<UnansweredQuery> maybe = repository.findById(id);
        if (maybe.isEmpty()) {
            return Optional.empty();
        }
        UnansweredQuery row = maybe.get();
        log.info("Generating answer for unanswered query id={} question='{}'", id, row.question());

        String answer = chatService.generateAnswer(buildPrompt(row.question()));

        String eventId = UUID.randomUUID().toString();
        publisher.publish(new LearnedAnswerEvent(
                eventId, row.question(), answer, List.of(), "llm", Instant.now().toString(), id));
        repository.markGenerated(id, answer, eventId);

        log.info("Generated + published answer for id={} eventId={} (embedding/storage async)", id, eventId);
        return Optional.of(new GenerateResult(id, "GENERATED", eventId, answer));
    }

    /**
     * Process up to {@code limit} PENDING questions: generate + publish each.
     * One failure is logged and skipped so the batch continues.
     */
    public List<GenerateResult> processPending(int limit) {
        List<UnansweredQuery> pending = repository.list(UnansweredQueryRepository.PENDING, limit);
        List<GenerateResult> results = new ArrayList<>();
        for (UnansweredQuery q : pending) {
            try {
                generate(q.id()).ifPresent(results::add);
            } catch (Exception e) {
                log.error("Failed to process unanswered query id={}: {}", q.id(), e.getMessage(), e);
            }
        }
        log.info("Processed {} of {} pending unanswered queries", results.size(), pending.size());
        return results;
    }

    private String buildPrompt(String question) {
        return """
                You are a senior software/system-design expert. Answer the QUESTION accurately
                and concisely from your own knowledge, focusing on the core technical reasons.
                Use a few short paragraphs or bullet points. Do not add a preamble.

                QUESTION:
                %s
                """.formatted(question);
    }

    public record GenerateResult(long id, String status, String eventId, String answer) {}
}
