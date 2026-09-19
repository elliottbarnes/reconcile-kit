package ca.elliottbarnes.reconcile;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** CLI boundary. No files are modified; reports go to stdout and errors to stderr. */
public final class Main {
    private static final String USAGE = """
        Usage: reconcile-kit LEDGER.csv PROCESSOR.csv [--format text|json|tsv]
        Compare UTF-8 transaction exports by transaction_id. Default format: text.
        Exit codes: 0 = match, 1 = reconciliation exceptions, 2 = usage or invalid input,
                    3 = input/output failure.
        Both files require: transaction_id,currency,amount,description
        Reports include source data. Use synthetic or appropriately protected exports.
        """;

    private Main() {}

    public static void main(String[] arguments) {
        var output = new PrintWriter(System.out, false, StandardCharsets.UTF_8);
        var error = new PrintWriter(System.err, false, StandardCharsets.UTF_8);
        int code = run(arguments, output, error);
        output.flush();
        error.flush();
        if (output.checkError() || error.checkError()) code = 3;
        System.exit(code);
    }

    static int run(String[] arguments, PrintWriter output, PrintWriter error) {
        if (arguments.length == 1 && (arguments[0].equals("--help") || arguments[0].equals("-h"))) {
            output.print(USAGE);
            return 0;
        }
        if (arguments.length != 2 && arguments.length != 4) {
            error.print(USAGE);
            return 2;
        }
        String format = "text";
        if (arguments.length == 4) {
            if (!arguments[2].equals("--format") || !java.util.Set.of("text", "json", "tsv").contains(arguments[3])) {
                error.print(USAGE);
                return 2;
            }
            format = arguments[3];
        }
        try {
            var ledger = LedgerReader.read(Path.of(arguments[0]));
            var processor = LedgerReader.read(Path.of(arguments[1]));
            var report = Reconciler.reconcile(ledger, processor);
            output.print(switch (format) {
                case "json" -> ReportWriter.json(report);
                case "tsv" -> ReportWriter.tsv(report);
                default -> ReportWriter.text(report);
            });
            return report.mismatches() == 0 ? 0 : 1;
        } catch (InvalidPathException exception) {
            error.println("Invalid input path.");
            return 2;
        } catch (IllegalArgumentException exception) {
            error.println("Invalid input: " + exception.getMessage());
            return 2;
        } catch (IOException exception) {
            error.println("Input/output failure: " + exception.getMessage());
            return 3;
        }
    }
}
