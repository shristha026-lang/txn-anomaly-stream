package dev.shristi.anomaly.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.shristi.anomaly.core.AnomalyDetector;
import dev.shristi.anomaly.core.Assessment;
import dev.shristi.anomaly.core.Transaction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumes transactions, scores them and publishes flagged ones to the alerts
 * topic. Malformed messages are logged and counted instead of blocking the
 * partition; a dead-letter topic is the next step for production.
 */
@Component
public class TransactionListener {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionListener.class);

    private final AnomalyDetector detector;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String alertsTopic;
    private final Counter scored;
    private final Counter flagged;
    private final Counter rejected;

    public TransactionListener(AnomalyDetector detector, KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                               @Value("${app.topics.alerts}") String alertsTopic, MeterRegistry metrics) {
        this.detector = detector;
        this.kafka = kafka;
        this.mapper = mapper;
        this.alertsTopic = alertsTopic;
        this.scored = metrics.counter("anomaly.transactions.scored");
        this.flagged = metrics.counter("anomaly.transactions.flagged");
        this.rejected = metrics.counter("anomaly.transactions.rejected");
    }

    @KafkaListener(topics = "${app.topics.transactions}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String payload) {
        Transaction tx;
        try {
            tx = mapper.readValue(payload, Transaction.class);
        } catch (Exception e) {
            rejected.increment();
            LOG.warn("Rejected malformed transaction message: {}", e.getMessage());
            return;
        }
        Assessment assessment = detector.assess(tx);
        scored.increment();
        if (assessment.flagged()) {
            flagged.increment();
            try {
                kafka.send(alertsTopic, tx.accountId(), mapper.writeValueAsString(assessment));
            } catch (Exception e) {
                LOG.error("Failed to publish alert for {}", tx.id(), e);
            }
        }
    }
}
