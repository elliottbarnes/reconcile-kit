package ca.elliottbarnes.reconcile;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LedgerReaderTest {
    private static final String HEADER = "transaction_id,currency,amount,description\n";

    @Test
    void parsesBomCrlfEscapedQuotesAndMultilineDescription() throws Exception {
        var rows = LedgerReader.read(new StringReader("\uFEFFtransaction_id,currency,amount,description\r\n"
            + "abc,CAD,12.30,\"Hello, \"\"world\"\"\r\nnext line\"\r\n"
            + "def,JPY,300,last row"));
        assertEquals(2, rows.size());
        assertEquals("Hello, \"world\"\nnext line", rows.getFirst().description());
        assertEquals(2, rows.getFirst().line());
        assertEquals(4, rows.get(1).line());
        assertEquals(new BigDecimal("12.30"), rows.getFirst().amount());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "a,CAD,1.00,\"unclosed", "a,CAD,1.00,un\"quoted", "a,CAD,1.00,\"closed\"x",
        "a,CAD,1.00,extra,column", "a,CAD,1.00", "\n", "a,CAD,1.00,\"closed\" \n"
    })
    void rejectsMalformedCsv(String row) {
        assertThrows(IllegalArgumentException.class, () -> LedgerReader.read(new StringReader(HEADER + row)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "a,CAD,0.001,x", "a,JPY,1.1,x", "a,KWD,1.0001,x", "a,USD,NaN,x",
        "a,USD,1e2,x", "a,usd,1,x", "a,ZZZ,1,x", "a,XXX,1,x", ",USD,1,x",
        " bad,USD,1,x", "a,USD,+1,x", "a,USD, 1,x", "a,USD,1.,x"
    })
    void rejectsInvalidCurrenciesIdentifiersAndMoney(String row) {
        assertThrows(IllegalArgumentException.class, () -> LedgerReader.read(new StringReader(HEADER + row)));
    }

    @Test
    void preservesLargeAmountsWithoutBinaryFloatingPointOrRounding() throws Exception {
        var rows = LedgerReader.read(new StringReader(HEADER
            + "large,CAD,999999999999999999999999999999.9900,x\n"
            + "refund,USD,-0.3000,x\n"
            + "dinar,KWD,1.125,x\n"));
        assertEquals("999999999999999999999999999999.99", rows.getFirst().amount().toPlainString());
        assertEquals("-0.30", rows.get(1).amount().toPlainString());
        assertEquals("1.125", rows.get(2).amount().toPlainString());
    }

    @Test
    void permitsHeaderOnlyExportsAndRejectsMissingOrChangedHeaders() throws Exception {
        assertTrue(LedgerReader.read(new StringReader(HEADER)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> LedgerReader.read(new StringReader("")));
        assertThrows(IllegalArgumentException.class, () -> LedgerReader.read(new StringReader("id,currency,amount,description\n")));
    }

    @Test
    void limitsFieldLengthAndTransactionCount() {
        assertThrows(IllegalArgumentException.class, () -> LedgerReader.read(new StringReader(HEADER + "a,CAD,1," + "x".repeat(16_385))));
        assertThrows(IllegalArgumentException.class, () -> LedgerReader.read(new StringReader(HEADER + "a,CAD,1,x\n".repeat(100_001))));
    }
}
