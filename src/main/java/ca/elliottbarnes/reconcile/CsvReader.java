package ca.elliottbarnes.reconcile;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Strict, bounded RFC 4180-style CSV: quotes, escaped quotes, CRLF and multiline fields. */
final class CsvReader {
    static final int MAX_RECORDS = 100_001; // Header plus 100,000 transactions.
    static final int MAX_FIELD_LENGTH = 16_384;

    record Row(int line, List<String> fields) {}

    private CsvReader() {}

    static List<Row> read(Reader input) throws IOException {
        var reader = new PushbackReader(input, 1);
        var rows = new ArrayList<Row>();
        var fields = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        boolean started = false;
        boolean firstCharacter = true;
        int line = 1;
        int recordLine = 1;
        long characters = 0;
        int code;
        while ((code = reader.read()) != -1) {
            if (++characters > 64L * 1024 * 1024) throw problem(line, "input exceeds 64 Mi characters");
            if (firstCharacter && code == '\uFEFF') {
                firstCharacter = false;
                continue;
            }
            firstCharacter = false;
            char character = (char) code;
            if (character == '\r') {
                int next = reader.read();
                if (next != '\n' && next != -1) reader.unread(next);
                character = '\n';
            }
            if (quoted) {
                if (character == '"') {
                    quoted = false;
                    closedQuote = true;
                } else {
                    field.append(character);
                }
            } else if (closedQuote && character == '"') {
                field.append('"');
                quoted = true;
                closedQuote = false;
            } else if (character == ',' || character == '\n') {
                fields.add(field.toString());
                if (fields.size() > 4) throw problem(line, "expected exactly four columns");
                field.setLength(0);
                closedQuote = false;
                started = false;
                if (character == '\n') {
                    rows.add(new Row(recordLine, List.copyOf(fields)));
                    if (rows.size() > MAX_RECORDS) throw problem(line, "too many records (maximum 100000 transactions)");
                    fields.clear();
                    recordLine = line + 1;
                }
            } else if (closedQuote) {
                throw problem(line, "unexpected character after closing quote");
            } else if (character == '"') {
                if (started) throw problem(line, "quote in unquoted field");
                quoted = true;
                started = true;
            } else {
                field.append(character);
                started = true;
            }
            if (field.length() > MAX_FIELD_LENGTH) throw problem(line, "field exceeds 16384 characters");
            if (character == '\n') line++;
        }
        if (quoted) throw problem(recordLine, "unclosed quoted field");
        if (started || closedQuote || !fields.isEmpty()) {
            fields.add(field.toString());
            if (fields.size() > 4) throw problem(line, "expected exactly four columns");
            rows.add(new Row(recordLine, List.copyOf(fields)));
            if (rows.size() > MAX_RECORDS) throw problem(line, "too many records (maximum 100000 transactions)");
        }
        return List.copyOf(rows);
    }

    private static IllegalArgumentException problem(int line, String message) {
        return new IllegalArgumentException("line " + line + ": " + message);
    }
}
