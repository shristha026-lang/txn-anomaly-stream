package dev.shristi.anomaly.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end: transactions in, alerts out, through an embedded Kafka broker. */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"transactions", "fraud-alerts"})
class KafkaFlowTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    EmbeddedKafkaBroker broker;

    @Test
    void publishesAnAlertForAnAnomalousTransaction() {
        for (int i = 0; i < 10; i++) {
            kafka.send("transactions", "ACC-1", """
                    {"id":"t%d","accountId":"ACC-1","amount":%d,"currency":"USD","merchantCategory":"grocery","country":"US","timestamp":"2026-01-01T%02d:00:00Z"}"""
                    .formatted(i, 20 + i, i));
        }
        kafka.send("transactions", "ACC-1", """
                {"id":"big","accountId":"ACC-1","amount":950,"currency":"USD","merchantCategory":"crypto","country":"RO","timestamp":"2026-01-01T12:00:00Z"}""");
        kafka.send("transactions", "ACC-1", "not json");

        Map<String, Object> props = KafkaTestUtils.consumerProps("alerts-test", "true", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "fraud-alerts");
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(20));
            assertThat(records.count()).isEqualTo(1);
            assertThat(records.iterator().next().value()).contains("\"transactionId\":\"big\"").contains("\"flagged\":true");
        }
    }
}
