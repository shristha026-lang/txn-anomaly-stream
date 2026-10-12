package dev.shristi.anomaly.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.shristi.anomaly.core.AnomalyDetector;
import dev.shristi.anomaly.core.Assessment;
import dev.shristi.anomaly.core.SyntheticTransactions;
import dev.shristi.anomaly.core.Transaction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Validated
public class TransactionController {

    private final AnomalyDetector detector;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String transactionsTopic;

    public TransactionController(AnomalyDetector detector, KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                                 @Value("${app.topics.transactions}") String transactionsTopic) {
        this.detector = detector;
        this.kafka = kafka;
        this.mapper = mapper;
        this.transactionsTopic = transactionsTopic;
    }

    /** Synchronous scoring, useful for testing a single transaction without Kafka. */
    @PostMapping("/transactions/score")
    public Assessment score(@RequestBody Transaction tx) {
        return detector.assess(tx);
    }

    /** Publishes synthetic transactions (with injected anomalies) to Kafka for a demo run. */
    @PostMapping("/demo/publish")
    public Map<String, Object> publishDemo(@RequestParam(defaultValue = "20") @Min(1) @Max(500) int accounts,
                                           @RequestParam(defaultValue = "30") @Min(6) @Max(200) int perAccount)
            throws JsonProcessingException {
        List<SyntheticTransactions.Labeled> data = SyntheticTransactions.generate(accounts, perAccount, 42L);
        for (SyntheticTransactions.Labeled l : data) {
            kafka.send(transactionsTopic, l.transaction().accountId(), mapper.writeValueAsString(l.transaction()));
        }
        long injected = data.stream().filter(SyntheticTransactions.Labeled::injectedAnomaly).count();
        return Map.of("published", data.size(), "injectedAnomalies", injected);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Invalid transaction");
        pd.setDetail(e.getMessage());
        return pd;
    }
}
