package ca.elliottbarnes.reconcile;

import java.util.List;
import java.util.stream.Collectors;

import ca.elliottbarnes.reconcile.LedgerReader.Transaction;
import ca.elliottbarnes.reconcile.Reconciler.Report;
import ca.elliottbarnes.reconcile.Reconciler.Status;

final class ReportWriter {
    private ReportWriter() {}

    static String text(Report report) {
        var output = new StringBuilder("RECONCILE KIT\n");
        output.append("Result: ").append(report.mismatches() == 0 ? "MATCH" : "MISMATCH").append('\n');
        output.append("Rows: ledger=").append(report.ledgerRows()).append(" processor=").append(report.processorRows()).append('\n');
        output.append("Keys: ").append(report.entries().size()).append(" | matched=").append(report.counts().get(Status.MATCHED))
            .append(" | exceptions=").append(report.mismatches()).append("\n\n");
        output.append("TOTALS (all input rows, including duplicates; delta = ledger - processor)\n");
        for (var total : report.totals()) {
            output.append(total.currency()).append("  ledger=").append(total.ledger().toPlainString())
                .append("  processor=").append(total.processor().toPlainString())
                .append("  delta=").append(total.delta().toPlainString()).append('\n');
        }
        output.append("\nEXCEPTIONS\n");
        if (report.mismatches() == 0) output.append("None. All transaction keys match.\n");
        for (var entry : report.entries()) {
            if (entry.status() == Status.MATCHED) continue;
            output.append(entry.id()).append("  ").append(entry.status()).append('\n');
            output.append("  ledger: ").append(evidence(entry.ledger())).append('\n');
            output.append("  processor: ").append(evidence(entry.processor())).append('\n');
        }
        return output.toString();
    }

    private static String evidence(List<Transaction> rows) {
        if (rows.isEmpty()) return "(absent)";
        return rows.stream().map(row -> row.currency() + " " + row.amount().toPlainString() + " [line " + row.line() + "]")
            .collect(Collectors.joining("; "));
    }

    static String json(Report report) {
        var output = new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"status\": ");
        output.append(quote(report.mismatches() == 0 ? "MATCH" : "MISMATCH"));
        output.append(",\n  \"ledgerRows\": ").append(report.ledgerRows());
        output.append(",\n  \"processorRows\": ").append(report.processorRows());
        output.append(",\n  \"exceptionKeys\": ").append(report.mismatches());
        output.append(",\n  \"counts\": {");
        boolean comma = false;
        for (var count : report.counts().entrySet()) {
            if (comma) output.append(',');
            output.append("\n    ").append(quote(count.getKey().name())).append(": ").append(count.getValue());
            comma = true;
        }
        output.append("\n  },\n  \"totals\": [");
        comma = false;
        for (var total : report.totals()) {
            if (comma) output.append(',');
            output.append("\n    {\"currency\": ").append(quote(total.currency()))
                .append(", \"ledger\": ").append(quote(total.ledger().toPlainString()))
                .append(", \"processor\": ").append(quote(total.processor().toPlainString()))
                .append(", \"delta\": ").append(quote(total.delta().toPlainString())).append('}');
            comma = true;
        }
        output.append("\n  ],\n  \"entries\": [");
        comma = false;
        for (var entry : report.entries()) {
            if (comma) output.append(',');
            output.append("\n    {\"transactionId\": ").append(quote(entry.id()))
                .append(", \"status\": ").append(quote(entry.status().name()))
                .append(", \"ledger\": ").append(rowsJson(entry.ledger()))
                .append(", \"processor\": ").append(rowsJson(entry.processor())).append('}');
            comma = true;
        }
        output.append("\n  ]\n}\n");
        return output.toString();
    }

    private static String rowsJson(List<Transaction> transactions) {
        return transactions.stream().map(row -> "{\"line\": " + row.line() + ", \"currency\": " + quote(row.currency())
                + ", \"amount\": " + quote(row.amount().toPlainString()) + ", \"description\": " + quote(row.description()) + "}")
            .collect(Collectors.joining(", ", "[", "]"));
    }

    static String tsv(Report report) {
        var output = new StringBuilder("transaction_id\tstatus\tledger_currency\tledger_amount\tprocessor_currency\tprocessor_amount\n");
        for (var entry : report.entries()) {
            output.append(entry.id()).append('\t').append(entry.status()).append('\t');
            output.append(currencies(entry.ledger())).append('\t').append(amounts(entry.ledger())).append('\t');
            output.append(currencies(entry.processor())).append('\t').append(amounts(entry.processor())).append('\n');
        }
        return output.toString();
    }

    private static String currencies(List<Transaction> rows) {
        return rows.stream().map(Transaction::currency).collect(Collectors.joining(";"));
    }

    private static String amounts(List<Transaction> rows) {
        return rows.stream().map(row -> row.amount().toPlainString()).collect(Collectors.joining(";"));
    }

    static String quote(String value) {
        var output = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) output.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
                    else output.append(character);
                }
            }
        }
        return output.append('"').toString();
    }
}
