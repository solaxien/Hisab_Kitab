# HisabKitab

HisabKitab is an offline-first Android shop-management app for a small dairy/Nandini shop. It is meant to replace the shopkeeper's daily handwritten hisaab-kitaab with a fast, readable, local mobile system for billing, stock, payments, profit, loss, reports, and backup.

The first version is intentionally focused on one real shopkeeper and one Android phone. It should be simple enough to use during busy counter hours and reliable enough to trust as the shop's daily record book.

## Product Goal

Digitize one shopkeeper's daily records:

- Products and current stock
- Active, paid, and cancelled bills
- Cash and UPI payment tracking
- Stock restocking
- Barcode/search-based item lookup
- Profit and loss calculations
- Daily, monthly, and custom analytics
- PDF/CSV exports
- Local or Google Drive file-picker backup and restore

The app should assist the shopkeeper, not control the shopkeeper. For example, low-stock warnings should guide the user, but the user should still be able to complete a sale if physical stock is available.

## Current Project Status

This repository is currently an early Android Compose project shell. The README describes the intended end-state product and the business rules future implementation should preserve.

Current stack in the project:

- Android app module
- Kotlin
- Jetpack Compose
- Material 3
- minSdk 24

Intended future direction:

- Kotlin Multiplatform where useful
- Android-first v1
- SQLDelight or another local SQLite-backed persistence layer
- Kotlinx Serialization for backup/import data
- Kotlinx DateTime for shared date/time logic
- CameraX + ML Kit for Android barcode scanning
- Android Storage Access Framework for backup/restore

## Navigation

The bottom navigation should contain:

1. Home
2. Analytics
3. Bills
4. Inventory

Settings should not be a bottom navigation item.

Settings-style actions such as backup, restore, exports, notifications, shop info, and app info can live behind a top-bar menu, overflow action, profile/info screen, or another non-bottom-nav entry point.

## Core Screens

### Home

Home is the daily counter screen. It should feel fast, positive, and operational.

Home should show:

- Today's sales
- Cash collected
- UPI collected
- Active bills preview
- Low-stock alerts
- Recent bills
- Create Bill floating action button

Home should not show loss, net profit, expired-stock loss, or heavy analytics by default.

### Analytics

Analytics is the business review area.

Analytics should include:

- Date filters: Today, This Month, Custom Range
- Total sales
- Number of bills
- Items sold
- Average bill value
- Cash total
- UPI total
- Gross revenue
- Gross profit
- Loss summary
- Add loss entry
- Loss history
- Net profit
- PDF/CSV export options

Loss entry belongs in Analytics for v1.

### Bills

Bills manages all bill records.

Bill statuses:

- `ACTIVE`
- `PAID`
- `CANCELLED`

Active bills are held and editable. Creating a bill should immediately create an `ACTIVE` bill, even before items are added. Pressing back should leave the bill active rather than deleting it.

Paid bills are completed sales. They should be locked in v1, included in analytics, and should reduce inventory stock only at payment time.

Cancelled bills should not affect stock, sales, or analytics. Prefer marking bills as `CANCELLED` instead of hard deleting them.

### Inventory

Inventory manages products, item lookup, stock, restocking, and low-stock indicators.

Inventory should include:

- Product search
- Barcode search/scanner
- Category filters
- Sort/filter options
- Product cards
- Add item action
- Restock action
- Edit item action
- Low-stock indicators

Changing a product price must not change old bills. Bill items should store price snapshots from the time of billing.

## Important Business Rules

- The app must work offline.
- No Firebase, Supabase, backend server, or cloud database in v1.
- Local database is the source of truth.
- Backup and restore should use the Android file picker.
- Create Bill creates an `ACTIVE` bill immediately.
- Active bills are editable.
- Back does not delete active bills.
- Active bills do not reduce stock.
- Stock reduces only when a bill is marked paid.
- Payment mode must be Cash or UPI.
- If recorded stock is lower than the bill quantity, warn before payment.
- The user can still mark paid anyway.
- Paid bills are locked in v1.
- Cancelled bills do not affect stock or analytics.
- Loss entry belongs in Analytics.
- Loss should not appear on Home by default.
- Product price changes should not affect old bills.
- Backup must include all bills and bill items.

## Stock and Billing Rule

Inventory stock should reduce only when a bill is marked paid, not when items are added to an active bill.

When the user marks a bill paid:

1. Check bill quantity against recorded stock.
2. If stock is enough, mark the bill `PAID`, save payment mode, reduce stock, and include it in analytics.
3. If stock is low, show a warning dialog.
4. If the user confirms, mark the bill paid anyway and record the inventory inconsistency for later correction.

## Data Model Direction

Expected core records:

- `Product`
- `Bill`
- `BillItem`
- `StockEntry`
- `LossEntry`
- `AppSettings`

Important bill item snapshots:

- `productNameAtBill`
- `purchasePriceAtBill`
- `sellingPriceAtBill`

These snapshots protect old bills from later product price edits.

## Calculations

- Line total = selling price at bill x quantity
- Line profit = (selling price at bill - purchase price at bill) x quantity
- Bill total = sum of line totals
- Bill profit = sum of line profits
- Gross revenue = sum of paid bill totals in selected date range
- Gross profit = sum of paid bill profits in selected date range
- Loss value = purchase price x quantity lost
- Net profit = gross profit - total loss
- Cash total = sum of paid bills where payment mode is Cash
- UPI total = sum of paid bills where payment mode is UPI

## Backup and Restore

HisabKitab is local-first, so backup and restore are critical.

Backup should be exported through Android's file picker to phone storage or a Drive-mounted location. The app should not use Google Drive API or OAuth in v1.

Recommended backup format:

- `metadata.json`
- `products.json`
- `bills.json`
- `bill_items.json`
- `stock_entries.json`
- `loss_entries.json`
- `settings.json`

Example backup filename:

```text
hisabkitab_backup_2026_06_07.zip
```

Restore should validate the selected backup and warn the user before replacing current app data.

## UI Direction

The app should use a dark, calm, readable Material 3 design.

Suggested palette:

- Background: `#0B1114`
- Surface: `#111A1E`
- Surface Variant: `#182428`
- Top App Bar: `#101B1F`
- Border / Divider: `#26373B`
- Primary Accent: `#4FB7A8`
- Secondary Accent: `#77C9BC`
- Muted Accent: `#2D6F67`
- Text Primary: `#E8F1EF`
- Text Secondary: `#A8B8B5`
- Success / Paid: `#65C9A8`
- Warning / Low Stock: `#C8A85A`
- Danger / Cancel: `#D06B6B`

Home should remain operational and positive. Analytics should contain deeper business truth such as profit, loss, and net profit.

## Features Not Required in v1

Do not add these unless explicitly requested later:

- Firebase Authentication
- Firestore
- Supabase
- Razorpay
- AI assistant
- Google Drive API
- Backend server
- Multi-shop support
- Employee accounts
- Customer loyalty
- GST-heavy billing
- Cloud sync
- Real-time multi-device sync
- Complex batch-wise expiry tracking
- Customer-facing app
- QR payment handoff

## Suggested Build Order

1. Theme, colors, and navigation shell
2. Local database schema
3. Product and inventory CRUD
4. Create active bill
5. Add/search/scan item into bill
6. Edit bill quantities and remove items
7. Mark paid with Cash or UPI
8. Reduce stock at payment time
9. Stock warning dialog
10. Bills history and details
11. Home summaries
12. Analytics reports
13. Loss entry and loss history
14. Backup export
15. Restore import
16. PDF/CSV exports
17. Notifications and reminders

## Development Guardrails

When modifying the app, preserve the business rules in this README. Keep the app offline-first, Android-first, and simple enough for daily shop use.

Avoid large one-file implementations. Prefer feature-based structure, reusable composables, repository/use-case separation, and shared business logic where practical.
