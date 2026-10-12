package dev.shristi.anomaly.core;

import java.util.List;

/**
 * The result of scoring one transaction. {@code score} is 0-100; reasons are
 * human-readable so an analyst can see why something was flagged.
 */
public record Assessment(String transactionId, String accountId, int score, boolean flagged,
                         List<String> reasons, String explanation) {
}
