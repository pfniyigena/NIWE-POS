# NIWE ERP, version 1.22.0

Assets: register search and files, cost and value changes, custody and verification, overview and reports. Versions 1.0.0 to 1.21.0 stay the base. One migration (V61). Four new pages (*Assets > Overview*, *Verification*, *Settings* and *Print tags*), added by `tools/wire-v220.py`.

## What changed

### Register, search and files
1. **Search, filters and views.** The register searches tag, name, serial number, holder and place; filters by category, status, branch, place and holder; and has quick views: Held by someone, Nobody holding, Warranty ending, Insurance ending, Warranty or insurance ended, Fully depreciated soon. It pages 25 at a time. *Excel* and *PDF* download exactly what is on screen.
2. **Warranty and insurance.** Each asset can have a warranty end date, an insurance end date and a policy number. The register shows "ending" and "ended" badges, the asset page shows a banner, and the overview and the expiry report list what is coming up (30 days by default, set in *Assets > Settings*).
3. **Photos and files.** Photos, PDF and text files up to 10 MB, 20 per asset, checked by content, with thumbnails on the asset page.
4. **Tags.** *Print tag* (one asset) and *Print tags* (the ticked assets on the register) make a sheet of labels with the tag number and a QR code that opens the asset.
5. **Activity.** Every asset keeps a history: registered, edited, moved, handed over or taken back, improved, written down, terms changed, depreciation taken back, files, verified, disposed and disposal undone, with who and when.
6. **Several at once.** Tick assets on the register to move them (branch and/or place), hand them out or take them back (up to 200 at a time; any that cannot be done are listed with the reason, the rest go through).

### Cost and value changes
7. **Improvements.** Money spent that adds value or life is added to the cost (already recorded, paid in cash, or on credit). Depreciation is spread again over the remaining life you give (or the current one) from the month after the date.
8. **Write-downs (impairment).** Lowers the book value by an amount; Accounting gets Dr Depreciation Expense, Cr Accumulated Depreciation.
9. **New terms.** Change the remaining life, salvage value or method after depreciation has started. Months already charged do not change.
10. **Take back the last month.** Removes the last month of depreciation and posts a reversing entry. Refused if an improvement, write-down or new terms is dated after that month.
11. **Undo a disposal.** Posts a reversing entry and makes the asset active again, with its cost and depreciation as they were. Administrators and users with *Approve large asset disposals* only.
12. **Category accounts.** A category can have its own asset, accumulated depreciation and depreciation expense accounts (for example separate accounts for vehicles and computers). Empty means the standard accounts, as before. They can only be changed while the category has no assets. Monthly depreciation is posted per category when it has its own accounts.

### Custody and verification
13. **Who holds it.** Hand an asset to a user or to a name, with date and condition; take it back with the condition it came back in. The asset page keeps the full list and prints a **handover sheet (PDF)**. Editing "Held by" on the asset form does the same hand-over or take-back. A disposed asset is released automatically.
14. **Verification rounds** (*Assets > Verification*). Start a round for a branch, a category or a place; it lists the assets that should be there. Mark each one Found (optionally where), Missing or Damaged, or mark everything left as found or missing. A round cannot be closed while assets are unchecked; closing can move assets to where they were found. A missing asset can be **written off** from the round (it is disposed of with no proceeds, so the disposal approval rule applies). Open rounds can be deleted.
15. **Disposal approval.** In *Assets > Settings* set a book-value limit (0 = no limit). Disposing of a more valuable asset needs an administrator or a user with the new permission *Approve large asset disposals*.

### Overview and reports
16. **Overview** (*Assets > Overview*): assets, cost, accumulated depreciation, book value, next month's depreciation, fully depreciated and disposed counts, assets nobody holds, open verification rounds, by category and by branch, and lists for warranty ending, insurance ending and fully depreciated soon. It warns when depreciation is behind.
17. **Reports** (screen, Excel, PDF, and as scheduled reports): *Asset Register* (now with serial number, warranty and insurance dates and the same filters as the list), *Assets by Location*, *Assets by Holder*, *Warranty and Insurance Expiry*, *Depreciation Forecast* (months ahead, assuming nothing is bought or sold), *Fixed Asset Movement* (opening cost, additions, improvements, disposals, closing cost and the same for depreciation, per category and period) and *Asset Verification* (one round).

## Technical

- Migration `V61__assets_v122.sql`: `fixed_asset` gains warranty and insurance dates, policy, custodian (user and name), checked-out date, monthly charge and rate life (for re-based depreciation), original cost (backfilled) and disposal-undo count; `asset_category` gains three optional account links; `depreciation_entry` gains the category; new tables `asset_adjustment`, `asset_custody` (existing "assigned to" values are carried over), `asset_attachment`, `asset_event`, `asset_settings` (default 30 days, 3 months, no limit), `asset_verification` and `asset_verification_line`; the user permission check now also allows `APPROVE_ASSET_DISPOSAL`.
- Backend: `FixedAssetService`, `AssetDepreciationService`, `AssetPurchaseService` reworked; new `AssetValueService`, `AssetCustodyService`, `AssetAttachmentService`, `AssetHistoryService`, `AssetVerificationService`, `AssetBulkService`, `AssetActionService`, `AssetOverviewService`, `AssetSettingsService`; `AccountingService` asset postings take per-category accounts and add improvement, impairment and the two reversing entries. New endpoints under `/api/assets` (list filters, `/overview`, `/settings`, `/custodians`, `/bulk`, `/verifications`, and per asset `/events`, `/adjustments`, `/improvement`, `/impairment`, `/terms`, `/depreciation/reverse`, `/undo-disposal`, `/custody`, `/check-out`, `/check-in`, `/handover-pdf`, `/files`); reports `/api/reports/assets-by-location|assets-by-custodian|assets-expiry|assets-forecast|assets-movement|assets-verification`, and `asset-register` takes status, category, search, place, holder and view.
- Frontend: new overview, settings, tags, verification list and detail pages; rewritten register and asset pages; category page with accounts; asset form with warranty and insurance; the report viewer gains report-specific filters (`extraFilters`); `asset-api.service`, `models`, `report-defs`, user form and list (new permission). `wire-v220.py` adds the routes and the menu entries.
- Tests: `t111` (API, 221 checks), `walk95` (browser, 96 checks); full regression on the frozen tree.

## Not in this version

Upward revaluation, units-of-production depreciation, barcode scanner input, linking custody to an HR employee record, maintenance cost on the overview, and approval chains for disposals.
