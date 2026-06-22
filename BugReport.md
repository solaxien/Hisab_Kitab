# HisabKitab Bug Report

**Audit:** 22 June 2026 (IST)
**Baseline:** `main` at `bf78eb30`, including all current uncommitted changes

## Summary

A full static/build audit found **17 actionable defects: 5 High and 12 Medium**. No Critical defect was found. Current changes fixed several earlier bill-integrity problems, but the app is not ready for real financial/inventory data until the five High findings are addressed.

## Verification

- JVM tests: **5 passed, 0 failed**.
- Android lint: **0 errors, 22 warnings**.
- Debug and Android-test APKs were produced.
- Android tests compiled/packaged but were not run (no device/emulator).
- Production Kotlin, Room schema/DAO, repository, Compose UI, scanner, backup/restore, widgets, manifest, resources, tests, README, and TODO were inspected.
- Gradle used repository-local caches. Debug, lint, unit-test, Android-test packaging, and unsigned release assembly all completed successfully; initial cache/signing failures were sandbox-only.

## High Severity

### HK-001 — Deleting a product with loss history can throw

`loss_entries.productId` is `RESTRICT` (`Entities.kt:63-71`), but `deleteProduct()` checks only active bills and does not catch the constraint failure (`HisabKitabDao.kt:192-195`). Repository/ViewModel also do not catch it (`ShopRepository.kt:56`, `ShopViewModels.kt:80`), and the UI closes the dialog immediately (`InventoryScreen.kt:205-213`).

**Impact:** a normal delete can raise an uncaught `SQLiteConstraintException`, silently fail, or terminate the process.

**Fix:** check loss references transactionally, return a typed result, and surface it without dismissing the dialog.

### HK-002 — Product editing overwrites newer stock

The editor saves a complete entity containing stock captured when it opened (`InventoryScreen.kt:273-279,373-384`), and existing products use full-row `@Update` (`ShopRepository.kt:35-37`; `HisabKitabDao.kt:27-28`).

**Impact:** a payment, restock, loss, widget action, or second task can change stock; a later metadata/price save writes stale stock and undoes that change.

**Fix:** separate metadata updates from stock adjustments, or use optimistic version checks.

### HK-003 — Arithmetic overflow can corrupt financial data

Prices accept any non-negative `Long` and stock any non-negative `Int` (`ShopRepository.kt:47-54`). Totals use unchecked multiplication/addition (`BusinessRules.kt:7-11`; `ShopViewModels.kt:60-72,235-251`). `stockAfterSale` subtracts `Int` before clamping (`BusinessRules.kt:23`), and item count sums into `Int` (`Relations.kt:15`).

**Impact:** accepted values can wrap negative/unrelated, corrupting stock, bills, profit, widgets, and analytics without an exception.

**Fix:** enforce practical maxima and checked `Long` arithmetic everywhere, including restore.

### HK-004 — Backup export is not one database snapshot

`BackupCodec.encode()` performs five independent DAO reads without an enclosing Room transaction (`BackupCodec.kt:16-25`).

**Impact:** concurrent payment/edit/delete/loss can create a backup whose tables represent different moments and restore misleading inventory/analytics.

**Fix:** read one snapshot through a transactional DAO method; serialize afterward. Add a concurrent-write test.

### HK-005 — Restore accepts invalid financial states

Validation only checks format/version, duplicate product/bill IDs, and basic references (`BackupCodec.kt:27-44`). It does not validate child/settings ID uniqueness, SKU/barcode uniqueness, positive IDs/timestamps, non-negative values, bill lifecycle combinations, one settings row, duplicate product lines, or size/count limits. `replaceAll()` then clears all tables (`HisabKitabDao.kt:161-178`).

**Impact:** malformed data can replace valid local data while containing negative quantities, impossible paid states, duplicates, or overflow values.

**Fix:** fully validate a versioned model before replacement and add hostile-backup/rollback tests.

## Medium Severity

### HK-006 — Typed operation failures are ignored

ViewModels discard save/delete/restock/bill/loss results (`ShopViewModels.kt:79-83,137-149,263-266`); forms dismiss immediately (`InventoryScreen.kt:174-185,205-213`); payment missing/not-found/invalid states only clear warnings (`ShopViewModels.kt:162-164`).

**Impact:** failed actions appear successful. Also, every `SQLiteException` is misreported internally as duplicate SKU/barcode.

**Fix:** expose typed UI events, show exact errors, and dismiss only on success.

### HK-007 — Forced payment loses the shortage discrepancy

Payment clamps stock to zero without an audit record (`HisabKitabDao.kt:281-295`; dialog at `BillsScreens.kt:405-419`).

**Impact:** missing sold units are permanently lost, contrary to `README.md:145-163`.

**Fix:** store bill/product/requested/available/missing/reason/timestamp in the payment transaction.

### HK-008 — Monthly and custom analytics were removed

**Evidence:** `AnalyticsPeriod` now contains only `TODAY`, `YESTERDAY`, `THIS_WEEK`, and `LAST_WEEK` (`ShopViewModels.kt:190`); the range function has no month or custom branch (`ShopViewModels.kt:275-285`). This conflicts with the monthly/custom analytics requirements in `README.md:17,81-96`.

**Impact:** users can no longer produce the monthly and arbitrary-date reports promised by the product specification; this is a functional regression from the prior implementation.

**Fix:** restore This Month, Last Month, and Custom options. For custom selection, convert picker dates to `LocalDate` and build `Asia/Kolkata` start-of-day boundaries.
### HK-009 — Shortage warning can belong to another bill

State stores `pendingBillId`, but every detail receives the global shortage list (`ShopViewModels.kt:92-95,118-123`; `HisabKitabApp.kt:165-180`).

**Impact:** navigation/deep links can show bill A’s warning on bill B; confirm pays A and pops B.

**Fix:** scope warning state to the displayed bill and clear it on exit.

### HK-010 — Bill numbers can collide

Numbers use the last five milliseconds plus a process-local counter modulo 10 (`ShopRepository.kt:25,60-66`) and have no unique index (`Entities.kt:29-38`).

**Fix:** use a persisted transactional sequence and database uniqueness.

### HK-011 — Scanner errors and unknown barcodes are silent

Camera acquisition/back-camera binding lack failure handling (`BarcodeScannerScreen.kt:112-142`). Bill scanning always pops regardless of add result (`HisabKitabApp.kt:190-198`).

**Fix:** handle camera/provider/analyzer errors, offer manual entry, and keep unknown scans open with a create/search path.

### HK-012 — Time-based screens do not roll over

Today/month boundaries recalculate only when database/settings/filter flows emit (`ShopViewModels.kt:213-256,268-285`).

**Impact:** screens stay stale across midnight/month changes.

**Fix:** combine a local-date flow that emits at midnight and on resume/time/timezone changes.

### HK-013 — Sales chart removes no-sale days

Only dates present in grouped paid bills become chart values (`ShopViewModels.kt:238-243`).

**Impact:** sales days separated by a week render as adjacent bars.

**Fix:** generate every date in range and fill missing days with zero.

### HK-014 — Widgets remain stale after mutations

Widgets query only in `provideGlance()` (`HisabKitabWidgets.kt:80-185`) and rely on a 30-minute XML refresh; mutations never call `updateAll()`.

**Impact:** payment/loss/restock/edit/restore can leave widgets wrong for 30 minutes; create-bill may retain a deleted selection.

**Fix:** refresh affected widgets after each successful commit and restore.

### HK-015 — Large backups can freeze or exhaust memory

Restore reads the entire file into one string (`MainActivity.kt:56-60`) then parses after returning to Main (`MainActivity.kt:62`; `BackupCodec.kt:27-44`). Export also builds all JSON in memory; no size limit exists.

**Fix:** validate/parse on IO, reject oversized input early, cap records/strings, and use streaming/zipped data.

### HK-016 — Settings expose non-functional features

Notification/PIN toggles only store booleans (`SettingsScreen.kt:138-155`); no alerts, permission flow, PIN setup, or lock exists. Appearance/support handlers are empty (`SettingsScreen.kt:148-162`).

**Impact:** users can enable security/alerts that do nothing.

**Fix:** hide/disable with explicit status or implement end-to-end.

### HK-017 — Platform backup policy is unspecified

Manifest enables backup (`AndroidManifest.xml:14-16`), while both rule files remain templates and `data_extraction_rules.xml` contains a TODO.

**Impact:** financial data may enter OS/cloud backup under defaults without a deliberate policy.

**Fix:** explicitly include/exclude database/preferences in both rule formats, or disable platform backup.

## Product and Test Gaps

Not counted above:

- PDF/CSV export described in `README.md` is absent.
- Home active-bill cards still use a separate Open button.
- Average-bill and explicit recent-bills views are missing.
- Release optimization is disabled.

Priority missing tests: loss-referenced deletion; stale-edit concurrency; arithmetic boundaries; concurrent backup snapshot; hostile restore; IST ranges/rollover; discrepancy persistence; operation error UI; camera/unknown barcode; widget refresh.

## Build Notes

Lint has **22 warnings and 0 errors**, mostly dependency advisories plus deprecated APIs and an unchecked heterogeneous `combine` cast. `assembleRelease` completed successfully and produced the unsigned release APK.

The audit does not claim runtime device verification. Room instrumentation tests were packaged but not executed, and no Compose UI tests cover primary journeys.

## Fix Order

1. HK-001 through HK-005 with regression tests.
2. UI error handling and forced-payment discrepancy.
3. Date boundaries, bill-scoped state, invoice sequence.
4. Scanner, rollover, chart, widgets, and backup scaling.
5. Finish/hide settings and define platform-backup policy.
6. Add device UI/instrumentation coverage and remaining product features.
