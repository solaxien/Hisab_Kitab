# HisabKitab Bug Report

**Audit date:** 2026-06-16 11:58 IST
**Branch:** `main`
**Baseline:** commit `42fe82e` plus the current uncommitted working tree
**Automation:** Bug Logger - HisabKitab (`bug-logger-hisabkitab`)
**Recommendation:** Do not ship until the High-severity data-integrity issues are fixed.

## Executive Summary

The current working tree builds successfully. JVM tests pass, lint reports no
errors, debug/release APKs package, and the Android-test APK packages. The
main release risks are not build failures; they are silent data corruption,
misleading financial analytics, and user operations that can fail without
recoverable feedback.

| Severity | Count |
|---|---:|
| Critical | 0 |
| High | 7 |
| Medium | 11 |

Highest-priority work:

1. Make every bill mutation transactional and legal only for `ACTIVE` bills.
2. Prevent deletion or payment of products missing from active bills.
3. Validate all product, quantity, money, loss, and restore values below the UI layer.
4. Make backup export a consistent Room snapshot and harden restore validation.
5. Correct India-local report boundaries, period rollover, and chart date gaps.

## Scope and Method

The audit covered:

- Room entities, relations, queries, transactions, and schema constraints.
- Product, bill, payment, loss, analytics, settings, backup, and restore flows.
- Compose screens, navigation, barcode scanning, permissions, and manifest policy.
- README/business expectation alignment and automated test coverage.
- Debug/release compilation, JVM tests, lint, and Android-test packaging.

This was a static and build-time audit. No emulator or device was connected, so
camera behavior, runtime permissions, UI lifecycle behavior, and instrumented
database behavior were not exercised on Android hardware.

## Verification Performed

Command run:

```powershell
$env:GRADLE_USER_HOME=(Resolve-Path '.gradle-local').Path
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease compileDebugAndroidTestKotlin assembleDebugAndroidTest --no-daemon
git diff --check
```

Results:

- `testDebugUnitTest`: passed, 4 tests, 0 failures, 0 errors.
- `lintDebug`: passed, 0 errors and 22 warnings.
- `assembleDebug`: passed.
- `assembleRelease`: passed; release APK is unsigned.
- `compileDebugAndroidTestKotlin`: passed.
- `assembleDebugAndroidTest`: passed.
- `git diff --check`: no whitespace errors; Git reported LF-to-CRLF conversion warnings for existing modified files.

Build-environment note:

- Android Gradle reported that `C:\Users\amogh\.android\analytics.settings`
  is not writable in the sandbox. This did not block the build.

Generated artifacts:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

## Working Tree Context

The audit included the current uncommitted working tree. Existing local changes
were treated as intentional and were not reverted:

- Modified: `.idea/misc.xml`
- Modified: `BugReport.md`
- Deleted: `app/src/main/java/com/amg/hisabkitab/logic/Analytics.kt`
- Deleted: `app/src/main/java/com/amg/hisabkitab/logic/Inventory.kt`
- Modified: `app/src/main/java/com/amg/hisabkitab/ui/screens/AnalyticsScreen.kt`
- Modified: `app/src/main/java/com/amg/hisabkitab/ui/screens/HomeScreen.kt`
- Modified: `app/src/main/java/com/amg/hisabkitab/ui/screens/InventoryScreen.kt`
- Untracked local cache/error output under `.android-local/`, `.gradle-local/`, and `.kotlin/errors/`

## High-Severity Findings

### HK-001 - Deleting a product can make a paid bill skip stock deduction

**Evidence:**

- `app/src/main/java/com/amg/hisabkitab/data/local/Entities.kt:41-60`
- `app/src/main/java/com/amg/hisabkitab/data/local/HisabKitabDao.kt:30-31`
- `app/src/main/java/com/amg/hisabkitab/domain/model/BusinessRules.kt:13-20`
- `app/src/main/java/com/amg/hisabkitab/data/local/HisabKitabDao.kt:169-193`

`bill_items.productId` has no foreign key to `products`, and product deletion
does not check active bill references. Payment loads referenced products with
`mapNotNull`, so missing products disappear from shortage detection. The later
stock update also skips missing products, then the bill is still marked paid.

**Reproduction:**

1. Add a product to an active bill.
2. Delete the product from Inventory.
3. Pay the bill.
4. Revenue/profit include the item, but no stock is deducted.

**Impact:** Inventory and financial records diverge silently.

**Fix:** Block deletion while a product is referenced by an active bill. Payment
must fail with a typed inconsistency result if any referenced product is missing.

---

### HK-002 - Normal product operations can fail without usable feedback

**Evidence:**

- Unique SKU/barcode constraints: `app/src/main/java/com/amg/hisabkitab/data/local/Entities.kt:12-15`
- Restricted loss foreign key: `app/src/main/java/com/amg/hisabkitab/data/local/Entities.kt:63-71`
- Fire-and-forget product writes: `app/src/main/java/com/amg/hisabkitab/ui/viewmodel/ShopViewModels.kt:79-83`
- Dialogs close immediately: `app/src/main/java/com/amg/hisabkitab/ui/screens/InventoryScreen.kt:152-163`

Duplicate SKU/barcode saves and deletion of a product with loss history raise
Room/SQLite exceptions. The launched coroutine has no error state, while the
editor or confirmation UI closes immediately. Users can believe an operation
succeeded or get an uncaught coroutine exception without an actionable message.

**Impact:** Common inventory maintenance workflows can fail unclearly and leave
users unsure whether data changed.

**Fix:** Return typed repository results, catch constraint exceptions, keep
dialogs open until success, and show field-specific save/delete errors.

---

### HK-003 - Invalid numeric product data can corrupt calculations

**Evidence:**

- Parseability-only validation: `app/src/main/java/com/amg/hisabkitab/ui/screens/InventoryScreen.kt:259-261`
- Values persisted directly: `app/src/main/java/com/amg/hisabkitab/ui/screens/InventoryScreen.kt:301-314`
- Inexact/unchecked money conversion: `app/src/main/java/com/amg/hisabkitab/ui/common/Formatters.kt:24-25`
- No database checks: `app/src/main/java/com/amg/hisabkitab/data/local/Entities.kt:16-26`
- Multiplication without overflow checks: `app/src/main/java/com/amg/hisabkitab/ui/viewmodel/ShopViewModels.kt:60-72`

Negative prices, stock, and thresholds are accepted when pasted or entered with
a hardware keyboard. Excess decimal places are truncated by `toLong()` rather
than rejected. Large values can overflow stock addition, inventory value, line
totals, profit, loss totals, and item quantity increments.

**Impact:** Inventory value, profit, low-stock status, and bill totals can become
incorrect or nonsensical.

**Fix:** Enforce non-negative bounded values in the domain/repository layer, use
exact money conversion with scale checks, use checked arithmetic, and add
database constraints where Room supports them.

---

### HK-004 - Loss quantity can exceed stock and overstate financial loss

**Evidence:**

- UI accepts any positive quantity: `app/src/main/java/com/amg/hisabkitab/ui/screens/AnalyticsScreen.kt:253-309`
- ViewModel checks only positivity: `app/src/main/java/com/amg/hisabkitab/ui/viewmodel/ShopViewModels.kt:260-263`
- DAO records full loss but clamps stock: `app/src/main/java/com/amg/hisabkitab/data/local/HisabKitabDao.kt:196-221`

Recording a loss of 10 when stock is 2 records loss for 10 units while inventory
decreases by only 2 units.

**Impact:** Financial loss analytics can be inflated without a matching physical
inventory movement.

**Fix:** Reject losses above available stock. Add a separate, explicit stock
correction workflow for physical-count discrepancies.

---

### HK-005 - Custom analytics ranges use wrong India-local boundaries

**Evidence:**

- Date-picker UTC values are forwarded directly: `app/src/main/java/com/amg/hisabkitab/ui/screens/AnalyticsScreen.kt:196-215`
- A fixed 24 hours is added: `app/src/main/java/com/amg/hisabkitab/ui/viewmodel/ShopViewModels.kt:256-258`
- Standard periods use `Asia/Kolkata`: `app/src/main/java/com/amg/hisabkitab/ui/viewmodel/ShopViewModels.kt:265-281`

Material date-picker values represent UTC date boundaries. A selected custom
date therefore starts at 05:30 IST, excluding the first 5.5 hours of the selected
start date and including 5.5 hours after the selected end date.

**Impact:** Custom sales and loss reports are wrong around every selected day
boundary for India-local users.

**Fix:** Convert picker values to `LocalDate`, then calculate `[start, end)`
instants with `ZoneId.of("Asia/Kolkata")`.

---

### HK-006 - Backup export is not a consistent database snapshot

**Evidence:** `app/src/main/java/com/amg/hisabkitab/data/repository/BackupCodec.kt:16-25`

Products, bills, bill items, losses, and settings are read using five
independent queries. A payment, loss, deletion, or edit between reads can combine
records from different database states.

**Impact:** A backup can contain pre-payment stock with a post-payment bill, or
broken references that its own restore validator rejects.

**Fix:** Read all backup tables inside one Room transaction/snapshot and test
export while concurrent writes are attempted.

---

### HK-007 - Bill edits can race with payment and corrupt status or stock

**Evidence:**

- Repository read/modify/write operations are not transactional:
  `app/src/main/java/com/amg/hisabkitab/data/repository/ShopRepository.kt:62-84`
- Customer updates run on every keystroke:
  `app/src/main/java/com/amg/hisabkitab/ui/screens/BillsScreens.kt:230-241`
- Payment is a separate DAO transaction:
  `app/src/main/java/com/amg/hisabkitab/data/local/HisabKitabDao.kt:159-193`
- Item add/update/delete does not verify bill status:
  `app/src/main/java/com/amg/hisabkitab/data/local/HisabKitabDao.kt:140-157`

`updateCustomer` reads a complete active `BillEntity`, then later writes that
stale copy. Payment or cancellation can execute between those operations. The
stale update can change a paid bill back to `ACTIVE`, or cancellation can
overwrite a just-paid bill as `CANCELLED` after stock has already been reduced.
Item addition, quantity changes, and removal also do not enforce active status
below the UI.

Payment returns `Success` for missing or non-active bills, so callers cannot
distinguish completed payment from a conflict or no-op.

**Impact:** Bill status and inventory can diverge under rapid UI actions,
process replays, or future multi-window/background flows.

**Fix:** Move every bill mutation into DAO transactions that perform conditional
SQL updates requiring `status = ACTIVE`. Update only intended columns,
serialize payment/cancellation against edits, and return typed not-found or
status-conflict results.

## Medium-Severity Findings

### HK-008 - Payment warning state leaks across bill screens

**Evidence:**

- Warning state is global to `BillsViewModel`: `app/src/main/java/com/amg/hisabkitab/ui/viewmodel/ShopViewModels.kt:97-102`
- Every detail screen receives the same shortage list:
  `app/src/main/java/com/amg/hisabkitab/ui/navigation/HisabKitabApp.kt:133-148`

After a shortage warning, navigating back and opening another bill can show the
old warning. Confirming it pays the original pending bill and pops the currently
displayed screen.

**Fix:** Scope the warning to the displayed bill ID, clear it when leaving the
bill, and reject force payment unless displayed and pending IDs match.

---

### HK-009 - Forced payment discards the stock discrepancy

**Evidence:**

- Stock is clamped to zero: `app/src/main/java/com/amg/hisabkitab/data/local/HisabKitabDao.kt:175-183`
- The README expects later reconciliation of stock mismatches.

The missing quantity is not stored after "Mark Paid Anyway", so the discrepancy
cannot be explained or reconciled later.

**Fix:** Persist a discrepancy record containing bill, product, expected stock,
sold quantity, deficit, and timestamp.

---

### HK-010 - Restore validation permits destructive or invalid states

**Evidence:**

- Limited validation: `app/src/main/java/com/amg/hisabkitab/data/repository/BackupCodec.kt:27-44`
- Restore inserts use `REPLACE`: `app/src/main/java/com/amg/hisabkitab/data/local/HisabKitabDao.kt:93-103`

Restore does not reject duplicate item/loss IDs, duplicate SKU/barcodes,
non-positive IDs, negative values, invalid timestamps, multiple settings rows,
or invalid bill states such as `PAID` without payment metadata. `REPLACE` can
silently delete or overwrite rows when a supposedly validated backup conflicts.

**Fix:** Validate every entity and cross-entity invariant before clearing data,
require unique positive IDs and business keys, and use aborting inserts.

---

### HK-011 - Large backup and restore operations can freeze the UI

**Evidence:**

- Backup string is written outside the IO context:
  `app/src/main/java/com/amg/hisabkitab/MainActivity.kt:32-38`
- Restore parsing and validation run after the IO read:
  `app/src/main/java/com/amg/hisabkitab/MainActivity.kt:51-60`
- JSON is fully materialized in memory:
  `app/src/main/java/com/amg/hisabkitab/data/repository/BackupCodec.kt:16-44`

Large backups can block the main thread during output writing, JSON parsing, and
validation. Oversized selected files can also cause substantial memory use.

**Fix:** Run read, parse, validate, encode, and write on `Dispatchers.IO`; use
streaming where practical and enforce file-size/entity-count limits.

---

### HK-012 - Bill numbers are collision-prone and not unique

**Evidence:**

- Process-local sequence: `app/src/main/java/com/amg/hisabkitab/data/repository/ShopRepository.kt:20,38-47`
- No unique bill-number constraint: `app/src/main/java/com/amg/hisabkitab/data/local/Entities.kt:29-39`

The timestamp component repeats every 100 seconds. The one-digit sequence wraps
every ten bills and resets after process death, so duplicate invoice numbers are
possible and accepted by the database.

**Fix:** Generate bill numbers from a database-backed sequence or inserted bill
ID and enforce uniqueness in the schema.

---

### HK-013 - Unknown scanned barcodes fail silently

**Evidence:**

- Repository returns match status: `app/src/main/java/com/amg/hisabkitab/data/repository/ShopRepository.kt:56-60`
- Navigation ignores status and always closes scanner:
  `app/src/main/java/com/amg/hisabkitab/ui/navigation/HisabKitabApp.kt:151-166`

When scanning into a bill, an unknown barcode closes the scanner without adding
an item or explaining why.

**Fix:** Keep the scanner open on no match and offer manual search or product
creation with a visible message.

---

### HK-014 - Scanner can fail without a usable back camera

**Evidence:**

- Camera hardware is optional: `app/src/main/AndroidManifest.xml:5-7`
- Provider acquisition and binding are unguarded:
  `app/src/main/java/com/amg/hisabkitab/ui/scanner/BarcodeScannerScreen.kt:109-142`

`cameraProviderFuture.get()` or `bindToLifecycle` can throw when the back camera
is absent, occupied, unavailable, or disabled by policy. Permanent permission
denial also provides no manual-entry or settings path.

**Fix:** Check camera availability, catch provider/binding failures, and provide
manual barcode entry plus a permission-settings action.

---

### HK-015 - Settings present features that are not implemented

**Evidence:** `app/src/main/java/com/amg/hisabkitab/ui/screens/SettingsScreen.kt:127-154`

Notification and PIN switches only persist booleans. There is no notification
scheduling, notification permission flow, PIN setup, or PIN enforcement.
Appearance and support rows are clickable but do nothing.

**Fix:** Hide or clearly label unfinished controls, or implement the complete
workflows before release.

---

### HK-016 - Android platform backup lacks an explicit financial-data policy

**Evidence:**

- `app/src/main/AndroidManifest.xml:12-20`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`

`android:allowBackup="true"` is enabled while the rules remain templates. Shop
financial records may be copied through cloud backup or device transfer outside
the app's explicit file-picker backup workflow.

**Fix:** Deliberately disable platform backup or define and test exact
include/exclude rules.

---

### HK-017 - Time-based summaries do not roll over without a data emission

**Evidence:**

- Day start is calculated only inside the combined-flow transform:
  `app/src/main/java/com/amg/hisabkitab/ui/viewmodel/ShopViewModels.kt:210-221`
- Month boundaries are calculated only on source/period emissions:
  `app/src/main/java/com/amg/hisabkitab/ui/viewmodel/ShopViewModels.kt:224-281`

If the app remains open across midnight, Home can continue showing yesterday's
sales until a bill, product, setting, or selected period changes. The same issue
applies across a month boundary. Today's filter also has no exclusive next-day
upper bound, so future-dated restored records can appear in today's totals.

**Fix:** Add a lifecycle-aware clock/day flow that emits at local midnight,
calculate bounded `[start, end)` periods, and recompute on resume.

---

### HK-018 - Sales trend removes no-sale days and misrepresents spacing

**Evidence:**

- Revenue is grouped only for dates containing paid bills:
  `app/src/main/java/com/amg/hisabkitab/ui/viewmodel/ShopViewModels.kt:235-240`
- The chart draws compact values as equally spaced bars:
  `app/src/main/java/com/amg/hisabkitab/ui/screens/AnalyticsScreen.kt:220-239`

For sales on June 1 and June 10 only, the chart receives two values and renders
them as adjacent bars. It contains neither zero-value dates nor date labels, so
the trend does not represent the selected period's actual timeline.

**Fix:** Produce a complete date-indexed series for every day in the selected
range, filling missing dates with zero, and render date context on the chart.

## Feature Gaps Against README / Product Expectations

- No `Today` analytics filter.
- No items-sold or average-bill metrics.
- No PDF or CSV exports.
- No Home recent-bills section.
- No category model/filter for Inventory.
- No restock or stock-adjustment history.
- No actual low-stock notification workflow.
- No actual PIN security workflow.

## Test Coverage Gaps

Current JVM coverage is four tests. The Android test APK builds, and one
instrumented test exercises the normal payment path, but the risky paths below
are uncovered:

1. Product deletion while referenced by active bills and losses.
2. Missing-product payment.
3. Duplicate SKU/barcode errors without app termination.
4. Edit/payment and cancel/payment races.
5. Paid/cancelled bill immutability.
6. IST custom-date boundaries and midnight/month rollover.
7. Sparse daily chart ranges with zero-sale dates.
8. Loss quantity greater than stock.
9. Negative, oversized, excess-scale, and overflowing values.
10. Forced-payment discrepancy persistence.
11. Concurrent backup consistency and malformed restore files.
12. Bill-number uniqueness after process/repository recreation.
13. Cross-bill shortage-warning state.
14. Unknown barcode, denied permission, and unavailable-camera behavior.

## Lint and Build Notes

Lint reported 22 warnings and no errors:

- 18 dependency/version warnings.
- 1 newer Kotlin version warning.
- 1 old target API warning.
- 1 redundant manifest label.
- 1 unused resource.

The compiler/build output also reported an experimental
`android.disallowKotlinSourceSets=false` option and the sandboxed Android
analytics-settings write warning. These are lower priority than the runtime and
data-integrity findings above.

## Suggested Fix Order

1. Lock down bill lifecycle transitions:
   - Conditional DAO updates requiring `status = ACTIVE`.
   - Typed `PaymentResult` values for success, not found, invalid status,
     missing product, and insufficient stock.
   - Tests for concurrent edit/payment/cancel behavior.

2. Lock down product and inventory invariants:
   - Domain/repository validation for money, quantities, thresholds, and
     overflow.
   - Product deletion checks for active bill references and loss history.
   - Visible UI errors for duplicate SKU/barcode and restricted deletion.

3. Fix analytics correctness:
   - `LocalDate`-based custom ranges in `Asia/Kolkata`.
   - Bounded Home "today" windows and lifecycle midnight refresh.
   - Complete date series for trend charts.

4. Harden backup/restore:
   - Transactional backup snapshot.
   - Full restore validation before destructive replace.
   - IO dispatching and size/entity limits.

5. Clean up release UX/policy gaps:
   - Scanner failure handling and unknown-barcode feedback.
   - Implement or hide notification/PIN/appearance/support controls.
   - Decide Android platform backup policy.
