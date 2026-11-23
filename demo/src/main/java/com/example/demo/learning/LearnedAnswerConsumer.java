package com.example.demo.learning;

import com.example.demo.model.LearnedAnswerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes validated web answers and hands them to ingestion. The payload is a
 * JSON string (a stable cross-service contract, independent of either app's
 * package names). A parse/ingest failure is logged and swallowed so one bad
 * message does not stall the partition; tune with a Kafka error handler if you
 * later want ret/dead-letter semantics.
 */
@Component
public class LearnedAnswerConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(LearnedAnswerConsumer.class);

    private final ObjectMapper objectMapper;
    private final LearnedAnswerIngestionService ingestionService;
    private final UnansweredQueryRepository unansweredRepository;

    public LearnedAnswerConsumer(
            ObjectMapper objectMapper,
            LearnedAnswerIngestionService ingestionService,
            UnansweredQueryRepository unansweredRepository
    ) {
        this.objectMapper = objectMapper;
        this.ingestionService = ingestionService;
        this.unansweredRepository = unansweredRepository;
    }

    @KafkaListener(
            topics = "${learning.topic:learned-answers}",
            groupId = "${spring.kafka.consumer.group-id:rag-learned-ingest}")
    public void onMessage(String payload) {
        try {
            LearnedAnswerEvent event =
                    objectMapper.readValue(payload, LearnedAnswerEvent.class);
            log.info("Received learned answer eventId={} question='{}'",
                    event.eventId(), event.question());
            ingestionService.ingest(event);
            if (event.sourceId() != null) {
                unansweredRepository.markStored(event.sourceId());
                log.info("Marked unanswered query id={} as STORED", event.sourceId());
            }
        } catch (Exception e) {
            log.error("Failed to ingest learned answer payload={}", payload, e);
        }
    }
}
