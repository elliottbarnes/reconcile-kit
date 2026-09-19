package ca.elliottbarnes.reconcile;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {
    @TempDir Path directory;

    private record Result(int code, String output, String error) {}

    private Result run(String... arguments) {
        var output = new StringWriter();
        var error = new StringWriter();
        int code = Main.run(arguments, new PrintWriter(output), new PrintWriter(error));
        return new Result(code, output.toString(), error.toString());
    }

    @Test
    void documentsHelpAndRejectsBadArgumentsWithoutReport() {
        assertEquals(0, run("--help").code());
        assertEquals(2, run().code());
        var invalid = run("examples/ledger.csv", "examples/processor.csv", "--format", "xml");
        assertEquals(2, invalid.code());
        assertTrue(invalid.output().isEmpty());
    }

    @Test
    void checksActualFixturesAndReturnsExpectedCodes() {
        var mismatch = run("examples/ledger.csv", "examples/processor.csv");
        assertEquals(1, mismatch.code());
        assertTrue(mismatch.output().contains("matched=3 | exceptions=5"));
        assertTrue(mismatch.error().isEmpty());
        assertEquals(0, run("examples/matched-ledger.csv", "examples/matched-processor.csv").code());
        assertEquals(3, run(directory.resolve("missing.csv").toString(), "examples/ledger.csv").code());
    }

    @Test
    void invalidInputsDoNotProducePartialOrSuccessReports() throws Exception {
        var invalid = directory.resolve("invalid.csv");
        Files.writeString(invalid, "transaction_id,currency,amount,description\na,CAD,1.001,bad precision\n");
        var result = run(invalid.toString(), "examples/ledger.csv", "--format", "json");
        assertEquals(2, result.code());
        assertTrue(result.output().isEmpty());
        assertTrue(result.error().contains("line 2"));
    }

    @Test
    void invalidUtf8IsAnIoFailure() throws Exception {
        var invalid = directory.resolve("invalid.csv");
        Files.write(invalid, new byte[] {(byte) 0xC3, (byte) 0x28});
        assertEquals(3, run(invalid.toString(), "examples/ledger.csv").code());
    }

    @Test
    void jsonIsStableEscapedAndUsesDecimalStrings() {
        var first = run("examples/ledger.csv", "examples/processor.csv", "--format", "json");
        var second = run("examples/ledger.csv", "examples/processor.csv", "--format", "json");
        assertEquals(first, second);
        assertTrue(first.output().contains("\"amount\": \"129.99\""));
        assertTrue(first.output().contains("Quoted \\\"special\\\" item"));
        assertEquals("\"line\\n\\t\\u0001\\\\\\\"\"", ReportWriter.quote("line\n\t\u0001\\\""));
        assertTrue(first.output().indexOf("txn-001") < first.output().indexOf("txn-008"));
    }

    @Test
    void tsvHasOneLinePerKeyAndPreservesDuplicateEvidence() {
        var result = run("examples/ledger.csv", "examples/processor.csv", "--format", "tsv");
        assertEquals(9, result.output().lines().count());
        assertTrue(result.output().contains("txn-006\tDUPLICATE_LEDGER\tCAD;CAD\t32.00;32.00\tCAD\t32.00"));
    }
}
