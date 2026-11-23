package local.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes an unanswered query to Kafka as a JSON string. Failures are logged,
 * never thrown, so a Kafka outage cannot break the chat response.
 */
@Component
public class UnansweredQueryPublisher {

    private static final Logger log = LoggerFactory.getLogger(UnansweredQueryPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public UnansweredQueryPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${learning.unanswered-topic:unanswered-queries}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(UnansweredQueryEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.eventId(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish unanswered query eventId={}: {}",
                                    event.eventId(), ex.getMessage());
                        } else {
                            log.info("Published unanswered query eventId={} topic={}",
                                    event.eventId(), topic);
                        }
                    });
        } catch (Exception e) {
            log.warn("Could not serialize/publish unanswered query eventId={}: {}",
                    event.eventId(), e.getMessage());
        }
    }
}
