#!/bin/sh
# Run from the repository root after ./gradlew installDist.
set -eu

cli=build/install/reconcile-kit/bin/reconcile-kit
"$cli" examples/matched-ledger.csv examples/matched-processor.csv

for format in text json tsv; do
    if "$cli" examples/ledger.csv examples/processor.csv --format "$format" > "build/report.$format"; then
        printf 'Expected mismatch exit code for %s\n' "$format" >&2
        exit 1
    else
        code=$?
        test "$code" -eq 1
    fi
done

# An independent parser verifies that the JSON contract is consumable.
python3 -c 'import json, sys; r=json.load(sys.stdin); assert r["schemaVersion"] == 1; assert r["exceptionKeys"] == 5; assert r["entries"][0]["ledger"][0]["amount"] == "129.99"' < build/report.json
awk -f scripts/summarize.awk build/report.tsv

if printf 'bad header\n' | awk -f scripts/summarize.awk; then
    printf 'Expected awk to reject a malformed header\n' >&2
    exit 1
else
    code=$?
    test "$code" -eq 2
fi

printf 'Installed CLI, exit codes, JSON, TSV, and awk checks passed.\n'
