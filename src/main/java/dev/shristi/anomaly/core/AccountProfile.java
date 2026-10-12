package dev.shristi.anomaly.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Running statistics for one account, updated in O(1) per transaction with
 * Welford's algorithm, plus a short window of timestamps for velocity checks.
 * Not thread-safe; callers synchronize per account.
 */
class AccountProfile {

    private long count;
    private double mean;
    private double m2;
    private final Set<String> countries = new HashSet<>();
    private final Deque<Instant> recent = new ArrayDeque<>();

    long count() {
        return count;
    }

    double mean() {
        return mean;
    }

    double stdDev() {
        return count < 2 ? 0 : Math.sqrt(m2 / (count - 1));
    }

    boolean hasSeenCountry(String country) {
        return countries.contains(country);
    }

    int transactionsWithin(Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        return (int) recent.stream().filter(t -> !t.isBefore(cutoff)).count();
    }

    void record(double amount, String country, Instant at, Duration velocityWindow) {
        count++;
        double delta = amount - mean;
        mean += delta / count;
        m2 += delta * (amount - mean);
        if (country != null) {
            countries.add(country);
        }
        recent.addLast(at);
        Instant cutoff = at.minus(velocityWindow);
        while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
            recent.pollFirst();
        }
    }
}
