package dev.shristi.anomaly.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic synthetic transaction generator. Produces normal spending for
 * a set of accounts and injects known anomalies so detection can be measured.
 */
public final class SyntheticTransactions {

    private static final String[] CATEGORIES = {"grocery", "fuel", "restaurant", "pharmacy", "online_retail"};

    private SyntheticTransactions() {
    }

    public record Labeled(Transaction transaction, boolean injectedAnomaly) {
    }

    public static List<Labeled> generate(int accounts, int perAccount, long seed) {
        Random random = new Random(seed);
        List<Labeled> out = new ArrayList<>();
        Instant start = Instant.parse("2026-01-05T09:00:00Z");
        int n = 0;
        for (int a = 0; a < accounts; a++) {
            String account = "ACC-" + (1000 + a);
            double typical = 20 + random.nextInt(80);
            Instant t = start.plusSeconds(a * 60L);
            for (int i = 0; i < perAccount; i++) {
                t = t.plusSeconds(3600 + random.nextInt(7200));
                double amount = Math.max(1, typical + random.nextGaussian() * typical * 0.25);
                out.add(new Labeled(tx("TX-" + (n++), account, amount, CATEGORIES[random.nextInt(CATEGORIES.length)], "US", t), false));
            }
            // Injected anomaly 1: a large amount abroad in a high-risk category.
            t = t.plusSeconds(1800);
            out.add(new Labeled(tx("TX-" + (n++), account, typical * 12, "crypto", "RO", t), true));
        }
        return out;
    }

    private static Transaction tx(String id, String account, double amount, String category, String country, Instant at) {
        return new Transaction(id, account, BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP), "USD", category, country, at);
    }
}
