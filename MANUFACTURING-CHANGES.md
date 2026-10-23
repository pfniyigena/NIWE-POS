# Manufacturing module improvements (v1.10.0)

## MF-1 Lists, paging, exports and numbering
- Manufacturing orders, bills of materials, routings, workstations, indirect costs and production plans now have a search bar, filters, 25-per-page paging and Excel / PDF / CSV of the filtered list.
- Orders: search order number, product, SKU, recipe code, note; status; product; day (the scheduled day, or the day it was made when none). Recipes: search code, name, product, SKU and components; active / inactive. Routings: search name, description, step and workstation. Workstations: code, name, description. Indirect costs: code, name, allocation method. Plans: number, note, product.
- **Numbers**: new manufacturing orders are MO-0001, MO-0002... and plans PLAN-0001... (from a database sequence, so two people never get the same number). Existing numbers are kept.
- API: `GET /api/manufacturing/orders | boms | routings | workstations | overhead-costs | production-plans` accept `q, status, page, size` (plus `productId, from, to, method` where it fits) and return `X-Total-Count`; without `size` they return everything as before. Exports at `.../orders/export`, `/boms/export`, `/routings/export`, `/workstations/export`, `/overhead-costs/export`, `/production-plans/export` (`format=xlsx|pdf|csv`; Operations view).
- Database: V45 (two sequences).
- Tests: t93 (70), walk76 (27).

## MF-2 Labour and machine cost
- Each routing step now has a **labour rate per hour** (people), next to the workstation's own hourly rate (machine). Negative rates are refused.
- When an order is **confirmed**, its routing steps are copied onto the order with the rates of that moment, and a **planned cost** (material, labour, machine, overhead) is worked out. Draft orders show the same estimate from the recipe's routing.
- Every **production run** charges each step: minutes × rate ÷ 60, for labour and for machine. Minutes default to the planned minutes for the quantity produced; the person recording production can type the **actual minutes** for any step. Unknown steps and negative minutes are refused.
- Later changes to workstation or labour rates do not move a confirmed order. Orders confirmed before this release copy their steps at their first run.
- Run totals now include labour and machine; the order shows totals for each and the unit cost includes them.
- **Accounting:** each run absorbs overhead + labour + machine into Inventory against the existing Accrued Manufacturing Overhead account (no chart change).
- Fixed: the journal entry of a production run now points back at its run (it was saved without a link before).
- Migration: V46. Tests: t94 (37 checks), walk77 (12 checks).

## MF-3 Shortage check and order editing
- **Component check** on every order (draft, confirmed, partly made): for the quantity still to make it lists each component with what is needed, what is on hand (at the order's location) and what is short, plus how many units the current stock allows. The order screen shows it as a warning/green notice, and "Use what stock allows" fills the produce quantity. `GET /api/manufacturing/orders/{id}/availability`.
- **Edit an order** (`PUT /api/manufacturing/orders/{id}`, new Edit button and screen). A draft can change recipe, quantity, date, note, location and indirect costs. A confirmed or partly made order can change planned quantity, date and note; the quantity cannot go below what is already produced, and setting it to exactly what is produced closes the order. Finished and cancelled orders cannot change. The planned cost is worked out again.
- **Copy as new draft** (`POST /api/manufacturing/orders/{id}/copy`): same recipe, quantity, note, location and indirect costs, no date.
- Tests: t95 (34 checks), walk78 (19 checks). No migration.

## MF-4 Scrap, yield, by-products
- **Scrap:** when recording production, enter how many units were scrapped/rejected and why. Components and machine/labour time are used for good + scrapped units; only good units go to stock and count against the order. Each run shows its scrap quantity, reason, the cost that went into the scrap (already inside the run's cost, so it raises the good units' cost), and its **yield** (good ÷ started). The order shows total scrap, scrap cost and yield.
- **Actual component use:** optionally type the real quantity used of any component. Stock is reduced by the real figure; the run shows the recipe's quantity, the difference and its cost.
- **By-products:** a recipe can list by-products (quantity per good unit and a share of the run's cost). Each run adds them to stock and takes their cost share off the main product's unit cost; the order shows the credit.
- Recipe is refused if a by-product is the product itself, a component, listed twice, or the shares add up to more than 100%.
- Migration: V47. Tests: t96 (38 checks), walk79 (15 checks).

## MF-5 Manufacturing reports
- New **Manufacturing > Reports** page with six reports, each with a date range, an optional finished-product filter and Excel / PDF / CSV downloads: **Production runs** (every run with quantities, yield, material, labour, machine, overhead, by-product credit, total and unit cost), **Cost per product**, **Component use against recipe** (planned vs used, difference in units, % and money), **Workstation time and cost** (planned vs actual minutes, machine and labour cost), **Scrap and rejects** (by product and reason) and **Orders: plan against actual** (planned cost, planned cost of the units made so far, actual cost, difference; cancelled orders left out).
- Figures come from what each run recorded, so later price or recipe changes never move past results. Needs the Reports area (or admin); the Manufacturing module must be on.
- Tests: t97 (39 checks), walk80 (12 checks). No migration.

## MF-6 Recipe cost roll-up, copy, versions, job card
- **Cost roll-up:** every recipe shows the cost of one unit at today's prices: components (and each line's cost), labour and machine time from its routing, the total, the cost after by-product shares, the product's selling price and the margin %. Shown as columns in the recipe list and as a card on the recipe screen. Indirect costs are added per order, so they are not in this figure.
- **Versions:** recipes of a product are numbered v1, v2…. **Copy** makes the next version (inactive, same components, by-products and routing) to change without touching the one in use; **Make active** switches a version on and the product's others off. Orders already made keep the recipe they were made with. Production plans use the active version.
- **Job card:** a Print job card button on every order opens an A4 PDF: product, quantity still to make, scheduled date, location, note, components to issue with an "Issued" box, routing steps with workstation and planned minutes plus an "Actual" box, expected by-products, boxes for good/scrapped units and reason, and Made by / Checked by / Date lines.
- Migration: V48 (recipe version numbers, back-filled per product). Tests: t98 (36 checks), walk81 (13 checks).
