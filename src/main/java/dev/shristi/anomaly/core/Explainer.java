package dev.shristi.anomaly.core;

import java.util.List;

/**
 * Writes the analyst-facing summary for a flagged transaction. The default is
 * a deterministic template; an LLM-backed implementation can be plugged in to
 * produce richer narratives from the same structured reasons (the model only
 * sees reasons and non-identifying fields, never raw card data).
 */
public interface Explainer {

    String explain(Transaction tx, List<String> reasons);

    static Explainer template() {
        return (tx, reasons) -> "Review transaction " + tx.id() + " on account " + tx.accountId()
                + " (" + tx.amount() + " " + tx.currency() + ", " + tx.merchantCategory() + "): "
                + String.join("; ", reasons) + ".";
    }
}
