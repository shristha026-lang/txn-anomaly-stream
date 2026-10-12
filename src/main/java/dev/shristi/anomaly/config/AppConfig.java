package dev.shristi.anomaly.config;

import dev.shristi.anomaly.core.AnomalyDetector;
import dev.shristi.anomaly.core.Explainer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class AppConfig {

    @Bean
    AnomalyDetector anomalyDetector() {
        return new AnomalyDetector(AnomalyDetector.Settings.defaults(), Explainer.template());
    }

    @Bean
    NewTopic transactionsTopic(@Value("${app.topics.transactions}") String name) {
        // Keyed by accountId, so one account's transactions stay ordered within a partition.
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic alertsTopic(@Value("${app.topics.alerts}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
