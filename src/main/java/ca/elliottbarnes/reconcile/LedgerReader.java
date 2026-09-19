package ca.elliottbarnes.reconcile;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

final class LedgerReader {
    private static final List<String> HEADER = List.of("transaction_id", "currency", "amount", "description");

    record Transaction(String id, String currency, BigDecimal amount, String description, int line) {}

    private LedgerReader() {}

    static List<Transaction> read(Path path) throws IOException {
        try (var input = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(input);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(path.getFileName() + ": " + exception.getMessage(), exception);
        }
    }

    static List<Transaction> read(Reader input) throws IOException {
        var rows = CsvReader.read(input);
        if (rows.isEmpty() || !rows.getFirst().fields().equals(HEADER)) {
            throw new IllegalArgumentException("expected header: transaction_id,currency,amount,description");
        }
        var transactions = new ArrayList<Transaction>();
        for (var row : rows.subList(1, rows.size())) {
            var fields = row.fields();
            if (fields.size() != 4) throw problem(row, "expected exactly four columns");
            String id = fields.get(0);
            String currencyCode = fields.get(1);
            String rawAmount = fields.get(2);
            if (!id.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")) {
                throw problem(row, "transaction_id must be 1–128 ASCII letters, digits or ._:/- and start with a letter or digit");
            }
            if (!currencyCode.matches("[A-Z]{3}")) throw problem(row, "currency must be an uppercase ISO 4217 code");
            Currency currency;
            try {
                currency = Currency.getInstance(currencyCode);
            } catch (IllegalArgumentException exception) {
                throw problem(row, "unknown ISO 4217 currency: " + currencyCode);
            }
            int scale = currency.getDefaultFractionDigits();
            if (scale < 0 || scale > 4) throw problem(row, "currency does not have a supported minor-unit scale: " + currencyCode);
            if (!rawAmount.matches("-?[0-9]{1,30}(\\.[0-9]{1,6})?")) {
                throw problem(row, "amount must be a plain decimal (up to 30 integer and 6 fractional digits)");
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(rawAmount).setScale(scale, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException exception) {
                throw problem(row, "amount has nonzero digits below the " + currencyCode + " minor unit");
            }
            transactions.add(new Transaction(id, currencyCode, amount, fields.get(3), row.line()));
        }
        return List.copyOf(transactions);
    }

    private static IllegalArgumentException problem(CsvReader.Row row, String message) {
        return new IllegalArgumentException("line " + row.line() + ": " + message);
    }
}
