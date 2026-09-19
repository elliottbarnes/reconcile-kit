package ca.elliottbarnes.reconcile;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

import ca.elliottbarnes.reconcile.LedgerReader.Transaction;
import ca.elliottbarnes.reconcile.Reconciler.Status;

class ReconcilerTest {
    private static List<Transaction> rows(String rows) throws Exception {
        return LedgerReader.read(new StringReader("transaction_id,currency,amount,description\n" + rows));
    }

    @Test
    void classifiesEveryOutcomeAndNeverSilentlyMatchesDuplicateKeys() throws Exception {
        var report = Reconciler.reconcile(rows("""
            a,CAD,1,x
            b,CAD,2,x
            c,CAD,3,x
            d,CAD,4,x
            f,CAD,6,x
            f,CAD,6,x
            g,CAD,7,x
            h,CAD,8,x
            h,CAD,8,x
            """), rows("""
            h,CAD,8,x
            h,CAD,8,x
            g,CAD,7,x
            g,CAD,7,x
            f,CAD,6,x
            e,CAD,5,x
            c,USD,3,x
            b,CAD,9,x
            a,CAD,1.000,x
            """));
        assertEquals(List.of("a", "b", "c", "d", "e", "f", "g", "h"), report.entries().stream().map(Reconciler.Entry::id).toList());
        assertEquals(List.of(Status.MATCHED, Status.AMOUNT_MISMATCH, Status.CURRENCY_MISMATCH,
            Status.MISSING_PROCESSOR, Status.MISSING_LEDGER, Status.DUPLICATE_LEDGER,
            Status.DUPLICATE_PROCESSOR, Status.DUPLICATE_BOTH), report.entries().stream().map(Reconciler.Entry::status).toList());
        assertEquals(7, report.mismatches());
        assertEquals(2, report.entries().getLast().ledger().size());
        for (var status : Status.values()) assertEquals(1L, report.counts().get(status));
    }

    @Test
    void duplicatesTakePrecedenceOverAbsenceAndCurrencyDisagreement() throws Exception {
        var report = Reconciler.reconcile(rows("a,CAD,1,x\na,USD,1,x\n"), List.of());
        assertEquals(Status.DUPLICATE_LEDGER, report.entries().getFirst().status());
    }

    @Test
    void sumsRefundsExactlyAndSeparatesCurrencies() throws Exception {
        var report = Reconciler.reconcile(rows("a,CAD,0.1,x\nb,CAD,0.2,x\nc,CAD,-0.1,x\nd,JPY,2,x\n"),
            rows("a,CAD,0.1,x\nb,CAD,0.2,x\nc,CAD,-0.1,x\ne,USD,3.00,x\n"));
        assertEquals(List.of("CAD", "JPY", "USD"), report.totals().stream().map(Reconciler.Total::currency).toList());
        assertEquals("0.20", report.totals().getFirst().ledger().toPlainString());
        assertEquals("0.00", report.totals().getFirst().delta().toPlainString());
        assertEquals("-3.00", report.totals().getLast().delta().toPlainString());
    }

    @Test
    void emptyExportsMatch() {
        var report = Reconciler.reconcile(List.of(), List.of());
        assertEquals(0, report.mismatches());
        assertTrue(report.totals().isEmpty());
    }
}
