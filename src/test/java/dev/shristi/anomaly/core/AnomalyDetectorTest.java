package dev.shristi.anomaly.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnomalyDetectorTest {

    private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

    private AnomalyDetector detector() {
        return new AnomalyDetector(AnomalyDetector.Settings.defaults(), Explainer.template());
    }

    private Transaction tx(String id, String account, String amount, String category, String country, long secondsAfter) {
        return new Transaction(id, account, new BigDecimal(amount), "USD", category, country, t0.plusSeconds(secondsAfter));
    }

    @Test
    void flagsEveryInjectedAnomalyWithFewFalsePositives() {
        AnomalyDetector detector = detector();
        List<SyntheticTransactions.Labeled> data = SyntheticTransactions.generate(50, 30, 42L);
        long truePositives = 0;
        long falsePositives = 0;
        for (SyntheticTransactions.Labeled l : data) {
            boolean flagged = detector.assess(l.transaction()).flagged();
            if (flagged && l.injectedAnomaly()) {
                truePositives++;
            } else if (flagged) {
                falsePositives++;
            }
        }
        assertThat(truePositives).isEqualTo(50);
        assertThat(falsePositives).isLessThanOrEqualTo(data.size() / 100);
    }

    @Test
    void explainsWhyATransactionWasFlagged() {
        AnomalyDetector detector = detector();
        for (int i = 0; i < 10; i++) {
            detector.assess(tx("n" + i, "A", String.valueOf(20 + i), "grocery", "US", i * 3600L));
        }
        Assessment a = detector.assess(tx("big", "A", "900", "crypto", "RO", 11 * 3600L));
        assertThat(a.flagged()).isTrue();
        assertThat(a.reasons()).anyMatch(r -> r.contains("standard deviations"))
                .anyMatch(r -> r.contains("country RO"))
                .anyMatch(r -> r.contains("crypto"));
        assertThat(a.explanation()).startsWith("Review transaction big");
    }

    @Test
    void detectsVelocityBursts() {
        AnomalyDetector detector = detector();
        Assessment last = null;
        for (int i = 0; i < 6; i++) {
            last = detector.assess(tx("v" + i, "B", "10", "grocery", "US", i * 60L));
        }
        assertThat(last.reasons()).anyMatch(r -> r.contains("6 transactions within 10 minutes"));
    }

    @Test
    void doesNotScoreAmountOrCountryWithoutHistory() {
        Assessment first = detector().assess(tx("x", "C", "5000", "crypto", "RO", 0));
        assertThat(first.score()).isEqualTo(15);
        assertThat(first.flagged()).isFalse();
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> tx("z", "D", "0", "grocery", "US", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
