package local.agent;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Auto-creates the pipeline topics when the broker is reachable. */
@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic learnedAnswersTopic(@Value("${learning.topic:learned-answers}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic unansweredQueriesTopic(@Value("${learning.unanswered-topic:unanswered-queries}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }
}
