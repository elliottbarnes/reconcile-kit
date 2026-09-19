# Summarize Reconcile Kit's TSV output, never raw CSV. POSIX awk; no money arithmetic.
# Usage: awk -f scripts/summarize.awk report.tsv
BEGIN {
    FS = "\t"
    statuses = "MATCHED AMOUNT_MISMATCH CURRENCY_MISMATCH MISSING_LEDGER MISSING_PROCESSOR DUPLICATE_LEDGER DUPLICATE_PROCESSOR DUPLICATE_BOTH"
    size = split(statuses, order, " ")
    for (i = 1; i <= size; i++) known[order[i]] = 1
}
NR == 1 {
    if ($0 != "transaction_id\tstatus\tledger_currency\tledger_amount\tprocessor_currency\tprocessor_amount") {
        print "Expected a Reconcile Kit TSV header." > "/dev/stderr"
        invalid = 1
        exit 2
    }
    next
}
{
    if (NF != 6 || !($2 in known)) {
        print "Invalid TSV record at line " NR > "/dev/stderr"
        invalid = 1
        exit 2
    }
    counts[$2]++
    total++
}
END {
    if (invalid) exit 2
    if (NR == 0) {
        print "Expected a Reconcile Kit TSV header." > "/dev/stderr"
        exit 2
    }
    printf "%-22s %6s\n", "STATUS", "KEYS"
    for (i = 1; i <= size; i++) printf "%-22s %6d\n", order[i], counts[order[i]]
    printf "%-22s %6d\n", "TOTAL", total
}
