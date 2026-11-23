package local.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes a validated answer to Kafka as a JSON string (a stable contract the
 * consumer parses independently). Publish failures are logged, never thrown, so
 * a Kafka outage cannot break the chat response.
 */
@Component
public class LearnedAnswerPublisher {

    private static final Logger log = LoggerFactory.getLogger(LearnedAnswerPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public LearnedAnswerPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${learning.topic:learned-answers}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(LearnedAnswerEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.eventId(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish learned answer eventId={}: {}",
                                    event.eventId(), ex.getMessage());
                        } else {
                            log.info("Published learned answer eventId={} topic={}",
                                    event.eventId(), topic);
                        }
                    });
        } catch (Exception e) {
            log.warn("Could not serialize/publish learned answer eventId={}: {}",
                    event.eventId(), e.getMessage());
        }
    }
}
