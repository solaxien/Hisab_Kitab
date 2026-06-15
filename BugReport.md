# HisabKitab Bug Report

**Audit date:** 2026-06-15  
**Branch:** `main`  
**Baseline:** commit `7b219ca` plus the current uncommitted working tree  
**Recommendation:** Do not ship until the High-severity data-integrity issues are fixed.

## Executive Summary

The app compiles, unit tests pass, lint reports no errors, and both debug and
unsigned release APKs build. The main risks are runtime failures and silent
financial/inventory corruption that are not covered by the current tests.

| Severity | Count |
|---|---:|
| Critical | 0 |
| High | 6 |
| Medium | 10 |

Priority order:

1. Protect active bills from product deletion and missing-product payment.
2. Add domain validation and typed error handling for every write operation.
3. Make backup export a single database snapshot and strengthen restore validation.
4. Correct custom analytics day boundaries.
5. Preserve forced-payment stock discrepancies for reconciliation.

## Verification Performed

- `testDebugUnitTest`: passed, 4 tests, 0 failures.
- `lintDebug`: passed, 0 errors and 28 warnings.
- `assembleDebug`: passed; generated `app/build/outputs/apk/debug/app-debug.apk`.
- `compileDebugAndroidTestKotlin`: passed.
- `assembleRelease`: passed; generated
  `app/build/outputs/apk/release/app-release-unsigned.apk`.
- `git diff --check`: no whitespace errors.

`assembleDebugAndroidTest` could not complete because the sandbox could not lock
`C:\Users\amogh\.android\debug.keystore.lock`. Instrumented tests were compiled
but not executed because no emulator/device run was available.

## High-Severity Findings

### HK-001 - Deleting a product can make a paid bill skip stock deduction

**Evidence:**

- `app/src/main/java/com/amg/hisabkitab/data/local/Entities.kt:41-60`
- `app/src/main/java/com/amg/hisabkitab/data/local/HisabKitabDao.kt:30-31`
- `app/src/main/java/com/amg/hisabkitab/domain/model/BusinessRules.kt:13-20`
- `app/src/main/java/com/amg/hisabkitab/data/local/HisabKitabDao.kt:169-193`

`bill_items.productId` has no foreign key to products. A product referenced by
an active bill can therefore be deleted. Shortage detection omits missing
products, payment skips their inventory update, and the bill is still marked
paid.

**Reproduction:**

1. Add a product to an active bill.
2. Delete the product from Inventory.
3. Pay the active bill.
4. Revenue/profit include the sale, but no product stock is reduced.

**Fix:** Block product deletion while referenced by an active bill, and make
payment fail with a typed inconsistency whenever any bill product is missing.

---

### HK-002 - Normal product operations can crash or fail without feedback

**Evidence:**

- Unique SKU/barcode constraints:
  `app/src/main/java/com/amg/hisabkitab/data/local/Entities.kt:12-15`
- Restricted deletion after loss history:
  `app/src/main/java/com/amg/hisabkitab/data/local/Entities.kt:63-71`
- Unhandled ViewModel writes:
  `app/src/main/java/com/amg/hisabkitab/ui/viewmodel/ShopViewModels.kt:79-83`

Saving a duplicate SKU/barcode or deleting a product referenced by a loss entry
raises a Room/SQLite exception. The dialogs close before the operation completes,
and no error state catches or explains the failure.

**Fix:** Return typed repository results, catch constraint exceptions, keep the
dialog open, and display actionable field/delete errors.

---

### HK-003 - Invalid product values can corrupt all calculations

**Evidence:**

- Parseability-only validation:
  `app/src/main/java/com/amg/hisabkitab/ui/screens/InventoryScreen.kt:226-228`
- Values persisted directly:
  `app/src/main/java/com/amg/hisabkitab/ui/screens/InventoryScreen.kt:258-275`
- Unsafe money conversion:
  `app/src/main/java/com/amg/hisabkitab/ui/common/Formatters.kt:24-25`
- Database schema has no value checks:
  `app/src/main/java/com/amg/hisabkitab/data/local/Entities.kt:16-26`

Negative prices, stock, and thresholds are accepted when pasted or entered with
a hardware keyboard. Large values can overflow `Int` stock or `Long`
price/total calculations, while extra decimal places are silently truncated.

**Impact:** Inventory value, bill totals, profit, loss, and stock alerts can
become negative or nonsensical.

**Fix:** Enforce non-negative bounded values in the domain/repository layer,
perform exact money conversion, reject excess scale, and add database checks.

---

### HK-004 - Loss quantity can exceed stock and overstate financial loss

**Evidence:**

- UI accepts any positive quantity:
  `app/src/main/java/com/amg/hisabkitab/ui/screens/AnalyticsScreen.kt:231-252`
- ViewModel checks only positivity:
  `app/src/main/java/com/amg/hisabkitab/ui/viewmodel/ShopViewModels.kt:252-255`
- DAO records the full loss but clamps stock:
  `app/src/main/java/com/amg/hisabkitab/data/local/HisabKitabDao.kt:197-221`

Recording a loss of 10 when stock is 2 charges analytics for 10 units while
inventory decreases by only 2.

**Fix:** Reject loss above available stock. Use a separate explicit stock
correction workflow when the physical count differs from recorded stock.

---

### HK-005 - Custom analytics ranges use the wrong day boundaries

**Evidence:**

- Picker UTC millis are forwarded directly:
  `app/src/main/java/com/amg/hisabkitab/ui/screens/AnalyticsScreen.kt:173-185`
- A fixed 24 hours is added:
  `app/src/main/java/com/amg/hisabkitab/ui/viewmodel/ShopViewModels.kt:247-250`
- Standard periods use `Asia/Kolkata`:
  `app/src/main/java/com/amg/hisabkitab/ui/viewmodel/ShopViewModels.kt:257-273`

Material date-picker values represent UTC date boundaries. For an India-local
report, a selected date starts at 05:30 IST, excluding the first 5.5 hours and
including 5.5 hours from after the selected end date.

**Fix:** Convert picker values to `LocalDate`, then calculate start and exclusive
end instants using `ZoneId.of("Asia/Kolkata")`.

---

### HK-006 - Backup export is not a consistent database snapshot

**Evidence:** `app/src/main/java/com/amg/hisabkitab/data/repository/BackupCodec.kt:16-25`

Products, bills, bill items, losses, and settings are read using five independent
queries. A payment or edit between those reads can combine pre-operation stock
with a post-operation paid bill, or produce broken references.

**Impact:** A backup can restore successfully while carrying incorrect stock, or
can fail its own reference validation.

**Fix:** Read every backup table inside one Room transaction/snapshot and add a
consistency test that performs concurrent writes during export.

## Medium-Severity Findings

### HK-007 - Paid and cancelled bills are mutable below the UI layer

**Evidence:** `app/src/main/java/com/amg/hisabkitab/data/repository/ShopRepository.kt:51-77`

Adding/removing items, changing quantities, and changing the customer do not
verify that the bill is `ACTIVE`. UI controls are hidden, but stale callbacks,
races, tests, or future callers can still alter historical bills.

**Fix:** Enforce bill status transactionally in every DAO mutation.

---

### HK-008 - Payment warning state leaks across bill screens

**Evidence:**

- Warning and pending payment are global ViewModel state:
  `app/src/main/java/com/amg/hisabkitab/ui/viewmodel/ShopViewModels.kt:97-103`
- Every detail screen receives the same shortages:
  `app/src/main/java/com/amg/hisabkitab/ui/navigation/HisabKitabApp.kt:125-139`

After a shortage warning, navigating back and opening another bill shows the old
warning there. Confirming it pays the original pending bill and pops the current
screen.

**Fix:** Scope warning state to the current bill ID, clear it on navigation, and
require the displayed bill ID to match `pendingBillId`.

---

### HK-009 - Forced payment discards the stock discrepancy

**Evidence:**

- Stock is clamped to zero:
  `app/src/main/java/com/amg/hisabkitab/data/local/HisabKitabDao.kt:175-183`
- Required reconciliation record:
  `README.md:154-163`

The missing quantity is not stored after "Mark Paid Anyway", so the user cannot
later explain or reconcile the adjustment.

**Fix:** Persist a stock discrepancy entry with bill, product, expected stock,
sold quantity, deficit, and timestamp.

---

### HK-010 - Restore validation permits silent replacement and invalid states

**Evidence:**

- Limited validation:
  `app/src/main/java/com/amg/hisabkitab/data/repository/BackupCodec.kt:27-44`
- Restore uses `REPLACE`:
  `app/src/main/java/com/amg/hisabkitab/data/local/HisabKitabDao.kt:93-103`

Duplicate bill-item/loss IDs, duplicate SKU/barcode values, non-positive IDs,
negative quantities/prices, multiple settings records, and invalid bill states
such as `PAID` without payment metadata are not fully rejected.

**Fix:** Validate every entity and invariant before replacement, require unique
positive IDs, and use aborting inserts for validated historical records.

---

### HK-011 - Large restore files are parsed on the main thread

**Evidence:**

- Only file reading is dispatched to IO:
  `app/src/main/java/com/amg/hisabkitab/MainActivity.kt:47-55`
- JSON parsing occurs before DAO suspension:
  `app/src/main/java/com/amg/hisabkitab/data/repository/BackupCodec.kt:27-44`

Large or malicious backup JSON can freeze the UI or cause memory pressure.

**Fix:** Move decode, validation, and replacement to `Dispatchers.IO`, and limit
file size and entity counts.

---

### HK-012 - Bill numbers are collision-prone

**Evidence:**

- Process-local one-digit sequence:
  `app/src/main/java/com/amg/hisabkitab/data/repository/ShopRepository.kt:20,38-47`
- No unique bill-number constraint:
  `app/src/main/java/com/amg/hisabkitab/data/local/Entities.kt:29-39`

The timestamp suffix repeats every 100 seconds and the sequence wraps every ten
bills and resets after process death.

**Fix:** Generate bill numbers from a database-backed sequence or inserted bill
ID and enforce uniqueness.

---

### HK-013 - Unknown scanned barcodes fail silently

**Evidence:**

- Repository returns match status:
  `app/src/main/java/com/amg/hisabkitab/data/repository/ShopRepository.kt:56-60`
- Navigation ignores it and always closes:
  `app/src/main/java/com/amg/hisabkitab/ui/navigation/HisabKitabApp.kt:149-156`

**Fix:** Keep the scanner open and show an unknown-barcode action for manual
search or product creation.

---

### HK-014 - Scanner can crash without a usable back camera

**Evidence:**

- Camera hardware is optional:
  `app/src/main/AndroidManifest.xml:5-7`
- Provider acquisition and binding are unguarded:
  `app/src/main/java/com/amg/hisabkitab/ui/scanner/BarcodeScannerScreen.kt:109-142`

`cameraProviderFuture.get()` or `bindToLifecycle` can throw when a camera is
missing, occupied, or unavailable.

**Fix:** Check camera availability, catch provider/binding failures, and provide
manual barcode entry.

---

### HK-015 - Settings claim features that are not implemented

**Evidence:** `app/src/main/java/com/amg/hisabkitab/ui/screens/SettingsScreen.kt:88-126`

Notification and PIN switches only persist booleans. No notification scheduling,
permission workflow, PIN setup, or PIN enforcement exists. Appearance and app
information rows also have no behavior.

**Fix:** Hide or label unfinished controls, or implement the complete workflows.

---

### HK-016 - Android platform backup is enabled without explicit data policy

**Evidence:**

- `app/src/main/AndroidManifest.xml:12-20`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`

The rules remain templates while `android:allowBackup="true"`. Shop financial
records may be copied by cloud backup/device transfer outside the app's explicit
file-picker workflow.

**Fix:** Deliberately disable platform backup or define and test precise
include/exclude rules.

## Feature Gaps Against README

- No `Today` analytics filter.
- No items-sold or average-bill metrics.
- No PDF or CSV exports.
- No Home recent-bills section.
- No category model/filter for Inventory.
- No restock/stock-adjustment history.
- No actual low-stock notification workflow.
- No actual PIN security workflow.

## Test Coverage Gaps

Current coverage is four JVM tests and two instrumented tests; only one
instrumented test exercises the normal payment path. Add tests for:

1. Product deletion while referenced by active bills and losses.
2. Missing-product payment.
3. Duplicate SKU/barcode errors without app termination.
4. Paid/cancelled bill immutability.
5. IST custom-date boundaries.
6. Loss greater than stock.
7. Negative, oversized, and overflowed values.
8. Forced-payment discrepancy persistence.
9. Concurrent backup consistency and malformed restore files.
10. Bill-number uniqueness after repository/process recreation.
11. Cross-bill shortage-warning state.
12. Unknown barcode and unavailable-camera behavior.

## Lint Notes

Lint reported 28 warnings and no errors. Warnings are mainly dependency/SDK
update notices, one redundant activity label, deprecated API usage, and unused
resources. These are lower priority than the findings above.
