package com.example.demo.learning;

import com.example.demo.model.UnansweredQueryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Phase 1 consumer: records unanswered questions as PENDING rows. */
@Component
public class UnansweredQueryConsumer {

    private static final Logger log = LoggerFactory.getLogger(UnansweredQueryConsumer.class);

    private final ObjectMapper objectMapper;
    private final UnansweredQueryRepository repository;

    public UnansweredQueryConsumer(ObjectMapper objectMapper, UnansweredQueryRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @KafkaListener(
            topics = "${learning.unanswered-topic:unanswered-queries}",
            groupId = "${spring.kafka.consumer.group-id:rag-learned-ingest}")
    public void onMessage(String payload) {
        try {
            UnansweredQueryEvent event = objectMapper.readValue(payload, UnansweredQueryEvent.class);
            int inserted = repository.insertIfAbsent(event.question(), event.eventId());
            log.info("Unanswered query eventId={} recorded={} question='{}'",
                    event.eventId(), inserted > 0, event.question());
        } catch (Exception e) {
            log.error("Failed to record unanswered query payload={}", payload, e);
        }
    }
}
