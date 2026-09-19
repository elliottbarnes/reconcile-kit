package ca.elliottbarnes.reconcile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import ca.elliottbarnes.reconcile.LedgerReader.Transaction;

final class Reconciler {
    enum Status {
        MATCHED, AMOUNT_MISMATCH, CURRENCY_MISMATCH, MISSING_LEDGER, MISSING_PROCESSOR,
        DUPLICATE_LEDGER, DUPLICATE_PROCESSOR, DUPLICATE_BOTH
    }

    record Entry(String id, Status status, List<Transaction> ledger, List<Transaction> processor) {}
    record Total(String currency, BigDecimal ledger, BigDecimal processor) {
        BigDecimal delta() { return ledger.subtract(processor); }
    }
    record Report(int ledgerRows, int processorRows, List<Entry> entries, List<Total> totals) {
        long mismatches() { return entries.stream().filter(entry -> entry.status() != Status.MATCHED).count(); }
        Map<Status, Long> counts() {
            var counts = new EnumMap<Status, Long>(Status.class);
            for (var status : Status.values()) counts.put(status, 0L);
            for (var entry : entries) counts.merge(entry.status(), 1L, Long::sum);
            return counts;
        }
    }

    private Reconciler() {}

    static Report reconcile(List<Transaction> ledger, List<Transaction> processor) {
        var ledgerIndex = index(ledger);
        var processorIndex = index(processor);
        var ids = new TreeSet<>(ledgerIndex.keySet());
        ids.addAll(processorIndex.keySet());
        var entries = new ArrayList<Entry>();
        for (var id : ids) {
            var left = ledgerIndex.getOrDefault(id, List.of());
            var right = processorIndex.getOrDefault(id, List.of());
            entries.add(new Entry(id, classify(left, right), left, right));
        }
        var leftTotals = totals(ledger);
        var rightTotals = totals(processor);
        var currencies = new TreeSet<>(leftTotals.keySet());
        currencies.addAll(rightTotals.keySet());
        var totals = new ArrayList<Total>();
        for (var code : currencies) {
            var zero = BigDecimal.ZERO.setScale(java.util.Currency.getInstance(code).getDefaultFractionDigits());
            totals.add(new Total(code, leftTotals.getOrDefault(code, zero), rightTotals.getOrDefault(code, zero)));
        }
        return new Report(ledger.size(), processor.size(), List.copyOf(entries), List.copyOf(totals));
    }

    private static Map<String, List<Transaction>> index(List<Transaction> transactions) {
        var index = new TreeMap<String, List<Transaction>>();
        for (var transaction : transactions) {
            index.computeIfAbsent(transaction.id(), ignored -> new ArrayList<>()).add(transaction);
        }
        index.replaceAll((id, values) -> values.stream().sorted(Comparator.comparingInt(Transaction::line)).toList());
        return index;
    }

    private static Map<String, BigDecimal> totals(List<Transaction> transactions) {
        var result = new TreeMap<String, BigDecimal>();
        for (var transaction : transactions) result.merge(transaction.currency(), transaction.amount(), BigDecimal::add);
        return result;
    }

    private static Status classify(List<Transaction> left, List<Transaction> right) {
        if (left.size() > 1 && right.size() > 1) return Status.DUPLICATE_BOTH;
        if (left.size() > 1) return Status.DUPLICATE_LEDGER;
        if (right.size() > 1) return Status.DUPLICATE_PROCESSOR;
        if (left.isEmpty()) return Status.MISSING_LEDGER;
        if (right.isEmpty()) return Status.MISSING_PROCESSOR;
        if (!left.getFirst().currency().equals(right.getFirst().currency())) return Status.CURRENCY_MISMATCH;
        return left.getFirst().amount().compareTo(right.getFirst().amount()) == 0 ? Status.MATCHED : Status.AMOUNT_MISMATCH;
    }
}
