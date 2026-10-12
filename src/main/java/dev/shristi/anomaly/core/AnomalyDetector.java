package dev.shristi.anomaly.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scores each transaction against the account's own history:
 * <ul>
 *   <li>amount z-score against the running mean and standard deviation,</li>
 *   <li>a country the account has not used before,</li>
 *   <li>transaction velocity inside a short window,</li>
 *   <li>high-risk merchant categories.</li>
 * </ul>
 * Each signal adds points; a score at or above the threshold is flagged.
 * Rules are explainable on purpose: analysts and auditors need the "why".
 */
public class AnomalyDetector {

    public record Settings(int minHistory, double zScoreLimit, int velocityLimit, Duration velocityWindow,
                           int flagThreshold, List<String> highRiskCategories) {

        public static Settings defaults() {
            return new Settings(5, 3.0, 5, Duration.ofMinutes(10), 50, List.of("crypto", "gift_cards", "wire_transfer"));
        }
    }

    private final Settings settings;
    private final Explainer explainer;
    private final Map<String, AccountProfile> profiles = new ConcurrentHashMap<>();

    public AnomalyDetector(Settings settings, Explainer explainer) {
        this.settings = settings;
        this.explainer = explainer;
    }

    public Assessment assess(Transaction tx) {
        AccountProfile profile = profiles.computeIfAbsent(tx.accountId(), k -> new AccountProfile());
        synchronized (profile) {
            List<String> reasons = new ArrayList<>();
            int score = 0;
            double amount = tx.amount().doubleValue();

            if (profile.count() >= settings.minHistory()) {
                double sd = profile.stdDev();
                double z = sd == 0 ? (amount > profile.mean() ? Double.POSITIVE_INFINITY : 0) : (amount - profile.mean()) / sd;
                if (z >= settings.zScoreLimit()) {
                    score += 45;
                    reasons.add(String.format("Amount %.2f is %s standard deviations above this account's average of %.2f",
                            amount, Double.isInfinite(z) ? "many" : String.format("%.1f", z), profile.mean()));
                }
                if (tx.country() != null && !profile.hasSeenCountry(tx.country())) {
                    score += 25;
                    reasons.add("First transaction from country " + tx.country());
                }
            }
            int burst = profile.transactionsWithin(tx.timestamp(), settings.velocityWindow()) + 1;
            if (burst > settings.velocityLimit()) {
                score += 30;
                reasons.add(burst + " transactions within " + settings.velocityWindow().toMinutes() + " minutes");
            }
            if (tx.merchantCategory() != null && settings.highRiskCategories().contains(tx.merchantCategory())) {
                score += 15;
                reasons.add("High-risk merchant category: " + tx.merchantCategory());
            }

            score = Math.min(score, 100);
            boolean flagged = score >= settings.flagThreshold();
            profile.record(amount, tx.country(), tx.timestamp(), settings.velocityWindow());
            String explanation = flagged ? explainer.explain(tx, reasons) : "";
            return new Assessment(tx.id(), tx.accountId(), score, flagged, List.copyOf(reasons), explanation);
        }
    }
}
