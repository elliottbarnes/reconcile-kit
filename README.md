# Reconcile Kit

**Two exports. One explainable answer.**

A Java 21 command-line tool that reconciles ledger and payment-processor CSV exports by transaction ID. It shows missing transactions, amount and currency differences, and duplicate IDs, with source-line evidence for every exception. Money stays decimal from parsing through reporting.

This is a standalone educational portfolio project using synthetic fixtures. It is not an accounting system, a production ledger, financial advice, or a representation of an employer's systems.

## Try it

Install a [Java 21 JDK](https://adoptium.net/temurin/releases/?version=21). Gradle itself does not need to be installed: the checked-in wrapper downloads and verifies the pinned distribution on first use.

```sh
./gradlew build installDist
build/install/reconcile-kit/bin/reconcile-kit examples/ledger.csv examples/processor.csv
```

The sample intentionally exits with **1** because it contains five exception keys. A clean comparison exits with **0**:

```sh
build/install/reconcile-kit/bin/reconcile-kit \
  examples/matched-ledger.csv examples/matched-processor.csv
```

On Windows use `gradlew.bat` and `build\install\reconcile-kit\bin\reconcile-kit.bat`. The optional awk and smoke scripts require a POSIX environment; the Java CLI does not.

## What the example reveals

```text
RECONCILE KIT
Result: MISMATCH
Rows: ledger=8 processor=7
Keys: 8 | matched=3 | exceptions=5

TOTALS (all input rows, including duplicates; delta = ledger - processor)
CAD  ledger=236.74  processor=274.48  delta=-37.74
JPY  ledger=1500  processor=1500  delta=0
USD  ledger=75.00  processor=0.00  delta=75.00

EXCEPTIONS
txn-002  AMOUNT_MISMATCH
  ledger: CAD 48.50 [line 3]
  processor: CAD 47.50 [line 4]
txn-004  CURRENCY_MISMATCH
  ledger: USD 75.00 [line 5]
  processor: CAD 75.00 [line 6]
txn-005  MISSING_PROCESSOR
  ledger: CAD 14.25 [line 6]
  processor: (absent)
txn-006  DUPLICATE_LEDGER
  ledger: CAD 32.00 [line 7]; CAD 32.00 [line 8]
  processor: CAD 32.00 [line 7]
txn-007  MISSING_LEDGER
  ledger: (absent)
  processor: CAD 9.99 [line 8]
```

Totals include every input row, including duplicates. They are diagnostic context, **not** proof of reconciliation: equal totals can conceal mismatched transactions. Different currencies are never summed together or converted.

## Input contract

Files are UTF-8 with this exact header and column order:

```csv
transaction_id,currency,amount,description
order-1042,CAD,129.99,"Blue Harbor Books, order 1042"
refund-1042,CAD,-20.00,"Refund with ""quoted"" detail"
```

| Field | Contract |
| --- | --- |
| `transaction_id` | Case-sensitive, 1–128 ASCII letters, digits or `._:/-`; must start with a letter or digit. The ID is the matching key across both files, regardless of currency. |
| `currency` | Uppercase ISO 4217 code recognized by the JDK, with a defined minor-unit scale from 0–4. `JPY` has 0, `CAD` has 2, `KWD` has 3. |
| `amount` | Signed plain decimal; optional minus, no exponent/group separators/whitespace. At most 30 integer and 6 fractional digits. Values must fit the currency's minor unit exactly; no rounding is applied. |
| `description` | Required column; value may be empty. Preserved in JSON evidence, but not used for matching. |

The parser handles comma-delimited quoted fields, doubled quotes, multiline descriptions, LF/CRLF/CR line endings, an optional leading UTF-8 BOM, and an optional final newline. Quoted line endings normalize to LF. It rejects blank rows, extra/missing columns, stray quotes and characters after closing quotes. It does not guess delimiter, header aliases, encodings, number locale, or malformed quoting. Surrounding spaces are data, not silently trimmed.

`1`, `1.0` and `1.000` compare equally for CAD and serialize as `"1.00"`. `1.001 CAD` is rejected rather than rounded. Negative values are allowed for refunds. Currency metadata follows the installed JDK, so keep JDK patch releases current.

Each input is limited to 100,000 transactions, 16,384 characters per field, and 64 Mi characters (after CRLF normalization). Parsing and indexing are in memory; the limits are guardrails, not a guarantee of low memory usage. Use a larger heap for unusually large descriptions.

## Matching rules

Classification follows this order, once per distinct transaction ID:

1. Duplicate IDs on either side produce `DUPLICATE_LEDGER`, `DUPLICATE_PROCESSOR`, or `DUPLICATE_BOTH`. Even identical duplicate rows are exceptions; no occurrence is silently discarded.
2. A key appearing on only one side produces `MISSING_LEDGER` or `MISSING_PROCESSOR`.
3. Different currency codes produce `CURRENCY_MISMATCH`; amounts are not compared across currencies.
4. Different exact amounts produce `AMOUNT_MISMATCH`.
5. Otherwise the result is `MATCHED`, regardless of row order or description.

Duplicate status takes precedence over absence or any monetary difference because the key is ambiguous. All occurrences remain in the report so a consumer can inspect them. Two header-only exports match; a missing/empty file does not.

## Reports and shell workflows

```sh
# Default human-readable report
build/install/reconcile-kit/bin/reconcile-kit ledger.csv processor.csv

# Machine-readable evidence; amounts are JSON strings to retain precision
build/install/reconcile-kit/bin/reconcile-kit ledger.csv processor.csv --format json > report.json

# Tab-separated records for lightweight shell inspection
build/install/reconcile-kit/bin/reconcile-kit ledger.csv processor.csv --format tsv > report.tsv
awk -f scripts/summarize.awk report.tsv
```

Reports go to stdout; diagnostics go to stderr. The application does not modify either input. A failed input produces no report. Redirection is controlled by your shell: choose a new output path, never an input filename.

| Exit code | Meaning |
| --- | --- |
| `0` | All keys match, or help was requested. |
| `1` | Reconciliation completed with one or more exception keys. |
| `2` | Invalid CLI arguments, CSV structure, ID, currency or amount. |
| `3` | Input/output failure, including invalid UTF-8 or a failed output stream. |

Use the installed launcher when scripting; `./gradlew run` reports application failures as Gradle task failures and does not preserve the CLI's exact exit codes. In shell pipelines, a downstream command may hide the CLI status. Capture the report first and check the exit code before processing it.

JSON has `schemaVersion: 1`, ordered status counts, currency totals and entries. Entries are sorted lexically by ID; currencies are sorted by code; duplicate evidence follows source-line order. There is no timestamp, random ID or machine path, so identical input bytes yield identical output on the same JDK. Reordering input changes evidence line numbers even when the match results stay the same. All monetary values serialize as decimal strings.

TSV has a fixed six-column header. Duplicate currencies and amounts use positional semicolon-separated lists. Descriptions are omitted so embedded tabs/newlines cannot break the format. The included **POSIX awk** utility consumes this TSV, counts statuses in a fixed order, and rejects an unexpected header or status. It deliberately does no monetary arithmetic because ordinary awk numbers use floating point.

Reports can contain transaction descriptions and amounts. Run it with synthetic data or appropriately protected local files. Runtime execution makes no network requests and has no telemetry. The initial build downloads Gradle and test dependencies.

## Architecture and tradeoffs

```text
UTF-8 CSV files
    ↓
CsvReader        strict record parsing + source line tracking
    ↓
LedgerReader     schema, identifier, currency + exact decimal validation
    ↓
Reconciler       keyed index → classification + per-currency totals
    ↓
ReportWriter     deterministic text / JSON / TSV
    ↓
Main             stdout, stderr + explicit exit status
```

The runtime uses only the Java standard library. Java records keep validated transactions and reports immutable at the boundary; `BigDecimal` with `RoundingMode.UNNECESSARY` rejects lossy amounts; sorted maps and sets make output predictable. `Reconciler` has no file or terminal access, so decision rules can be tested independently from parsing and presentation.

The small CSV parser makes strict behavior explicit and independently testable. The tradeoff is maintaining a carefully bounded subset rather than inheriting a mature CSV library's larger dialect surface. The JSON writer handles the limited report schema and escaping directly; an independent parser checks its output in the smoke test. For an evolving schema or more formats, a maintained serialization library would be preferable.

Indexing plus ordered reporting costs O(n + k log k) time, where n is total rows and k is distinct keys; records and output are held in memory. This version intentionally does not implement fuzzy matching, one-to-many settlement grouping, date windows, FX conversion, configurable rounding/tolerances, a database, parallel parsing, or automated correction. Those require explicit business rules, not heuristics hidden in a matcher.

## Development and verification

```sh
./gradlew test                     # JUnit tests; HTML at build/reports/tests/test/index.html
./gradlew build installDist        # ZIP/TAR distribution and local launcher
sh scripts/smoke.sh                # CLI exit codes, JSON parser and awk; requires Python 3
```

Tests cover quoted multiline CSV, malformed rows, invalid UTF-8, currency precision, large exact amounts, negative values, all outcome categories, duplicate precedence, source-line evidence, deterministic serialization and CLI errors. JaCoCo produces coverage reports under `build/reports/jacoco/test/`; no benchmark claims are made.

Build choices are pinned and inspectable:

- Java 21 toolchain; Gradle 9.7.1 wrapper with the official SHA-256 distribution checksum.
- JUnit 6.1.3 for tests; no runtime libraries.
- Dependency locking and SHA-256 verification metadata for downloaded build dependencies.
- GitHub Actions pinned to immutable commits, read-only repository permissions and automatic wrapper validation. CI runs the build and installed-CLI smoke test on Ubuntu.
- Dependabot checks Gradle and action dependencies monthly. Review changed versions and checksums before merging updates.

To update dependencies intentionally, regenerate lockfiles with `./gradlew dependencies --write-locks` and verification metadata with `./gradlew build --write-verification-metadata sha256`, then review both against trusted artifacts. Updating the wrapper should use Gradle's wrapper task and the newly published distribution checksum, not a hand-edited URL alone.

References: [Gradle wrapper verification](https://docs.gradle.org/current/userguide/gradle_wrapper.html), [Gradle dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html), [Java BigDecimal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html), and [Java Currency](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Currency.html).

## License

MIT © Elliott Barnes. All names and transactions in `examples/` are fictional.
