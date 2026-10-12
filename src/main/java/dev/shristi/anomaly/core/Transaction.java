package dev.shristi.anomaly.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** A card transaction. All data in this project is synthetic. */
public record Transaction(String id, String accountId, BigDecimal amount, String currency,
                          String merchantCategory, String country, Instant timestamp) {

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(timestamp, "timestamp");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
