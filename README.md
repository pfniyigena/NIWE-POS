# POS

A standalone point-of-sale application: **Angular** frontend, **Spring Boot** REST API backend,
**PostgreSQL** database. This is a clean break from the earlier Thymeleaf-based `smb-erp` project —
it doesn't depend on that codebase or database at all, and can be developed, deployed, and scaled
independently.

## Architecture

```
pos-app/
├── backend/     Spring Boot 3 REST API (Java 21, JWT auth, JPA/Hibernate, PostgreSQL)
└── frontend/    Angular 18 SPA (standalone components, signals, no NgModules)
```

The two talk over plain JSON REST + a bearer JWT — there's no server-side rendering, no shared
templates, no coupling beyond the API contract. That means you can point a different frontend
(a native mobile app, a kiosk app, another web client) at the same backend later without touching
it, and you can redeploy either side independently.

### Backend package layout

Package-per-module, same "modular monolith" discipline as the earlier ERP project:

```
com.posapp
├── common/     BaseEntity (id + audit timestamps), ApiException, global @RestControllerAdvice
├── security/   JWT issuing/parsing, the auth filter, the Spring Security UserDetails adapter
├── config/     SecurityConfig (JWT + CORS wiring), DataSeeder (first-run demo data)
├── user/       AppUser, Role (ADMIN/CASHIER), admin-only staff account management
├── auth/       POST /api/auth/login
├── catalog/    Category, Product — search/CRUD, admin-only write + full listing/low-stock
├── customer/   Customer — list (any user)/create/update (admin)
├── register/   Register, RegisterSession (a shift/till) — open/close/report, till reconciliation;
│               CashierSession — lets more than one cashier work the same shift at once, each
│               identified by their own PIN (see "Multiple cashiers per shift" below)
├── order/      SaleOrder (a cart, and after checkout, a completed sale), OrderLine, Payment —
│               the core of the terminal: add/remove lines, discounts, barcode lookup, checkout
├── paymentmethod/  PaymentMethodEntity — admin-manageable payment/refund methods (code, name,
│               active, cash-equivalent flag); Payment.method / SalesReturn.refundMethod store its
│               `code` as a plain string, not a foreign key, so renaming/deactivating a method
│               never touches historical rows
├── returns/    SalesReturn, SalesReturnLine — look up a sale, restock, reversing refund math
├── reports/    ReportService/ReportController/ReportExportService — the seven POS Reports plus
│               Customer Statement, all built from OrderRepository directly (see the note below)
├── credit/     CustomerCreditService — the store-credit/AR subsystem (enable/limit credit,
│               checkout-time enforcement, CustomerPayment + CustomerPaymentAllocation,
│               the four POS Credit reports' underlying balance math)
├── inventory/  Suppliers, purchase orders/receiving, stock adjustments, stock counts, supplier price lists, reorder
├── warehouse/  Warehouses, locations (RECEIVING/SALES/STORAGE/DAMAGED), per-location stock, stock transfers (incl. in-transit)
├── tracking/   Batch/expiry and serial-number tracking (StockBatch, StockSerial, TrackingService)
└── dashboard/  Admin-only summary: today's sales, open sessions, active products, customers,
                low-stock list — one aggregating GET /api/dashboard/summary
```

Cross-module calls go through a service's public methods, never another module's repository —
`RegisterService` asks `OrderService` for a session's sales totals and `ReturnsService` for its
refunds total when closing a till or building a Z-report; `ReturnsService` asks `OrderService` for
the completed order it's refunding against and `CatalogService` to restock. No module reaches
past another's public API. **`reports/` and `credit/` are the deliberate exception**:
`ReportService` reads `OrderRepository`/`CustomerRepository` directly rather than going through
`OrderService`, and `CustomerCreditService` does the same, specifically so that `OrderService`
can depend on `CustomerCreditService` (for the checkout-time credit-limit check) without creating
a circular Spring bean dependency — a service is free to depend on another module's repository
read-only when that module's own service already needs to depend back on it.

### Why a cart is a `SaleOrder`, not two different things

There's one `SaleOrder` entity for both "the cart the cashier is currently building" (status
`OPEN`) and "a finished sale" (status `COMPLETED`) — checkout doesn't create a new record, it just
flips the status, stamps an invoice number, and records payments on the same row. Every open
`SaleOrder` for a session is a tab in the terminal's tab bar; there's no separate "parked" status —
parking just sets a free-text `note` for context, because unlike the old page-reload Thymeleaf
version, every mutation here is already persisted immediately over REST, so there's nothing extra
to "save" when you switch tabs or step away from one.

### Multiple cashiers per shift

A shift (`RegisterSession` — one till open/close, one cash float, one reconciliation) can now have
more than one cashier ringing sales on it at the same time, via `CashierSession`: a lightweight
"who's actually at the terminal right now" record, separate from the shift itself. The terminal
silently resolves "my own" cashier session from whoever's logged in (no PIN — `GET
/api/sessions/{id}/cashier-sessions/mine`, find-or-create); a **🔁 Switch cashier** button lets a
*different* staff member step up to the same physical terminal and identify themselves with just
their PIN (`POST /api/sessions/{id}/cashier-sessions`, no username needed — PINs are enforced
unique among active staff), which scopes the tab bar to just their own open carts and tags any sale
they ring to them (`OrderDto.cashierName` now prefers the order's own `cashierSession.cashier` over
the shift's opener). This is deliberately **attribution only** — it never touches or widens the JWT
that actually authorizes API calls; the till's cash float and reconciliation stay one shared thing
at the `RegisterSession` level regardless of how many `CashierSession`s work it. See
`CashierSession`'s and `CashierSessionService`'s javadoc for the full reasoning, and the admin
**Shifts** screen's "▸ Cashiers" expander for a per-shift breakdown of who joined.

Ending a `CashierSession` is separate from ending the *shift*: a cashier can end their own working
session at any time (**🚪 End my session** in the terminal — `POST
/api/sessions/{id}/cashier-sessions/{cashierSessionId}/close`), which discards any cart they never
checked out (same VOIDED-status mechanism as **Discard**, so nothing is left orphaned once their
tab bar disappears) and sends them back to the register list. An `ADMIN` can additionally end
*every* open cashier session on a shift at once without closing the shift itself (**Close all
sessions** on the admin **Shifts** screen's "▸ Cashiers" expander — `POST
/api/sessions/{id}/cashier-sessions/close-all`) — handy for kicking everyone off a till at end of
day before the shift itself is closed and reconciled. `CashierSessionService.close` allows a
cashier to close only their *own* session (`ApiException.forbidden` otherwise); `closeAllForShift`
is `ADMIN`-only and is also what runs automatically when the shift itself is closed.

**Sessions are never shared, and this is enforced server-side, not just by what the frontend
happens to ask for.** Every order endpoint that's scoped to a `CashierSession` — listing/starting
tabs (`GET`/`POST /api/cashier-sessions/{id}/orders`) and every mutation of an open cart
(add/remove/update line, set customer, hold, discard, checkout) — now requires the authenticated
caller to actually be that cashier session's own cashier, or an `ADMIN`
(`OrderService.requireOwnCashierSessionOrAdmin`, `403` otherwise). Before this check existed, the
frontend always *asked* for its own session (via `mine`/`join`), but nothing stopped a different
authenticated cashier from calling the same endpoints with someone else's `cashierSessionId` or
`orderId` and reading or posting transactions under it — i.e. "sessions" were attributable in the
UI but not actually isolated at the API. A pre-existing order with no `cashierSession` (from
before this feature existed) can only be acted on by an `ADMIN`, since ownership can't be
determined for it. The same ownership-or-admin check now also backs a cashier's own read access to
their session data (see "Sessions" below and `OrderService.listAllForCashierSession`) — being
scoped out of *other* cashiers' sessions is one half of "sessions are never shared"; being able to
actually *see your own* is the other half, which the `ADMIN`-only "Sessions" screen from the
previous pass had accidentally removed for non-admins.

### Auth

Stateless JWT — `POST /api/auth/login` returns a token, every other request carries it as
`Authorization: Bearer <token>`, `JwtAuthFilter` validates it per-request, no server-side sessions.
Two roles exist (`ADMIN`, `CASHIER`), and **`ADMIN` is now enforced** via Spring Security method
security (`@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")`): every write to categories,
products, customers, and every staff-account and dashboard endpoint requires `ADMIN` — and, as of
an earlier pass, so does every write on registers and shifts: creating/editing a register
(`RegisterController.createRegister`/`updateRegister`), opening or force-closing a shift
(`openSession`/`closeSession`), listing every cashier session on a shift and bulk-closing them
(`CashierSessionController.list`/`closeAll`). Reads that the terminal needs at checkout time
(category/product search, customer list, register listing) stay open to any logged-in user,
including `CASHIER`, and a cashier can always join, resolve, and close *their own* cashier session
(`CashierSessionController.join`/`mine`/`close`) — that "own resource" check can't be expressed
as a role alone, so it lives in `CashierSessionService.close` (and, for the order endpoints it
scopes, `OrderService.requireOwnCashierSessionOrAdmin`) as an owner-or-`ADMIN` check instead of a
`@PreAuthorize` annotation. The plain "Sessions" screen's listing (`GET /api/cashier-sessions`,
`CashierSessionQueryController`) follows the same pattern rather than an `ADMIN`-only
`@PreAuthorize`: it's open to any logged-in user, and branches internally —
`CashierSessionService.listAll()` for an `ADMIN` (every cashier session, every shift),
`listMine(caller)` for anyone else (just their own, via
`CashierSessionRepository.findAllByCashierIdOrderByStartedAtDesc`). An earlier pass had made this
endpoint (then `CashierSessionAdminController`) `ADMIN`-only outright, which over-corrected: it
stopped a cashier from seeing *any* of their own session history, not just other cashiers'. The
Angular app mirrors all of this with a route-level `adminGuard` on the truly admin-only routes and
by hiding the still-restricted buttons and nav links (New/Edit register, Open session, Close,
Close all sessions) from non-admins — but the backend check is what actually matters; the frontend
guard is just UX.

**One route-level gate is frontend-only and doesn't mirror a backend restriction**: `/registers`
itself gained `adminGuard` this pass (see "Registers" below), even though every individual action
on it was already `ADMIN`-only server-side. The problem wasn't a missing backend check on a
mutation — it was a *read* (the register list, and its unguarded "Go to terminal" link) that let a
cashier reach a terminal for a shift nobody had assigned them, where the terminal's own `mine()`
call would then auto-create a cashier session for them. There's no single backend endpoint to gate
against that; gating the route was the actual fix. `adminGuard`'s own fallback-on-rejection
changed from `/registers` to `/sessions` in the same pass, since falling back to a route the guard
itself now blocks would loop.

## Design system: Dark Pro Terminal, app-wide

Earlier, the Thymeleaf ERP's POS module got a dark "Pro Terminal" theme scoped to just that module,
because it lived inside a lighter admin app. This project **is** the POS product, so the same dark
theme is now the app's only look, applied globally in `frontend/src/styles.scss`: a small set of CSS
variables (`--accent`, `--bg`, `--surface`, `--text`, `--muted`, …) that every component's classes
build on. Change the look by editing those variables in one file — nothing else needs to change.

The one deliberate exception is the **receipt** screen (`checkout/receipt.component.ts`): it stays
light and printable on purpose — a dark background is a bad idea both on paper and in a print
preview — using its own component-scoped styles that don't touch the global dark theme.

## Running it

### Backend

```bash
cd backend
createdb pos_app   # or: psql -c "CREATE DATABASE pos_app;"
mvn spring-boot:run
```

`src/main/resources/application.properties` defaults to `jdbc:postgresql://localhost:5432/pos_app`
with user/password `postgres`/`postgres` — edit those to match your local setup.

Demo data (an admin + cashier login, 4 categories, 8 barcoded products, a walk-in customer, one
register) is seeded automatically on first run. Set `app.seed-demo-data=false` once you have real
data. The API listens on `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm start          # ng serve — http://localhost:4200, proxies nothing; the API base URL
                    # is set directly in src/app/core/constants.ts
```

Log in with `admin` / `admin123` (full access) or `cashier` / `cashier123` (day-to-day use). Each
demo account also has a PIN for the terminal's **🔁 Switch cashier** flow: `1234` for admin, `5678`
for cashier — try opening a shift as one, then switching to the other from the same browser
session without logging out, to see two cashiers' carts kept separate on one shift.

If your backend runs somewhere other than `localhost:8080`, change `API_BASE` in
`frontend/src/app/core/constants.ts`, and `app.cors.allowed-origins` in
`backend/src/main/resources/application.properties` if the frontend runs somewhere other than
`localhost:4200`.

## What's included

- **Terminal** (`/pos/:sessionId`): opening it now always **requires a PIN** (Task: "To open
  terminal for selling should ask PIN and open related session based on the PIN entered. If PIN
  is not valid the terminal shouldn't open") — there's no more silent "my own session" auto-open
  by whoever's logged in; the terminal shows a PIN-entry screen first, and only resolves (or
  creates) the matching cashier's `CashierSession` — and shows the terminal proper — once a valid
  PIN is entered. An invalid PIN leaves the PIN screen up with an error, same as any other PIN
  check in this app (`CashierSessionService.joinShift`'s "PIN not recognized"). Past that gate:
  barcode scan or product-tile entry, colored category filter tiles, live search, order tabs
  (every open cart for the *current cashier's own* working session on this shift, with a **+** to
  start a new one — see "Multiple cashiers per shift" above), per-line quantity/discount, a
  customer picker with its own **search box** (filters by name/phone as you type — Task: "Add
  Search Input Customer from Terminal screen") and a **👤+ Add** button that opens an inline
  name/phone/email/**Tax ID** form (Task: "Add Tax ID on Customer input inside Terminal screen" —
  the same optional field Admin > Customers' own form has) and, once saved, **selects the
  newly-created customer on the current order** (Task: "Add Customer button from Terminal screen
  And the added customer should be selected") — no need to leave the terminal (and lose the cart
  in progress) to reach Admin > Customers just to add someone new, **Hold** (parks the tab with a
  note and opens a new one),
  **Discard** (cancels an open cart outright — never touches stock), Payment, a **🔁 Switch
  cashier** button so another staff member can ring sales on the same shift under their own PIN
  without anyone logging out, a **🚪 End my session** button that closes the current cashier's own
  `CashierSession` (discarding any cart they never checked out) and returns to the register list,
  and an **↩️ Exit** link (an icon added alongside the existing text — Task: "Add icon on Exit
  Terminal button") back to the register/session list. A **📺 Customer display** button (Task:
  "Add customer-facing double screen") opens a second, read-only screen (`/customer-display/
  :cashierSessionId`, a new browser window) showing whichever tab is currently selected — its
  live cart and running total while ringing up, then a "Thank you!" screen with the completed
  payment breakdown for a few seconds once checkout finishes, then back to an idle branded
  welcome screen. It's meant for a second monitor on the same till (drag the new window over), or
  for entirely separate hardware (a tablet, a small counter screen) pointed at the exact same URL
  — either way it works the same, since it never talks to the terminal directly: the terminal
  just tells the backend which order is "on screen" every time a tab is switched
  (`CashierSessionApiService.setActiveOrder`), and the display polls that plus the order itself
  every couple of seconds. **It still needs a login** — there's no new unauthenticated "public
  kiosk" mode — so a customer display on separate hardware needs to be signed in as either the
  same cashier working that till or an admin (see "Known limitations" below for why, and what a
  display shows if that's not the case). Once a PIN has opened the terminal, it
  stays "remembered" for the rest of this browser tab's session (`TerminalSessionService`, a
  plain in-memory `providedIn: 'root'` singleton keyed by register-session id) — navigating to
  Checkout or the receipt screen and back **no longer re-shows the PIN screen** (Tasks: "The back
  button on checkout should not request PIN" / "The back button on print receipt should take back
  to terminal and not request PIN"), since that's just an in-app route change, not a new terminal
  session. An actual browser reload still requires the PIN again, because that clears the
  singleton — the PIN-on-open gate itself is unchanged and intentional (see the pass below).
- **Offline mode** (this pass — "there's no offline mode... a real deployment usually needs local
  queuing of sales that sync once connectivity returns"): once a cashier session is already open,
  a lost connection no longer means the terminal can't ring anything. `ConnectivityService` tracks
  reachability (a periodic ping to the new public `GET /api/health`, plus the browser's own
  online/offline events), and `OfflineSyncService`/`OfflineDbService` keep a local IndexedDB
  snapshot of the active product catalog, categories, active payment methods, and customers,
  refreshed automatically every few minutes while online (and force-refreshed once a cashier
  session opens). The moment connectivity is reported lost, the terminal's right-hand cart panel
  swaps to a local-only **offline cart**: the same product grid/search/barcode entry, tapping a
  cached product or scanning a barcode adds a line, quantities/discounts are editable the same
  way, and a **Payment** step (the same split-tender button-per-method UI as Checkout, minus a
  live credit-limit check — see below) queues the finished sale in IndexedDB with a
  client-generated id instead of calling the server. A **📥 Offline queue** button (shown once
  anything is queued) lists every queued sale and its status. The moment connectivity returns,
  every queued sale is replayed automatically, oldest first, against a new
  `POST /api/orders/offline-sync` endpoint — one call per sale that does the equivalent of the
  normal create-order/add-line/checkout round trips at once (since none of those are reachable
  offline), computing the *real* price/tax/total server-side exactly like an online sale would
  (same `OrderService.completeCheckout` code path, credit-limit enforcement included). It's
  idempotent by that client-generated id, so a retried sync (the terminal lost connectivity again
  mid-request) can never double-sell. **What happens to a queued sale at sync time**: the terminal
  priced it offline from a cached snapshot that can be stale by the time it actually syncs — stock
  was never checked at checkout at all, online or offline, so there's nothing to reconcile there,
  but price/tax can drift. The chosen policy (see the two clarifying questions this feature was
  built from) is to sync the sale anyway and flag it: if the terminal's offline estimate and the
  server's final total differ by more than a cent, the resulting order gets a plain-text
  `syncNote` ("a price or tax rate likely changed...") shown on the Receipts list, the in-terminal
  shift receipts, and the receipt reprint screen (a 📴/⚠ badge with the details in its tooltip) —
  reviewable, never silently accepted or silently rejected. The one thing that genuinely still
  fails at sync time is a real business-rule violation the terminal couldn't check offline (most
  notably a credit sale that turns out to exceed the customer's limit by sync time) — that one
  queued sale is marked **failed** with the server's own rejection message and stays in the queue
  for a cashier/admin to review and **Discard**, rather than being silently accepted or retried
  forever.
- **Checkout** (`/checkout/:orderId`): payment entry is a **row of buttons, one per active
  payment method** (Task: "On checkout change select into buttons for payment modes so If I click
  on the button then input text is enabled for entering amount") — tapping a method toggles it
  selected and reveals/enables its own amount input; any combination of selected methods, entered
  amounts summing to at least the total due, completes the sale. Replaces the previous fixed three
  rows of a method dropdown + always-enabled amount field. **An amount input can no longer be
  pushed past what's still owed** (Task: "if the total amount to pay is already input do not
  allow input extra amount") — each input is capped to the order total minus whatever's already
  entered on the *other* selected methods, so once one or more methods already cover the sale in
  full, every other method's input is capped at zero; toggling on a second method after the first
  already covers the total now defaults its amount to that remaining zero rather than pre-filling
  the full total again. **Cash is selected by default** (Task: "by default cash is selected") —
  whichever payment method is flagged cash-equivalent (`PaymentMethodOption.cash`, same flag
  `listCashCodes()` already uses elsewhere) is pre-selected with the full total on page load,
  falling back to the first active method if none is flagged cash; every other method's amount
  input stays hidden until its own button is tapped (Task: "Input for payment mode should be
  hidden and displayed once I press on payment mode"). **Each method's button and its amount input
  now share one row** (this pass — Task: "Replace Payment method label before input with the
  payment method button (each payment method should be on the same line with its input)") — the
  separate row of method-toggle buttons above a separate stack of "label + input" rows is gone;
  each payment method is one `*ngFor` row with its own toggle button immediately followed by its
  own (still hidden-until-selected) amount input, so the button itself is what replaces the old
  plain-text label rather than sitting apart from it. **Layout: payment on the left, totals on
  the right** (Task: "payment mode to be on left side and totals on right side") — the payment
  method buttons/inputs and the subtotal/tax/total-due table now sit side by side in a two-column
  layout (stacking on narrow viewports) instead of totals-then-payment stacked vertically; purely a
  template/CSS change, no behavior difference. "Back to cart" always returns straight to the
  terminal without a PIN prompt (see **Terminal** above). Then a printable **receipt**, which now
  opens with a **business header** (this pass — Task: "Add Business details + logo on the header
  of receipt printout"): logo (if one's been uploaded), business name, address, phone/email, and
  tax ID, pulled from the same `GET /api/settings` **Business details** feeds — the whole block
  is wrapped in `*ngIf="settings as s"` with every field inside its own `*ngIf`, so a store that
  hasn't filled in Business details yet just sees the receipt exactly as before (plain "Receipt"
  heading, no gap where the header would be). Also prints the register
  name and cashier who rang up the sale (`o.registerName` / `o.cashierName` on `OrderDto`, resolved
  from the order's `RegisterSession`), a **(tax incl.)** note under any line whose tax was folded
  into its price rather than added on top (see **Taxes** below), and a single **"Back to
  terminal"** link (previously a completed sale's receipt linked to `/receipts`
  instead, and a still-open one linked to the terminal but with a PIN prompt; Task: "The back
  button on print receipt should take back to terminal and not request PIN"). **"Complete sale"
  now auto-opens the print dialog and returns to the terminal once it's dismissed** (Task: "when I
  press Complete sale button, it should open automatically kiosk print page where I have to select
  printer and once I leave that page it have to take me back to the terminal screen to make
  another order") — checkout navigates to the receipt with `?autoPrint=1`, which triggers
  `window.print()` on load (the browser's own print dialog is where a printer actually gets
  picked) and listens for the browser's `afterprint` event — fired once that dialog is dismissed,
  whether the cashier printed or cancelled — to navigate straight back to `/pos/:sessionId`. A
  reprint opened from the **Receipts** list has no `autoPrint` flag, so it still behaves exactly as
  before: a manual **Print** button and a manual **Back to terminal** link, no forced navigation.
- **Receipts** (`/receipts`, any logged-in user): the most recent 200 completed sales app-wide,
  newest first, with invoice number, customer, total, and payment method — each row links to its
  receipt for **reprint** (`GET /api/orders/completed`). The terminal (`/pos/:sessionId`) now has
  its own **🧾 Receipts** toggle in the tab bar that opens the same list scoped to just that till
  session (`GET /api/orders/completed?sessionId=...`) without leaving the terminal's full-screen
  layout — a "← Back to terminal" button returns to the cart view.
- **My Profile** (`/profile`, new this pass — Task: "Add my profile, change my password"; any
  logged-in user, so it's a link in the topbar next to "Log out" rather than a sidebar item under
  the admin-only groups): view your own username/role (read-only) and edit your own display name,
  plus a separate change-password form that requires your *current* password (new backend
  `MeController` at `/api/me`, open to any authenticated user — every endpoint resolves "you" from
  the JWT, there's no id in the URL to act on someone else's account). Deliberately narrower than
  what Admin > Users' Staff screen can do to *other* accounts: you can't change your own
  username, role, or active flag here — self-promoting to `ADMIN` or self-deactivating would
  defeat the point of those being admin-controlled. Saving your name here updates the topbar
  immediately (`AuthService.updateFullName`), without needing to log out and back in. **Change PIN**
  (new this pass — Task: "Add change PIN inside my profile"): a third card, new PIN + confirm PIN
  (4-6 digits, same pattern as the admin-side `SetPinRequest`), a **Set/Not set** badge like Admin >
  Staff's list already shows, and `POST /api/me/pin` on the backend — which just calls the existing
  `UserService.setPin` aimed at the caller's own id instead of one an admin picked, so it gets the
  same active-user PIN-uniqueness check `CashierSessionService` relies on for free. Deliberately
  doesn't ask for your *current* PIN first, unlike the password form above: the PIN isn't the
  credential that got you into My Profile (your password already did that), so requiring the old
  one would strand anyone who forgot it with no self-service way to reset it.
- **Registers** (`/registers`, **`ADMIN`-only route** as of this pass — was open to any logged-in
  user): create/edit a register (name, location), open a till session with a starting cash float,
  close it with a counted-cash reconciliation (expected vs. counted, variance), and a Z-report per
  closed session — all already `ADMIN`-only server-side. The route itself is now gated too (Task:
  "Remove Registers & Terminal menu from Cashier side so he can not create a session himself"):
  its "Go to terminal" link had no per-row ownership check, so a cashier browsing this list could
  open the terminal for a shift nobody had assigned them, silently self-creating a cashier session
  on it via the terminal's `mine()` call — the exact thing the ninth pass's "Assign to" picker was
  supposed to prevent. A cashier's own assigned shift is still one click away, just from
  **Sessions** now (their own row's "Terminal" link) instead of from this list. `adminGuard`'s
  fallback-on-rejection destination changed from `/registers` to `/sessions` accordingly (falling
  back to a now-gated route would have looped), and login/`**`/`''` still resolve to `/registers`
  first but a non-admin bounces through to `/sessions` immediately. **Opening a session requires
  picking which staff member it's assigned to** (`SessionOpenComponent`'s "Assign to" dropdown,
  populated from active staff via `GET /api/users`) — the admin opening the till usually isn't the
  one who'll ring sales on it, so that staff member, not the admin, becomes the shift's
  `RegisterSession.cashier` and gets its first `CashierSession` (`OpenSessionRequest.cashierId`,
  validated server-side against `UserRepository` — 404 if no such user, 400 if inactive). Opening
  doesn't drop the admin straight into the terminal (that would auto-create *the admin's own*
  cashier session on the shift, which was never who the till was actually being opened for); it
  returns to the register list instead, and the assigned staff member reaches the terminal
  themselves — either by clicking "Go to terminal" on their own row on **Sessions**, or via
  **🔁 Switch cashier** with their PIN if the terminal is already logged in as someone else.
  One gap this left (reported as "when I open a new session for Admin there is no way to open
  another session for Cashier"): since **Registers & Terminal** is now `ADMIN`-only, an admin who
  clicks "Go to terminal" themselves lands the same `mine()` call the ninth pass built for
  cashiers — it silently auto-creates *the admin's own* `CashierSession` on that shift, from their
  JWT, regardless of who the shift was assigned to. If the admin doesn't happen to know the
  intended cashier's PIN for **🔁 Switch cashier**, there was no other way to get that cashier a
  session at all. Fixed with an admin-only **"+ Add cashier"** action (see **Shifts** below) that
  creates a specific staff member's `CashierSession` directly — `POST
  /api/sessions/{id}/cashier-sessions/assign` (`CashierSessionService.assignCashier`), the same
  find-or-create as `mine()`/"Assign to", just for an admin-chosen cashier instead of the caller's
  own JWT identity, and with no PIN involved.
- **Sessions** (`/sessions`, any logged-in user): every individual cashier session — one row per
  cashier actually working a shift, not one row per shift (`GET /api/cashier-sessions`, backed by
  `CashierSessionQueryController`/`CashierSessionService.listAll`/`listMine`). What comes back is
  scoped by role, not by an `ADMIN`-only route guard: an `ADMIN` sees and can **Close** any cashier
  session across every shift; a `CASHIER` sees and can close only their own (Change: "Cashier does
  not have access to his session data" — an earlier pass had made the whole screen `ADMIN`-only,
  which was an over-correction). Each row also has an **Orders** link to that cashier session's
  full order history (`/cashier-sessions/:id/orders`, `GET
  /api/cashier-sessions/{id}/orders/all`) — every order on that session regardless of status (open
  tab, completed sale, voided cart), not just what's currently open at the terminal
  (`OrderService.listAllForCashierSession`, same ownership-or-`ADMIN` check as everything else
  scoped to a cashier session). Shift-level actions (force-closing a whole shift, its Z-report)
  stay on the **Shifts** screen below — this screen is deliberately just about individual cashier
  sessions and their own orders, so both screens have one clear job instead of overlapping. Each
  row's other two links are keyed off **that row's own status** (`cs.status`, not the shift's) —
  Task: "When Session is closed show orders and report. When session is open show orders and
  Terminal." A **Terminal** link shows only while that specific cashier session is still `OPEN`
  (previously it showed whenever the *shift* was open, so a cashier whose own session had already
  ended could still see "Terminal" and silently start a brand-new session on a shift they were
  done with); a **Report** link (`/sessions/:id/report`) shows once that row's own session is
  `CLOSED` (previously gated on the whole shift being closed, so a session someone ended early on
  a still-open shift had no Report link at all).
- **Returns** (`/returns`): look up a completed sale by invoice number, pick quantities to return
  per line (validated server-side against what's already been returned, across multiple return
  visits), restocks inventory, and computes a tax-inclusive refund. A **Return all items** button
  (this pass — "add any option of returning all items on the order at once") fills every line's
  return quantity up to what was sold in one click, instead of typing each line by hand; a
  **Clear** button next to it resets every line back to zero. On an order that already had a
  prior partial return, filling in the full originally-sold quantity this way and submitting it
  gets the same precise "only N remaining" rejection a manual over-return always has — the button
  doesn't try to guess what's still returnable, only the backend actually knows that. Processing a
  return (this pass — "print out the return") now navigates straight to a printable **Return
  Receipt** (`/returns/:id/receipt`) and auto-prints it, the same flow checkout already uses for
  sale receipts; the Returns list also has a **Print** link per row to reprint one later. The
  return receipt shows each returned line's description, quantity, and refund amount (snapshotted
  onto `SalesReturnLine.refundAmount` at the moment it's returned — null for any return processed
  before this pass, which the receipt shows as "—"), plus the refund total/method and reason.
  Separately (this pass — "on the receipt which was returned, I should mark it as returned"), the
  original **sale** receipt (`/receipt/:orderId`) now marks any line with a prior return against
  it as `[RETURNED N of M]` in plain bold text (no color, since this receipt has to stay readable
  on a monochrome printout) — fetched from `GET /api/returns/order/{orderId}`, a
  `Map<orderLineId, totalQuantityReturned>` summed across every return visit against that order.
- **Admin management** (`/admin/*`, sidebar shown only when logged in as `ADMIN`):
  - **Dashboard** (`/admin/dashboard`): today's sale count/revenue, open session count, active
    product count, customer count, and a low-stock table (threshold set by
    `app.pos.low-stock-threshold`, default 10).
  - **Products** (`/admin/products`): full list including deactivated items, create/edit
    (name, SKU, barcode, **code**, **icon**, price, **cost price**, stock, category, tax rate,
    active flag), and deactivate (soft-delete — a product referenced by past sales is never
    hard-deleted). The tax rate picker offers only currently-active rates plus "— None
    (tax-exempt) —"; the list view shows each product's assigned rate (or "— exempt —") and its
    cost alongside its price. Cost price (`Product.costPrice`) is never shown to customers — it
    exists purely so the **Reports** below can compute profit; it defaults to 0 (100% margin)
    until an admin fills it in, and is snapshotted onto each `OrderLine` the moment it's added to
    a cart (`OrderLine.unitCost`), the same reasoning as `unitPrice`/the tax snapshot, so a later
    cost edit never rewrites a past sale's reported profit.
    - **Code** (`Product.code`, this pass — "add product icon, code. code can be null but
      unique"): a second, optional product identifier, distinct from SKU (never
      uniqueness-checked) and barcode (unique only within the barcode-lookup query, never
      enforced at write time). Leave it blank for products that don't need one; if set, the
      backend rejects a duplicate with a 409 (`CatalogService.applyRequest`), and the column
      itself carries a Postgres unique index (`@Column(unique = true)`) that — like every other
      nullable-but-unique column in this app — allows any number of NULLs since Postgres never
      treats two NULLs as equal.
    - **Icon** (`Product.iconDataUrl`, this pass): a small picture per product, uploaded the same
      way as the business logo (Settings > Business Details) — a `data:` URL read client-side via
      `FileReader`, capped at 2MB, no server-side resizing. Shown as a thumbnail in this list, in
      the edit form, and — new this pass — as a small icon on the product's tile on the cashier
      terminal's product grid.
  - **Loading 100,000 products** (this pass — "check the loading of 100000 products"): this
    screen used to fetch the *entire* product table in one request and render every row in one
    unbounded table. `GET /api/products/all` is now a real server-side pager
    (`page`/`size`/`q` params, capped at 200 rows per page regardless of what's requested,
    returned as a plain `{content, page, size, totalElements, totalPages}` envelope —
    `com.posapp.common.PageResponse` — rather than Spring's own `Page<T>` JSON shape) with a
    search box (name/SKU/barcode/code) and page-size selector, debounced so typing doesn't fire a
    request per keystroke. The edit form no longer loads the whole list to find one product by
    id (broken once that list was paged anyway) — it fetches `GET /api/products/{id}` directly.
    The terminal's own product search (`GET /api/products`, used by "All products" and by typing
    in the search box) is capped server-side at 200 results
    (`CatalogService.TERMINAL_SEARCH_CAP`) for the same reason — selecting "All products" against
    a 100,000-row catalog can't hand the terminal tens of thousands of tiles to render; a small
    hint appears under the grid when that cap is the reason the list stopped. See
    `backend/scripts/seed_100k_products.sql` for a fast, set-based script that generates 100,000
    sample products directly in Postgres to test this against (run it after starting the backend
    at least once, so `ddl-auto=update` has added the `code`/`icon_data_url` columns first).
  - **Categories** (`/admin/categories`): create/edit/delete (blocked server-side if any product
    still uses it), with a color picker matching the terminal's category tiles.
  - **Customers**, now its own collapsible sidebar group (Task: "Rename Customers menu to List
    and put it under Customers Menu") rather than a single flat link:
    - **List** (`/admin/customers` — same route as always, only the nav label/grouping changed):
      create/edit, now including a **Tax ID** field (Task: "Add Tax ID on customer entity") — a
      VAT/registration number a business customer can quote on their receipt, purely
      informational (nothing in checkout/tax math reads it), nullable since most walk-in
      customers won't have one.
    - **Statement** (`/admin/customers/statement`, new this pass — Task: "Add Customers Report
      (Customer Statement)"): pick a customer and a date range, see every completed sale they
      made in it (invoice, date, register, cashier, items, subtotal, tax, grand total, payment
      method) with a totals row, and export the same list as Excel or PDF — same backend
      machinery (`ReportController`/`ReportService`/`ReportExportService`) the seven POS Reports
      use, just with its own hand-built screen (a customer picker isn't one of the generic
      report-viewer's filter types) and its own home under **Customers** instead of the Reports
      picker.
  - **Users**, also now its own collapsible sidebar group (Task: "Move Staff menu to new Menu
    called Users") holding **Staff** (`/admin/staff` — route/label unchanged): create/edit staff
    accounts (username, full name, role, active flag), reset a staff member's password, and
    set/change their PIN (4–6 digits, used at the terminal — see "Multiple cashiers per shift"
    above; the list shows Set/Not set per staff member). An admin cannot deactivate their own
    account (enforced server-side in `UserService.updateUser`) — that's still only an admin
    resetting *someone else's* account; see **My Profile** below for a user managing their own.
  - **Shifts** (`/admin/shifts`, nav link moved under the **POS** sidebar group as of this pass —
    see "Sidebar navigation" below; the route itself is unchanged): an oversight view over every
    register session (open and closed, across all registers/cashiers) with a status filter, each
    row's sale count and gross sales for the session, a **▸ Cashiers** expander showing every
    `CashierSession` that joined that shift (loaded on demand) plus, while the shift is still
    `OPEN`, a staff picker and **+ Add cashier** button that gives the chosen active staff member
    their own `CashierSession` on that shift right away — no PIN needed (see the "Registers" bullet
    above for why this was added) — and a **Close all sessions** button that ends every still-open
    one on that shift without closing the shift itself, and
    links to the existing force-close (`/sessions/:id/close`) and Z-report
    (`/sessions/:id/report`) screens. This reuses the existing `RegisterSession` data — "shift"
    and "till session" are the same thing — it's the admin-facing list with totals that was
    missing; the `/sessions` list itself is role-scoped, not admin-only (see "Sessions" above).
  - **Settings** (new collapsible sidebar group as of this pass, grouping the "how the store is
    configured" screens):
    - **Payment methods** (`/admin/payment-methods` — route unchanged, only the nav link moved):
      what used to be a hardcoded CASH/CARD/MOBILE_MONEY/BANK_TRANSFER enum is an admin-manageable
      list (`PaymentMethodEntity`/`PaymentMethodController`, `/api/payment-methods`). Create a
      method with a `code` (e.g. `CASH`), a display `name`, and a `cash` flag (whether it counts
      as cash for till reconciliation — `RegisterService`'s expected-cash math and
      `ReturnsService`'s cash-refund total both look this up instead of comparing against a fixed
      `CASH` constant). A method's `code` is immutable once created (the edit form disables that
      field) because it's stored as a plain string on every historical `Payment`/`SalesReturn`
      row, not a foreign key — renaming or deactivating a method never rewrites past sales.
      Deactivating a method (there's no delete) just hides it from the checkout/refund dropdowns
      going forward. **Display order is now admin-configurable** (this pass — Task: "Make payment
      method order dynamic so I can set the first, second on checkout screen"): a plain integer
      `sortOrder` field (lower shows first; ties break alphabetically by name) on both the create
      and edit forms, plus an **Order** column on the list screen. The backend sorts by it
      everywhere a method list is returned (`findByActiveTrueOrderBySortOrderAscNameAsc` /
      `findAllByOrderBySortOrderAscNameAsc`), so Checkout's payment-method row — which simply maps
      `listActive()`'s array in order into its `*ngFor` — reorders itself automatically with no
      separate checkout-side sorting logic. The four seeded methods default to CASH, CARD, MOBILE
      MONEY, BANK TRANSFER, CREDIT in that order; every method created before this pass defaults to
      `0` (same as a never-configured new one), so nothing looks unordered until an admin actually
      sets distinct values. The four original methods are seeded on first run so existing behavior is
      unchanged out of the box.
    - **Taxes** (`/admin/settings/taxes`): what started as a single fixed `app.pos.tax-rate`
      application property, then (an earlier pass) a single admin-manageable "one active rate for
      the whole store" row, is now **per-product** tax (this pass — "Manage multiple tax where
      each product will have tax Id"): an admin-manageable list of named rates
      (`TaxRate`/`TaxRateService`/`TaxRateController`, `/api/tax-rates`, all methods `ADMIN`-only —
      a cashier never picks a tax rate at checkout, so unlike payment methods there's no "active
      list for anyone logged in" endpoint here), and **any number of rates can be `active` at
      once** — there's no more "activating one deactivates the others" exclusivity. Each product
      (`/admin/products`) picks which rate applies to it — or none, for a deliberately tax-exempt
      product — via `Product.taxRate` (nullable `@ManyToOne`). Like `PaymentMethodEntity`, a
      `TaxRate` has a `code` (e.g. `STD`) that's set at creation and never editable afterward
      (mirrors `PaymentMethodEntity.code`'s reasoning below) alongside an editable `name` and
      `ratePercent`, plus (this pass — Task: "On Tax rate add inclusive attribute where if it is
      true the in the tax in price is inclusive") an editable **"Tax-inclusive price"** checkbox,
      `TaxRate.inclusive` (default `false`).
      - **Inclusive vs. exclusive tax**: `inclusive = false` (the default, unchanged behavior) adds
        tax on top of a product's `unitPrice` at checkout — the classic "+ tax" total. `inclusive =
        true` means `unitPrice` already has that rate's tax folded into it (the shelf price the
        customer sees *is* the final price); `OrderCalculator` then extracts the tax back out
        (`amount * rate / (1 + rate)`, the standard tax-inclusive-price formula) instead of adding
        it on top. Either way, `lineNetTotal(line) + lineTax(line)` still equals what the customer
        pays for that line, and `subtotal(order) + taxTotal(order)` still equals `total(order)` —
        only what counts as "subtotal" changes: for an inclusive line it's the price *minus* the tax
        baked into it, not the raw displayed price. The per-line displayed price (`lineTotal`, and
        every screen that shows it — cart, receipt) is untouched either way, since showing the
        sticker price as-is is correct for both cases; only the tax/subtotal *split* changes.
      - **How tax is computed per sale**: when a line is added to a cart, `OrderService` reads the
        product's *currently assigned* `TaxRate` and snapshots its `code`/`ratePercent`/`inclusive`
        onto that `OrderLine` right then (`OrderLine.taxCode`/`taxRatePercent`/`taxInclusive`) — the
        same snapshot-not-live-reference pattern `OrderLine.description`/`unitPrice` already used
        for price/name. `OrderCalculator.lineTax()` computes each line's tax from its own snapshotted
        percent and inclusive/exclusive flag (zero if the percent is null, i.e. tax-exempt), and
        `OrderCalculator.lineNetTotal()`/`subtotal()`/`taxTotal()`/`total()` sum across lines —
        there's no more single external "the rate" (or "is it inclusive") parameter threaded through
        order math at all. A later edit to a product's tax assignment, a rename of a rate, a change
        to its percent or its inclusive flag, or deactivating it entirely **never** rewrites an
        already-rung line, because nothing about checkout looks the rate up live from the `TaxRate`
        table again. The Reports (below) and product list/edit screens show the same
        `taxRatePercent`/`taxRateInclusive` pairing, e.g. "Standard (15%, incl.)".
      - **Returns** work the same way: `ReturnsService.processReturn` computes the tax portion of
        each returned line's refund from that specific `OrderLine`'s own `taxRatePercent` and
        `taxInclusive` flag (via the same `OrderCalculator.taxOnAmount` helper `lineTax` uses), not
        a single store-wide rate or a hardcoded exclusive assumption — a return on a sale with mixed
        tax rates (or a mix of inclusive/exclusive lines) across its lines refunds the correct tax
        per line, and the refunded "subtotal" share is likewise net of any embedded tax.
      - **Seeding/upgrades**: `DataSeeder` seeds one `"Standard"` rate (code `STD`, from
        `app.pos.tax-rate` — still the *only* thing that property does, and only on a brand-new
        database) and assigns it to every demo product it creates. On every startup (not just a
        fresh database) it also backfills that same Standard rate onto any product that has no tax
        rate assigned — covering products that existed before this migration — so upgrading an
        existing deployment doesn't silently make previously-taxed products tax-exempt.
      - **Known trade-off**: pre-existing `OrderLine` rows from *before* this migration (created
        under the old single-flat-rate model) have no snapshot (`taxCode`/`taxRatePercent` are
        both `null`) — `OrderCalculator.lineTax()` treats that as zero, so historical completed
        sales rung before this pass will show **$0 tax** on their receipts going forward, even
        though they collected tax at the time. This is a direct consequence of the old design never
        having stored a per-order tax figure at all (`OrderDto` always recomputed tax live from
        whichever rate was *currently* active — so a rate edit could already silently change a
        past sale's displayed tax before this pass); there's no clean way to reconstruct what rate
        applied to a specific historical line after the fact. If preserving historical tax figures
        matters for your deployment, backfill `OrderLine.tax_code`/`tax_rate_percent` from your own
        records before/after upgrading, or accept that old receipts now read $0 tax.
    - **Business details** (`/admin/settings/business`, new this pass — Task: "Under Settings menu
      add Business Details menu" / "Add Business Details like name, address, contact, TAX ID,
      logo, etc"): business name (required), address, phone, email, tax ID, and a logo image —
      picked as a file, read client-side with `FileReader.readAsDataURL` (capped at 2MB before
      upload — there's no server-side resizing), and stored as-is as a `data:` URL string. Backed
      by a new singleton `StoreSettings` row (new `settings` backend package: `StoreSettings`,
      `StoreSettingsService`, `StoreSettingsController` at `/api/settings`, `ADMIN`-only end to
      end) — there's exactly one row in practice, created on first run by `DataSeeder` (mostly
      empty; an admin fills it in) and fetched-or-created on demand otherwise, the same
      singleton-row pattern as any other "one row of store-wide config" table would use, unlike
      **Payment methods**/**Taxes** above which are proper multi-row lists. **The business name and
      logo are now shown app-wide, not just on this settings screen** (this pass — Task: "Add
      Business name and logo in the header of system template (where label POS)" / "Add Business
      details + logo on the header of receipt printout"): the topbar's plain "POS" brand label is
      replaced with the business name (+ logo, if uploaded) once Business details has been filled
      in, and the same fields appear as a header block on every printed receipt (see **Checkout**
      above). Reading `GET /api/settings` for this reason is why the twenty-fourth pass changed the
      controller's access from class-level `ADMIN`-only to a method-level `@PreAuthorize` on just
      the two `PUT` endpoints — the topbar and receipt are seen by cashiers just as often as admins,
      so the **read** needed to open up (the same "open read, gated write" shape **Payment
      methods**/**Customer credit** already use), while writes stayed `ADMIN`-only.
    - **System settings** (`/admin/settings/system`, new this pass — Task: "Add System setting
      like currency, time zone, date format, etc"): currency (a curated dropdown of ~10 common
      codes — USD, EUR, GBP, UGX, KES, TZS, RWF, NGN, GHS, ZAR, INR — each pre-filling its usual
      symbol into a separately editable **currency symbol** field, so an admin can show a code like
      "UGX" instead of a symbol if they prefer), time zone (populated from the browser's own
      `Intl.supportedValuesOf('timeZone')` — the full, always-current IANA database, with a short
      static fallback list for the rare browser without that API — rather than hand-maintaining a
      timezone list), and date format (a fixed choice of four common patterns). Saved to the same
      `StoreSettings` row as **Business details** above, via its own `PUT /api/settings/system`
      that only ever touches these four fields. **Still not wired into how amounts or dates actually
      render anywhere in the app** (unlike Business details' name/logo, see above) — receipts,
      reports, and every other screen still format plain numbers/dates with no symbol or per-store
      pattern applied; doing that is a cross-cutting change touching every screen that shows a
      price or a date, out of scope for adding the settings screen itself (see "Known limitations"
      below).
    - **Reports** (`/admin/reports`, brand new this pass — Task: "Add POS Report where it will
      show the list of the following reports and by click on each report it will open report
      with different filters and export as EXCEL,PDF"): a picker grouped exactly as asked — **POS
      Sales-Transactions** (Total POS Categories Sales, Total POS Products Sales, POS Shifts
      Sales, Detailed POS Shifts Transactions) and **POS Profit** (POS Shifts Profit, Total POS
      Categories Profit, Total POS Products Profit) — each linking to its own report page
      (`/admin/reports/:key`). New `reports` backend package: `ReportController`
      (`/api/reports/*`, `ADMIN`-only, same as `DashboardController`) and `ReportService`, which
      builds every report from `OrderRepository.findCompletedForReport` (every completed sale in
      a date range, optionally narrowed to one register) grouped in Java by category/product/shift
      — the same "group in Java, not SQL" convention `OrderService.aggregate` already uses for the
      Z-report. Filters, on every report: a date range (defaults to the last 30 days) and a
      register; the two per-product reports also filter by category. Every report supports
      `?format=xlsx` or `?format=pdf` (`ReportExportService`, using Apache POI and OpenPDF) for
      download, alongside the plain JSON the on-screen table uses — one frontend
      `ReportViewerComponent` renders all seven from a shared config (`core/report-defs.ts`)
      rather than seven near-identical components.
      - **"Profit" throughout means revenue (post-discount, pre-tax — `OrderCalculator.lineNetTotal`,
        not `lineTotal`: for an inclusive-tax line `lineTotal` still has its tax folded in) minus
        cost of goods** — sales tax collected is never counted as revenue, since it's money held
        for remittance, not income. Cost comes from the new `Product.costPrice`/`OrderLine.unitCost`
        (see **Products** above); a product with no cost recorded reports 100% margin until an
        admin fills it in. The Sales reports (Total POS Categories/Products Sales) use the same
        `lineNetTotal` + `lineTax` split for their "Sales"/"Tax"/"Grand total" columns, for the same
        reason — summing the raw sticker price for an inclusive-tax line would double-count its
        embedded tax once the Tax column is added back on.
      - **Shift** in these reports means `RegisterSession` (the till/shift), same meaning as
        everywhere else in this app — "POS Shifts Sales"/"POS Shifts Profit" group by shift,
        "Detailed POS Shifts Transactions" is one row per completed sale (not per shift), and the
        two "Total POS Categories/Products" reports group across every matching shift at once.
- **Sidebar navigation** (`shared/shell.component.ts`, reworked this pass — Tasks 2–6): every link
  and toggle now has a leading icon (Task 3 — plain emoji, e.g. 🛒 POS, 🖥️ Registers & Terminal,
  📋 Sessions, ⚙️ Settings), and every collapsible group's expand/collapse indicator moved to the
  right edge of its row and switched from the previous right/down-pointing (▸/▾) pair to an
  up/down (▲/▼) pair — `▼` collapsed, `▲` expanded (Task 2). **Dashboard** is still the very first
  link (admins only), above every group; the everyday screens are grouped under **POS**
  (Registers & Terminal — `ADMIN`-only nav link now, see "Registers" above; Sessions; Returns;
  Receipts; and, moved in from the flat Admin list this pass, **Shifts**, Task 4); inside the admin
  section, **Products**/**Categories** stay grouped under **Inventory**, a new **Settings**
  group (Task 5/6) holds **Payment methods** (moved from a flat top-level admin link),
  **Taxes**, and (this pass) **Business details** and **System settings**, and (a later pass) the former flat **Customers** and **Staff** links became their
  own **Customers** (List/Statement) and **Users** (Staff) groups the same way — see
  **Customers**/**Users** above. The non-clickable "🛠️ Admin" heading that used to sit above the
  admin-only nav items has been removed — those items sit directly under the role check with no
  divider label above them.
  - **Sidebar groups are now an accordion, not five independent toggles** (Task: "Once one menu
    is open other should be closed"): `ShellComponent` replaced its per-group `posOpen`/
    `inventoryOpen`/`settingsOpen` booleans (each toggled independently, so any number could be
    open at once) with a single `openGroup: string | null` field plus a `toggleGroup(name)`
    method — clicking a group's header opens it and, as a side effect of there being only one
    `openGroup` value, closes whatever other group was open. Defaults to `'pos'` open (the
    everyday group) rather than every group collapsed, so the accordion doesn't hide everything
    on first load. State is still just a component field, not persisted across reloads.

- **Store credit / accounts receivable** (this pass — the 18-item credit request: "Enable credit
  for customer" through "Optional due date/payment terms"). A full add-on, not a bolt-on: every
  write action is `ADMIN`-only end to end (the user's explicit "who settles" answer), while the
  one read every cashier needs — a customer's current balance — stays open to any logged-in user.
  - **Enable credit / credit limit / default payment term** (Tasks 1, 2, 18): three new fields on
    `Customer` — `creditEnabled` (off by default), `creditLimit` (defaults to zero, never null,
    same "default missing numeric to zero" convention as `Product.costPrice`), and an optional
    `defaultCreditTermDays`. All three are `ADMIN`-only, set from Admin > Customers' existing
    form (a new "Store credit" section: an enable checkbox, and — shown only once enabled — the
    limit and default term inputs). The Customers list shows an "Enabled · limit X" / "Off" badge
    per row, plus a **Record payment** link for credit-enabled customers.
  - **Credit as a payment-method flag, not a separate checkout flow** (Tasks 3, 4 — "Cash + credit
    partial payment" / "100% credit sale"): `PaymentMethodEntity` gained a `credit` boolean,
    mirroring its existing `cash` flag exactly, and a seeded "CREDIT"/"Store Credit" method has it
    set. This means checkout's existing split-tender UI (tap a method, enter an amount, any
    combination summing to the total) already *is* "cash + credit" or "100% credit" — no new
    screen, just one more button. `SaleOrder` gained `creditAmount` (the sum of that sale's
    credit-flagged payments, snapshotted once at checkout — never live-derived from the payment
    method's flag later, same snapshot convention as tax/cost on `OrderLine`) and `dueDate`
    (explicit checkout value, else the customer's `defaultCreditTermDays`, else null/never-overdue).
  - **Credit limit enforcement** (Task 12): `CustomerCreditService.assertWithinLimit`, called from
    `OrderService.checkout` only when a sale has a credit portion — requires a customer on the
    order, requires that customer's `creditEnabled`, and rejects the sale if the customer's
    current outstanding balance plus this sale's credit portion would exceed their `creditLimit`.
    Checkout's payment-method row for the credit method is capped to the customer's currently
    available credit the same way every other method's row is already capped to the amount still
    due, and shows "Available credit: X" beneath it once a credit-enabled customer is on the sale.
  - **No floating/unapplied balance — allocations must sum to exactly the payment amount**: the
    deliberate design choice underlying items 5–8 and 13. A `CustomerPayment` (one customer
    settling their tab, in whole or in part) always carries one or more
    `CustomerPaymentAllocation` rows, each applied to a specific outstanding `SaleOrder` up to that
    order's own remaining balance, and the allocations on one payment must add up to exactly that
    payment's amount — there's no separate running "customer credit balance" sub-ledger to keep in
    sync. `CustomerCreditService.outstandingForOrder` (an order's `creditAmount` minus whatever's
    been allocated against it, floored at zero) is the one calculation every other credit feature
    reuses — checkout's limit check, the allocation screen, and all four new reports.
  - **Record Payment screen** (`/admin/customers/:id/record-payment`, `ADMIN`-only — Tasks 5, 6,
    7, 8, 13, 15: "Customer payment later" / "Partial settlement" / "Multiple payments against one
    order" / "Payment allocation to orders" / "Payment receipt" / "Payment history"). Allocation is
    **manual, oldest-first suggested** (the user's explicit answer to "how should a payment apply
    to outstanding orders?"): every outstanding order gets its own editable amount field; a
    "Suggest oldest-first" button (and typing a payment amount) greedily fills them oldest-first,
    but any row can be hand-edited before submitting — the backend only requires that the final
    amounts sum to exactly the entered payment. One payment can span several orders (item 8) and
    one order can receive several payments over time as it's partially settled (items 6, 7) — both
    fall out of the same allocation table with no separate code path. Recording a payment shows an
    inline printable receipt (item 13, a receipt number, amount, per-order allocations, and the
    customer's remaining balance) and a payment-history table below (item 15) that persists across
    visits (`GET /api/customers/{id}/payments`); `GET /api/customer-payments/{id}` reprints any
    past payment's receipt by its own id.
  - **Customer balance on POS screen** (Task 14): the terminal's customer picker shows "Owes X ·
    available credit Y" (or "Not enabled for credit") right under the customer dropdown, refreshed
    whenever the selected customer changes — backed by the same `GET
    /api/customers/{id}/credit-summary` endpoint checkout uses, the one credit endpoint open to any
    logged-in user rather than `ADMIN`-only.
  - **Four new reports under a new "POS Credit" section** on the Reports picker (Tasks 9, 10, 11,
    16, 17), reusing the same `ReportController`/`ReportExportService` (Excel/PDF export) as the
    original seven. Credit Sale Report (16) and Daily Credit Collection Report (17) take a date
    range but no register filter (`ReportDef.filterMode: 'dateOnly'` — a new option the generic
    `ReportViewerComponent` respects by hiding the register picker); Outstanding Credit Report (10)
    and Overdue Credit Report (11) are **snapshots** with no date range at all
    (`filterMode: 'none'` — the viewer hides the whole date block and runs automatically on open,
    since there's nothing to fill in first). The **Customer Statement** (item 9's closest existing
    relative) kept its existing shape — one row per invoice — and just gained three additive
    columns (credit amount, due date, outstanding) rather than being restructured into a mixed
    ledger of invoices and payments interleaved chronologically; that "everything in one timeline"
    interpretation is what the separate Payment History table on the Record Payment screen serves
    instead, so Customer Statement's existing contract (and every screen already built on it) never
    breaks.
  - **One-directional dependency to avoid a circular Spring bean**: `CustomerCreditService`
    (package `credit`) depends on `OrderRepository`/`CustomerRepository` directly, never
    `OrderService` — the same pattern `ReportService` already uses to read orders without depending
    on `OrderService`. This matters here specifically because `OrderService.checkout` needs
    `CustomerCreditService` for the limit check, so the dependency could only ever point one way.
- **Apps/plugins framework, starting with EFRIS Uganda e-Invoicing** (Task: "If this is headed
  toward Uganda-market use — proper e-invoicing/EFRIS integration with the Uganda Revenue
  Authority... I want to enable EFRIS... This will work as a plugin (or apps) to be installed and
  configured... I will share the required field to be added on receipt and new tables to be added
  once this app is installed"). A new `Admin > Settings > Apps` screen backed by a new `apps`
  package on the backend:
  - **A fixed, code-defined catalog, not a real plugin-jar/marketplace system** — `AppCatalog` is a
    small `List<Definition>` in code (currently one entry, `EFRIS_UG` — "EFRIS Uganda
    e-Invoicing"), the same "fixed list defined in code" pattern the frontend's report picker
    already uses for its report definitions. Adding a second app later means adding a second
    `AppCatalog` entry (and its own settings/behavior), not building a real installable-package
    system — there was no request for arbitrary third-party plugins, just a small fixed set of
    built-in integrations that can be turned on per store.
  - **Two-step lifecycle: Install, then separately Enable.** A new `InstalledApp` row (one per
    store, unique per `AppKey`) tracks `installed` and `enabled` as two independent booleans.
    Installing an app always leaves it disabled — so an app with no settings filled in yet never
    starts acting on real sales the moment someone clicks "Install." Enabling is a distinct,
    explicit second action, always available from both the Apps list and an app's own Configure
    screen. Uninstalling deletes the `InstalledApp` row (cascading to its settings) and requires an
    on-screen confirmation, since it discards whatever configuration was entered.
  - **Deliberately generic key/value settings, not an EFRIS-shaped form.** `InstalledAppSetting` is
    just `(appId, settingKey, settingValue)` with no fixed schema — because EFRIS's actual required
    fields (and any new tables needed to store what EFRIS returns per sale) haven't been specified
    yet; the user explicitly said those would come in a follow-up message. The Configure screen
    (`/admin/apps/:key`) is a plain add/remove key-value editor with an on-screen note that nothing
    here is validated or wired into any live integration yet. This was a deliberate choice to avoid
    inventing plausible-sounding EFRIS field names now and having to throw them away — the generic
    editor lets someone start typing in what they already know (e.g. an EFRIS API base URL or
    device/TIN identifier) without losing that work once the real structured form replaces this
    screen.
  - **New endpoints, all `ADMIN`-only** (`AppsController`, same tier as every other Settings
    screen): `GET /api/apps` (catalog merged with this store's install/enable state), `POST
    /api/apps/{key}/install|uninstall|enable|disable`, `GET`/`PUT /api/apps/{key}/settings`.
  - **`AppsService.isEnabled(AppKey)` exists but nothing calls it yet** — see Known limitations
    below; this pass intentionally stopped short of wiring any real checkout-time or receipt-time
    behavior for EFRIS.
- **EFRIS fiscal-receipt columns and per-terminal URL setting** (follow-up to the Apps framework
  above — Fourier's own spec: "When installing EFRIS the system will add the following column to
  SaleOrder... Add also Table EFRIS_setting column EFRIS_url... EFRIS_setting for one terminal"):
  - **`SaleOrder` gained eight nullable `TEXT` columns**: `sdcId`, `sdcDate`, `sdcReceiptNumber`,
    `internalData`, `signatureData`, `cisNumber`, `cisDate`, `mrcData` — exactly the fields
    specified, all mirrored onto `OrderDto` and the frontend `Order` model. All eight are null for
    every sale today, since nothing yet calls a real EFRIS/URA endpoint to populate them (see
    below) — this pass adds the storage and the receipt display, not the submission.
  - **The sale receipt (`checkout/receipt.component.ts`) shows a fiscal-receipt block once these
    fields are populated** — SDC ID, receipt number, fiscalization date, CIS number/date,
    signature, internal data, MRC — printed (not `no-print`) since these are meant for the
    customer's own paper copy, same as a real EFRIS receipt would show them. The block simply
    doesn't render at all while every field is null, which is every sale as of this pass.
  - **A new `efris_setting` table, one row per `Register` (terminal), not store-wide** — Fourier's
    own wording, "EFRIS_setting for one terminal," names a real difference from every other
    admin-configurable setting in this app (business details, system settings, tax rates, payment
    methods, and the generic `InstalledAppSetting` store the rest of the Apps framework uses):
    those are all one value for the whole store, but a real EFRIS deployment fiscalizes through a
    local device on each till's own network (a "Sales Data Controller"), so each terminal needs
    its own URL. New `EfrisSetting` entity (`register` FK unique, `efrisUrl` TEXT), its own
    `EfrisSettingService`/`EfrisSettingController` at a fixed `/api/apps/efris/terminals` path
    (`ADMIN`-only) rather than routed through `AppsController`'s generic `{key}` endpoints, since
    this is EFRIS-specific structured data, not something every catalog app needs.
  - **The Configure screen (`/admin/apps/EFRIS_UG`) gained a "Terminal EFRIS URLs" table** above
    its existing generic key/value editor — one row per register with an editable URL and its own
    Save button. The generic editor is unchanged and still there for anything else EFRIS (or a
    future app) needs that has no structured shape yet.
  - **Superseded by the next pass below** — this pass was schema plus display only; the actual
    submission is wired in immediately after.
- **Real EFRIS submission at checkout** (Fourier's own request: "Send to order data to EFRIS as
  json," with a working JSON-RPC request/response sample — Odoo's generic `/jsonrpc`
  "object.execute" calling convention, model `api.invoice`, method `create_api`):
  - **Two schema additions the sample payload needed but nothing captured yet**, both added after
    asking Fourier rather than guessing: `TaxRate.taxLabel` (the EFRIS/URA-RRA letter tax category,
    e.g. "A"/"B"/"C"/"D" — an admin maps each rate to one on the Tax Rates screen) and
    `Product.unspCode` (the UNSPSC-style commodity/tariff code EFRIS calls `unsp_code` — set per
    product). Both nullable, both snapshotted onto `OrderLine` at add-line time (`taxLabel`,
    `unspCode`), same "freeze it at the moment of sale" convention every other per-line field
    already uses (`taxCode`, `unitCost`, ...) — a later edit to a tax rate or product never rewrites
    what was actually sent for a past sale.
  - **`EfrisSetting` gained three more columns** (`efrisDb`, `efrisUid`, `efrisPassword`) alongside
    the existing `efrisUrl` — the JSON-RPC `db`/`uid`/`password` arguments EFRIS's own middleware
    expects (`args[0..2]` in the sample), still one row per terminal. The Configure screen's
    "Terminal EFRIS URLs" table gained DB/UID/Password columns to match; `efrisPassword` is stored
    (and resent on every call) in plaintext by necessity — EFRIS's protocol requires the raw value
    each time, the same way any third-party API key would.
  - **`EfrisSubmissionService`** (new, package `com.posapp.apps.efris`) builds the exact JSON-RPC
    tree from the sample using Jackson's `ObjectMapper`/`ObjectNode` and POSTs it with the JDK's
    built-in `java.net.http.HttpClient` — no new HTTP client dependency added. Deliberately holds
    zero reference to `SaleOrder`/`Register`/`Customer`: it takes and returns plain records
    (`EfrisConnection`, `EfrisInvoicePayload`, `EfrisInvoiceLine`, `EfrisSubmissionResult`) that
    `OrderService` builds from its own entities and applies the result back onto its own
    `SaleOrder`, so `apps.efris` never depends back on `order` — avoiding a circular Spring bean
    dependency, since `order` is the one that needs to call into `apps.efris`.
  - **How checkout triggers it**: `OrderController.checkout`/`syncOfflineSale` call the existing
    checkout logic (which commits its own transaction), then make a second, separate call to
    `OrderService.submitToEfrisIfApplicable(orderId)` — its own `@Transactional` method. This is a
    deliberate simplification: the outbound HTTP call to EFRIS happens synchronously, inline with
    the checkout HTTP response (briefly blocking it if EFRIS is slow to respond), rather than being
    queued for a background worker — there's no async/post-commit-hook infrastructure in this
    codebase yet, and adding one felt like scope creep beyond what was asked. `submitToEfrisIfApplicable`
    is a no-op (returns the order unchanged) whenever the order isn't `COMPLETED`, the order already
    has an `sdcId` (already fiscalized — protects a retried offline-sync request from double
    submitting), the `EFRIS_UG` app isn't enabled, or the order's terminal has no `EfrisSetting` URL
    configured — none of those are errors, just nothing to do.
  - **Failure policy Fourier chose when asked: "sale still completes, flagged for review."** A
    network error, an unreachable endpoint, or EFRIS itself rejecting the invoice never blocks or
    rolls back the sale — `submitToEfrisIfApplicable` catches everything and records
    `SaleOrder.efrisSubmittedAt` (when it was attempted) and `SaleOrder.efrisError` (plain text,
    null on success) instead, mirroring the offline-sync pass's existing `syncNote` pattern exactly.
    The receipt component shows an `efrisError` warning banner (`no-print`, staff-facing only) the
    same way it already shows `syncNote`.
  - **Parsing EFRIS's response is a best-effort read of the one sample given, not documented EFRIS
    behavior.** `sdc_data`/`cis_data` come back as opaque comma-separated strings with no field
    names; `EfrisSubmissionResult.success(sdcData, cisData)` splits each positionally
    (`sdc_data` → `[sdcId, sdcReceiptNumber, (dup), (unused "NS"), sdcDate, internalData,
    signatureData]`; `cis_data` → `[cisDate, cisNumber, mrcData, (unused amounts/count/TIN)]`) —
    documented in that method's own javadoc as the one place to fix the split/index logic if a real
    submission's fields land in the wrong `SaleOrder` columns.
  - **Not attempted this pass**: no retry queue for a failed submission (staff would need to notice
    the `efrisError` badge and handle it manually — there's no "retry EFRIS" button anywhere yet);
    no validation that a tax rate has a `taxLabel` or a product has an `unspCode` before checkout
    (a line simply submits `null` for either if unset, which will likely fail EFRIS's own validation
    — this pass didn't add a client-side warning for that); and `transaction_type`/`invoice_type`
    are hardcoded to the sample's own `"S"`/`"N"` since every sale this app submits is a normal
    completed sale.
  - **Fixed same day, after Fourier noticed it**: the "EFRIS commodity code" input on the Product
    form and the "EFRIS tax label" input on the Tax Rate form originally showed unconditionally —
    every store's admin saw two EFRIS-specific fields on its everyday product/tax-rate forms even
    if EFRIS was never installed at all. Both forms now call `AppsApiService.list()` and only
    render that one field when `EFRIS_UG` shows `installed: true`, mirroring how the Configure
    screen's own "Terminal EFRIS URLs" table is only ever reached by navigating into that specific
    app. The underlying `unspCode`/`taxLabel` values themselves are unaffected either way — hiding
    the field only changes whether it's *shown*; a value set while EFRIS was installed still loads,
    submits, and prints correctly if EFRIS is later uninstalled and the field disappears again.
  - **Request/response logging, added same day**: `EfrisSubmissionService` now logs every
    submission's outbound request and inbound response at `INFO`, each line tagged with the
    invoice number (`erp_code`) so a real submission can be traced end to end in the server's own
    log output — there's no admin-UI log viewer for this, just the application log. The logged
    request is never the literal one sent over the wire: a `redactedRequestJson` helper replaces
    the connection password (`args[2]`) with a fixed placeholder before serializing it for
    logging, falling back to a placeholder string (never the raw tree) if redaction itself ever
    fails, so a bug in that one method can't result in a terminal's EFRIS password landing in
    plain text in a log file. A network failure, a non-2xx response, and a response the parser
    couldn't understand are all still logged at `WARN` (as before), now also tagged with the
    invoice number for the same traceability.
  - **`payment_mode` wired up, same day**: Fourier confirmed four field mappings for the JSON sent
    to EFRIS. Three (`client_tin` ← the customer's `taxId`, `client_name` ← the customer's `name`,
    `client_phone` ← the customer's `phone`) were already exactly right in `OrderService
    .buildEfrisPayload` from the thirty-second pass — no change needed there. The fourth,
    `payment_mode` ← "the payment method," had been sent as a hardcoded `null` (EFRIS's own sample
    payload didn't populate it either, so there was nothing to copy at the time). `EfrisInvoicePayload`
    gained a `paymentMode` field; a new `OrderService.resolvePaymentMode(SaleOrder)` collects the
    sale's distinct `Payment.method` codes (the same plain code — `"CASH"`, `"CARD"`, etc. — this
    app already stores on every payment) in tender order and joins them with a comma for a
    split-tender sale, since EFRIS's sample sends this as one string field, not an array.
    `EfrisSubmissionService` now sends that value (or a JSON `null` if a sale genuinely has no
    payments, which shouldn't happen for a completed sale) instead of always sending `null`.
    **Not an EFRIS-defined payment-mode code** — no such enum was specified, so this sends the
    store's own payment-method vocabulary as-is, the same "carry whatever this app already has,
    don't invent a translation" choice `tax_label`/`unsp_code` already made.
  - **`tax_label` corrected to send the tax code, next day**: Fourier corrected the mapping — the
    JSON's `tax_label` field should carry this app's own tax-rate `code` (e.g. `"STD"`, the stable
    identifier already snapshotted onto `OrderLine.taxCode`), not the separate admin-entered
    EFRIS-category-letter field (`TaxRate.taxLabel`/`OrderLine.taxLabel`, e.g. `"A"`/`"B"`/`"C"`/
    `"D"`) the thirty-second pass had wired up instead, based on a misreading of Fourier's original
    sample. `OrderService.buildEfrisPayload` now passes `line.getTaxCode()` into
    `EfrisInvoiceLine.taxLabel` (kept under that name — like `erpCode`/`erpCodeRef` elsewhere on
    this record, it's named after the JSON key it becomes, not its source field). Since nothing
    reads `TaxRate.taxLabel`/`OrderLine.taxLabel` anymore, the "EFRIS tax label" input on the Tax
    Rate admin form was removed too — leaving it in place would have kept inviting an admin to set
    a value that silently did nothing, the same class of stale-UI gap the "EFRIS fields showing
    for stores without EFRIS" bug above was about. The underlying `taxLabel` columns/fields
    themselves were left alone (nullable, harmless, no migration needed) rather than deleted
    outright, in case a genuine future use for an EFRIS category letter shows up.
  - **Also asked for, same follow-up**: a "Retry EFRIS submission" button for a sale whose EFRIS
    submission failed. New `POST /api/orders/{id}/efris/retry` on `OrderController` — it just calls
    `OrderService.submitToEfrisIfApplicable` again, the exact same method the original checkout
    called; that method's own guards (order not `COMPLETED`, already has an `sdcId`, EFRIS not
    enabled, no connection configured for this terminal) already make repeat calls safe, so the
    endpoint adds nothing beyond exposing it. The receipt screen (`receipt.component.ts`) — which
    already showed the `⚠ EFRIS submission failed: ...` warning — now renders a button right next
    to it whenever `order.efrisError` is set; clicking it calls the new endpoint and replaces the
    on-screen order with whatever comes back, so a successful retry immediately swaps the warning
    for the fiscal-data block (`sdcId` etc.) with no reload needed. The button disables itself
    while a retry is in flight (so a slow EFRIS endpoint can't be double-clicked into two
    concurrent submissions) and shows a separate small error only if the retry call itself
    couldn't be reached — an EFRIS rejection on the retry just re-populates `efrisError` and the
    same warning shows again, no separate handling needed for that case.
  - **Investigated, not changed: a report that `client_tin`/`client_name`/`client_phone` were
    still null.** Re-read `OrderService.buildEfrisPayload`, `Customer`, `CustomerController`,
    `SaleOrder`'s `customer` association, the `/orders/{id}/customer` endpoint, and the terminal's
    customer picker end to end — every step is wired correctly: selecting a customer in the
    terminal persists it via `setCustomer` immediately (not deferred to checkout), and
    `buildEfrisPayload` reads `customer.getTaxId()/getName()/getPhone()` straight off whatever
    `order.getCustomer()` returns, with no other construction site anywhere in the codebase. No
    bug could be found in the code as it stands in this delivery. The most likely explanations for
    what Fourier saw: the specific sale in question was rung without a customer attached (walk-in
    default — check the receipt's own "Customer: ..." line for that sale), or it was tested against
    a build predating this delivery. The redacted request now logged at `INFO` for every submission
    (tagged with the invoice number) is the fastest way to confirm which: if `client_tin` is null
    there for a sale whose receipt clearly shows a real customer name, that's the concrete repro
    this needs to track the bug down further.
  - **`tax_amount`/`discount_amount`/`gross_amount` now fixed-2-decimal, next follow-up**: Fourier
    asked that "the amount value in json should be type of double with 2 decimals." These three
    fields (see the retry-button note above and `EfrisInvoiceLine`'s own javadoc for why they're
    always zero — the sample lets EFRIS compute them server-side) were being sent as the plain
    JSON integer `0` rather than a decimal. A JSON number has no real "double" type distinct from
    "integer," so what actually produces a fixed two-decimal number on the wire is a
    `java.math.BigDecimal` scaled to exactly 2 places — a Java `double` literal can't guarantee
    that (Jackson would still print a bare `0.0` for `0.0d`, not `0.00`). New
    `EfrisSubmissionService.ZERO_AMOUNT` constant (`BigDecimal.ZERO.setScale(2,
    RoundingMode.HALF_UP)`) replaces the three `0` literals, so the JSON now sends `0.00` for each
    — the same money-shaped representation `unit_price`/`tax_rate`/`discount_rate` already used.
  - **Tax code default "B", and the old EFRIS-category `taxLabel` columns dropped entirely**:
    Fourier asked for a default tax code of `"B"` (URA/RRA's standard-rate category letter), and
    for `TaxRate.taxLabel`/`OrderLine.taxLabel` to be removed as columns, not just left unused.
    `OrderService.DEFAULT_EFRIS_TAX_CODE` is the new `"B"` constant — `buildEfrisPayload` now
    falls back to it whenever a line's `taxCode` is null or blank (a tax-exempt line, or one
    predating the tax-rate feature), since EFRIS requires `tax_label` on every line and a bare
    `null` isn't a valid category. The `taxLabel` field is gone from `TaxRate` and `OrderLine`
    (entities), `TaxRateDto`/`TaxRateRequest`/`UpdateTaxRateRequest` (backend DTOs),
    `TaxRateService` (no longer sets it), and the frontend's `TaxRateOption` model,
    `tax-rate-api.service.ts` request payloads, and `tax-rate-form.component.ts` (which was
    already round-tripping a value nothing read since the previous pass — now gone for real). The
    two DB columns themselves are left in place (this app's schema is `ddl-auto=update`, which
    only adds columns, never drops them) — orphaned, harmless, and outside this pass's scope.
  - **`sdc_data`/`cis_data` parsed to Fourier's exact spec, and printed on the receipt**: the
    previous split/index logic was an explicit best-effort guess from a single worked example
    (see `EfrisSubmissionResult`'s old javadoc); Fourier has now supplied the real rule.
    `sdc_data` is 7 comma-separated values: `[0]` `sdcId`; `[1..3]` joined as `"{1}/{2} {3}"` into
    `sdcReceiptNumber` (e.g. `"301271/328875 NS"` — previously only `[1]` was kept and `[2]`/`[3]`
    were discarded as "unused"); `[4]` `sdcDate`; `[5]` `signatureData`; `[6]` `internalData` —
    note `[5]`/`[6]` were swapped from the old guess. `cis_data` is 3 comma-separated values:
    `[0]` `cisDate`, `[1]` `cisNumber`, `[2]` `mrcData` (the old code already had this part right).
    New private `EfrisSubmissionResult.joinReceiptNumber` builds the combined receipt number;
    `splitLenient`'s expected-length for `cis_data` dropped from 7 to 3 to match the real shape.
    No `OrderService`/`SaleOrder` changes were needed — the eight fiscal columns and their
    `submitToEfrisIfApplicable` wiring were already correct, only what fed them was wrong.

    The receipt (`receipt.component.ts`) now prints these as their own "SDC INFORMATION" section
    right after Payment, matching Fourier's own layout: SDC DATE and TIME side by side on one
    line (both come out of the single `sdcDate` value — `sdcDate`/`cisDate` are themselves a
    combined "date time" string, e.g. `"16/09/2026 07:38:15"`, per the parsing above), then SDC
    RECEIPT NUMBER and SDC ID as label/value rows, then Internal Data and Receipt Signature
    centered on their own lines (long opaque strings don't fit a label/value row), a divider, and
    the CIS RECEIPT NUMBER / CIS DATE+TIME / MRC rows. New component method
    `dateTimeParts(raw)` splits a combined date-time string on its first space (falling back to
    putting everything in `date` and leaving `time` blank if there's no space, so a
    differently-shaped response still prints something instead of breaking the layout) — used for
    both `sdcDate` and `cisDate`. The whole section still only renders when `sdcId` is set, same
    gate as before.
  - **Fixup, same day: a leftover `line.setTaxLabel(taxRate.getTaxLabel())` in `buildLine`**:
    Fourier caught a real gap in the previous pass's `taxLabel`-removal sweep — `OrderService
    .buildLine` (the snapshot-at-add-line-time method) still had this call, which the earlier
    grep for `taxLabel` across the codebase should have caught but didn't (it was checked by
    method name — `buildEfrisPayload`, the DTOs, the admin form — rather than a plain full-file
    search of `OrderService.java` itself). With `TaxRate.taxLabel`/`OrderLine.taxLabel` already
    deleted as fields, this line no longer compiles at all — the static brace/package check this
    project relies on (no Maven access in this sandbox — see "Known limitations" below) can't
    catch a missing method the way a real `mvn compile` would have. Removed; the surrounding
    `taxCode`/`taxRatePercent`/`taxInclusive` snapshot lines are untouched.
  - **Walk-in Customer now pre-selected on a new cart, next follow-up**: Fourier: "since we have
    a default customer 'Walk-in Customer' it is better to set this customer as default selected
    in the terminal (Selling cart) instead of having null." `DataSeeder` already seeded a real
    Customer row named "Walk-in Customer" on a fresh install, but nothing ever selected it — a new
    cart's `customerId` just stayed null until a cashier picked someone, and the terminal's
    customer dropdown's null option was itself mislabeled "Walk-in Customer" (implying it *was*
    that customer, when it was really "no customer at all"). That gap is also why an earlier
    investigation into null `client_tin`/`client_name`/`client_phone` in the EFRIS JSON came up
    with nothing to fix in the code — a walk-in sale genuinely had no customer attached, so those
    fields were correctly null; this pass is what actually closes that gap.

    New `Customer.walkIn` boolean (default false) marks the one customer the terminal should
    pre-select. `DataSeeder` sets it true on the "Walk-in Customer" row it seeds fresh, and a new
    `backfillWalkInCustomer` step (runs every startup, unconditionally, same pattern as
    `backfillProductTaxRates`) flags an *existing* store's own identically-named customer on
    upgrade, so nobody has to do this by hand. Not admin-editable from the Customer form —
    changing which customer this is means editing the seed or the database directly, deliberately
    out of scope for this pass.

    `CustomerDto` now carries `walkIn`; the terminal (`terminal.component.ts`) tracks
    `walkInCustomerId` (found from `customerApi.list()`) and applies it via
    `applyDefaultCustomer()` — called only right after a *brand-new* cart is created
    (`loadTabs`'s empty-tabs branch and `newOrder()`), deliberately not on every ordinary tab
    switch: a cart a cashier explicitly set to "No customer" must stay that way when they switch
    back to it, not get silently re-defaulted. It persists the pick through the existing
    `setCustomer()` call, exactly as if the cashier had chosen it from the dropdown — not just a
    frontend display value, so it's genuinely on the order server-side for EFRIS to read.

    The dropdown's null option is no longer labeled "Walk-in Customer" (that name now belongs to
    the real customer entry below it) — it reads "No customer" instead, for a cashier who
    deliberately wants a cart with nobody attached. The offline cart (`offlineCustomerId`,
    populated from the IndexedDB-cached customer list) got the identical treatment: default
    applied when the cache loads or after a queued sale resets the cart, same "No customer"
    relabel. If a store has no customer flagged `walkIn` at all (no fresh-install seed and no
    existing customer literally named "Walk-in Customer" for the backfill to find), the terminal
    simply falls back to its old null-by-default behavior — no regression, just no improvement
    until such a customer exists.

- **EFRIS success/failure decision corrected, raw response now recorded (2026-09-16)**: Fourier:
  "if error is not null and not empty that is when we record error ... also record Efris response."
  `EfrisSubmissionService.parseResponse` previously required EFRIS's `result.error` to equal the
  literal string `"SUCCESS"` for a submission to count as a success — anything else, including a
  blank/absent `error` field, was treated as a rejection. That was backwards from how EFRIS
  actually signals outcome: the corrected rule is `status != null && !status.isBlank()` means
  failure, so a null or empty `error` (EFRIS's own way of saying "no problem") is now correctly
  read as success, matching Fourier's own stated rule.

  Separately, the raw EFRIS response body — whatever text/JSON EFRIS actually sent back — is now
  kept end to end instead of only ever reaching the application log. `EfrisSubmissionResult`
  gained an 11th field, `rawResponse`, threaded through every call site in
  `EfrisSubmissionService` that has an actual HTTP response available (the non-2xx-status
  failure, all three failure returns inside `parseResponse`, the parse-exception catch, and the
  success path) — the handful of earlier failures that never get as far as receiving a response
  (no EFRIS URL configured, the request couldn't be built or sent) keep the original
  single-argument `failure(String message)` and leave `rawResponse` null, since there's genuinely
  nothing to record yet. `SaleOrder` gained a matching nullable `efrisResponse` TEXT column,
  written in `OrderService.submitToEfrisIfApplicable` right alongside `efrisSubmittedAt` so it's
  captured on both success and failure — reviewable next to the existing `efrisError` field, same
  "sale still completes, flagged for review" policy as before. `OrderDto` now exposes
  `efrisResponse` alongside the other fiscal fields for API consistency, though no UI currently
  displays it (not requested this pass). Backend-only change; no frontend files were touched.

- **Customer dropdown's "No customer" placeholder removed, replaced by the real Walk-in Customer
  DB row (2026-09-16)**: Fourier: "Instead of No customer (in select box) replaced by Walk-in
  Customer Customer from DB." The terminal's customer `<select>` (both the online cart and the
  offline cart) previously had a synthetic `<option [ngValue]="null">No customer</option>` above
  the real customer list, left over from before Walk-in Customer was pre-selected by default — a
  cashier could use it to deliberately clear a cart to no customer at all. That option is now
  removed from both dropdowns entirely; the real seeded "Walk-in Customer" row already renders via
  the existing `*ngFor` over `filteredCustomers`/`offlineCustomers`, and `applyDefaultCustomer`/
  `loadOfflineCatalog`'s existing default-selection logic (unchanged) is what makes it show there
  pre-selected on a new cart, so nothing else had to change to make it "the" default option instead
  of a fake null one. A cashier can still change the cart to any other real customer from the same
  dropdown, same as before — the only thing that's gone is the specific "no customer attached at
  all" choice.

  Frontend-only change: one file edited, `terminal.component.ts` (two `<option>` removals plus a
  doc-comment update on `applyDefaultCustomer` to stop describing a "No customer" selection that
  the UI no longer offers). Both dev and production `ng build` pass cleanly; no backend files
  touched, so the structural check stayed at 167 files.

- **QR code added to the EFRIS printout (2026-09-16)**: Fourier: "add QR CODE on the printout of
  receipt: input_data=sdc_date#sdc_id#sdc_receipt_number#+internal_data#+signature_data." The
  receipt's SDC INFORMATION block (printed only for a sale actually submitted to EFRIS, gated on
  `o.sdcId`) now ends with a centered QR code encoding exactly that payload — `sdcDate`, `sdcId`,
  and `sdcReceiptNumber` joined with `#`, followed by `internalData` and `signatureData` each
  additionally prefixed with a literal `+` before their own `#` separator. Missing sub-fields
  degrade to empty segments (never the literal string "null"), consistent with how every other
  field in this printout already handles a short/garbled EFRIS response.

  Generated entirely client-side with the `qrcode` npm package (new dependency, plus
  `@types/qrcode` as a dev dependency) — `ReceiptComponent.generateQrCode()` builds the payload
  string and calls `QRCode.toDataURL(...)`, storing the resulting PNG data: URL in a new
  `qrCodeDataUrl` field that the template renders as a plain `<img>`; a rejected/failed generation
  just leaves it null, so a receipt is never blocked from printing over a QR code failure. Called
  once after the order first loads and again after a successful EFRIS retry (`retryEfris`), so a
  receipt that failed EFRIS submission and is then retried successfully picks up its QR code
  without a page reload. `angular.json`'s `build.options` gained `"allowedCommonJsDependencies":
  ["qrcode"]` to silence the CLI's CommonJS-optimization-bailout warning for that package (it isn't
  published as ESM) — a cosmetic build-log fix, not a behavior change.

  Frontend-only change: `package.json`/`package-lock.json` (new `qrcode`/`@types/qrcode`
  dependencies), `angular.json` (the CommonJS allowlist entry), and `receipt.component.ts`
  (`qrCodeDataUrl` field, `generateQrCode()`, the two call sites, and the `<img>` in the template).
  Both dev and production `ng build` pass cleanly with no new warnings. No backend files touched —
  the structural check stayed at 167 files.

- **Tax code printed after each line's total, and two EFRIS letterhead logos added (2026-09-16)**:
  Fourier's two-part request. Task 1: "Add tax code after total order_line" — each order line's
  right-hand total cell on the receipt now prints that line's own `taxCode` (the EFRIS tax code
  already snapshotted onto `OrderLine` at add-line time, e.g. "B") in parentheses right after the
  amount, e.g. "12.00 (B)"; a line with no tax code (tax-exempt, or rung before this feature
  existed) just shows the plain total, same as before. Frontend-only, one line changed in
  `receipt.component.ts`'s line-item template.

  Task 2: "On EFRIS setting add two logo (URA LOGO, EFRIS LOGO) ... added left and right on the
  printout." `EfrisSetting` (the per-terminal EFRIS row this app already had for URL/db/uid/
  password) gained two new nullable TEXT columns, `uraLogoDataUrl`/`efrisLogoDataUrl` — data: URLs,
  same convention as `StoreSettings.logoDataUrl`. Admin > Settings > Apps > EFRIS's per-terminal
  table gained two matching columns with a file-upload input each (same FileReader.readAsDataURL
  pattern as the existing Business Details logo upload, 2MB cap), saved through the existing
  per-terminal PUT endpoint (`UpdateEfrisSettingRequest` gained the same two fields).

  The receipt needed a way to read just these two images without also exposing that terminal's
  EFRIS URL/db/uid/password to whoever's printing a receipt — any logged-in cashier can print a
  sale, but the rest of that settings table is admin-only. Rather than loosen that, a new endpoint,
  `GET /api/apps/efris/terminals/{registerId}/logos`, was added with a method-level
  `@PreAuthorize("isAuthenticated()")` override on the otherwise `hasRole('ADMIN')`-gated
  `EfrisSettingController`, backed by a new narrow `EfrisLogosDto` (just the two logo fields) and
  `EfrisSettingService.getLogosForRegister`. `OrderDto`/the frontend `Order` model both gained a
  `registerId` field (the order's own terminal's Register id, previously only `registerName` was
  exposed) so the receipt knows which terminal's logos to ask for.

  On the printout, the two logos render as a letterhead row — URA on the left, EFRIS on the right,
  fixed-width slots so a single uploaded logo still lands on the correct side — right above "SDC
  INFORMATION," gated the same way the rest of that block already is (only for a sale actually
  submitted to EFRIS, and only when at least one of the two logos has actually been uploaded for
  that terminal). `ReceiptComponent` fetches them once via the new endpoint when the order first
  loads (`loadEfrisLogos`); any fetch error just leaves them unset, same "never blocks printing"
  convention as every other best-effort fetch already in that component.

  Backend: six files edited (`EfrisSetting.java`, `EfrisTerminalDto.java`,
  `UpdateEfrisSettingRequest.java`, `EfrisSettingService.java`, `EfrisSettingController.java`,
  `OrderDto.java`) plus one new file, `EfrisLogosDto.java` (168 files total, static review clean).
  Frontend: `models.ts` (`registerId` on `Order`, two logo fields on `EfrisTerminal`, new
  `EfrisLogos` interface), `efris-api.service.ts` (new `getLogosForRegister`, `saveTerminalSettings`
  widened), `app-settings.component.ts` (two logo upload columns), and `receipt.component.ts` (tax
  code + the letterhead row/`loadEfrisLogos`). Both dev and production `ng build` pass cleanly.

- **EFRIS logos moved to the top of the receipt, flanking a centered "Receipt" title
  (2026-09-16)**: Fourier's follow-up on the previous pass: "those two logos ... right at the top
  of receipt. Better put Receipt Label in center." The URA/EFRIS letterhead row (added the same
  day, just above SDC INFORMATION) moves to the very top of the printout — the first thing inside
  `.receipt` — with the "Receipt" heading itself centered between the two logos instead of sitting
  on its own line below the business details block. The plain centered "Receipt" heading
  (unchanged) still renders on its own when the terminal has no logos configured, via an
  `<ng-template #plainReceiptTitle>` fallback on the row's `*ngIf`.

  Deliberately no longer gated on `o.sdcId`: it was fetched/gated that way only because it first
  landed inside the SDC INFORMATION block, which only renders for a sale actually submitted to
  EFRIS. Now that it's the page header, gating it the same way would mean the very top of the
  receipt visibly shifts layout depending on EFRIS submission status — inconsistent with how the
  business logo/name above it already prints unconditionally. A terminal with these logos
  configured now prints them on every receipt, submitted to EFRIS or not; `loadEfrisLogos` itself
  is unchanged (still fetched once when the order loads, still never blocks printing on error).

  Frontend-only change: one file edited, `receipt.component.ts` (template reorganization only — no
  new fields, no new API calls). Both dev and production `ng build` pass cleanly. No backend files
  touched — the structural check stayed at 168 files.

- **Five access-control/discount requirements (2026-09-16)**: Fourier's numbered list. Investigated
  each before touching anything — two (Tasks 3 and 5) turned out to already be fully implemented
  by earlier work, so only Tasks 1, 2, and 4 involved code changes.

  **Task 1, "In the system setting configure the max discount"**: `StoreSettings` gained a new
  `maxDiscountPercent` column (`BigDecimal`, default 100 — effectively unrestricted until an admin
  narrows it, so an existing deployment that never visits the screen keeps today's behavior).
  Threaded through `SystemSettingsRequest` (`@DecimalMin`/`@DecimalMax` 0-100), `StoreSettingsDto`,
  `StoreSettingsService.updateSystemSettings`, and a new "Max discount (%)" field on Admin >
  Settings > System settings, same pattern as the existing currency/timezone/date-format fields.

  **Task 2, "Only Admin will be allowed to gives discount"**: `OrderService` gained a shared
  `validateDiscount(discountPercent, caller)` helper, called from both `updateLine` (the normal
  online add-then-discount flow) and `syncOfflineSale`'s own per-line loop (which previously had
  its own separate copy of just the 0-100 range check — replaced with the same shared call so the
  offline path can't bypass the new rules). The rule: a negative value is always rejected; raising
  a line's discount above zero is rejected for anyone who isn't `Role.ADMIN` (dropping it back to
  zero is always allowed for any cashier, since that only removes a discount, never grants one);
  and even an admin's discount is capped at the store's own configured
  `maxDiscountPercent` from Task 1 — the cap and the role check are independent rules, both
  enforced together. On the terminal, both discount inputs (online cart and offline cart) are now
  `[disabled]` for a non-admin cashier (visible, so an admin-applied discount is still shown, just
  not editable) and clamped client-side to the fetched max — real enforcement stays server-side in
  `validateDiscount`, so disabling the input is a UX courtesy, not the actual security boundary.

  **Task 3, "Only Admin can enable Customer Credit"** — already fully in place from earlier work:
  `Customer.creditEnabled` is only ever set via `CustomerController.create`/`update`, both
  `@PreAuthorize("hasRole('ADMIN')")`, and the Customer form itself lives behind `[authGuard,
  adminGuard]` in `app.routes.ts` — a cashier can't even reach the checkbox. No code changed for
  this task.

  **Task 4, "If the customer does not signed Credit then the credit is desabled on checkout"** —
  the *effect* already existed (checkout's `maxAmountFor` capped a non-credit-enabled customer's
  credit row to a 0 max amount), but the credit payment button itself was still clickable, which
  read as a dead end rather than a disabled option. `CheckoutComponent` gained a `creditDisabled(row)`
  method (true when there's no customer on the sale yet, or the selected customer's own
  `creditEnabled` is off) — the credit button now has `[disabled]="creditDisabled(row)"` plus an
  explanatory `title`, and `togglePaymentMethod` itself also short-circuits on a disabled row as a
  second guard. The three existing explanatory `<p>` messages under the payment rows are unchanged.

  **Task 5, "Only Admin is allowed to change the product price"** — already fully in place: the
  product create/edit form lives behind `[authGuard, adminGuard]`, and every `CatalogController`
  write (`POST`/`PUT`/`DELETE /api/products`) is `@PreAuthorize("hasRole('ADMIN')")`. There is no
  separate in-terminal price-override feature anywhere in this codebase to gate — the only way a
  sale line's effective price ever changes today is via the line discount (Tasks 1/2 above), and
  `unitPrice` is always a read-only snapshot of the catalog price at add-line time. No code changed
  for this task.

  Backend: five files edited — `StoreSettings.java`, `SystemSettingsRequest.java`,
  `StoreSettingsDto.java`, `StoreSettingsService.java`, `OrderService.java` (168 files total,
  static review clean, unchanged count — no new files). Frontend: `models.ts`,
  `store-settings-api.service.ts`, `system-settings.component.ts`, `terminal.component.ts`,
  `checkout.component.ts`. Both dev and production `ng build` pass cleanly.

- **"Switch cashier" hard to find, and a cashier can be stuck with no way to open the terminal
  (2026-09-16)**: Fourier's bug report — "Missing 'Switch cashier' in the terminal" and "The
  Cashier is not able to sale in terminal (reason no selling cart available for him)". Both traced
  to `terminal.component.ts`; no backend change needed, so the 168-file count/static check is
  unaffected.

  Investigated first rather than guessing: read through the whole cashier-session model
  (`CashierSessionService`, `CashierSessionController`, `OrderService`'s ownership checks,
  `session-list.component.ts`, `app.routes.ts`) and confirmed the backend join/assign/mine
  endpoints, the `/sessions` "Terminal" link, and `loadTabs`'s auto-create-a-tab-if-none-exist
  logic are all already correct — a cashier who successfully resolves a `cashierSession` always
  gets a cart. The actual gap was reaching that point at all:

  1. The "Switch cashier" button (lets someone else step up to the same physical terminal and
     re-identify by their own PIN) has always worked, but its visible label was only
     `🔁 {{ cashierSession.cashierName }}` — the *current* cashier's own name. The words "Switch
     cashier" existed only in the button's `title` tooltip, so the feature read as just a name
     badge, not an action — which is exactly Fourier's report. Now reads
     `🔁 Switch cashier (name)`, keeping the current cashier's name visible alongside the now-explicit action.
  2. The terminal's PIN-entry gate (`*ngIf="!cashierSession"`) is deliberately mandatory on every
     fresh open (Round 1 decision, see the class doc comment) — but a cashier with no PIN set yet
     (Admin > Staff shows this as a "Not set" badge) had no way forward from that screen at all: no
     cashier session, so no cart, so nothing to sell with, and no hint that they even *could* fix
     it themselves. Self-service PIN setup already exists (My Profile > Change PIN, from an
     earlier task), it just wasn't linked from the one screen where a cashier without a PIN
     actually gets stuck. Added a line under the PIN form pointing to Admin > Staff (ask an admin)
     or My Profile (set your own PIN) — no change to the PIN-required behavior itself, which stays
     intentional.

  Frontend only: `terminal.component.ts`. Both dev and production `ng build` pass cleanly; backend
  untouched (168 files, static review clean).

- **Three more from the same day (2026-09-16): "Switch cashier" left the switched-to cashier
  unable to sell; cashiers can now add/modify customers; cashiers now have a Customers menu.**
  Fourier's messages, verbatim: "Task 2: When Switch cashier, the user is not able to sale in
  terminal(reason no selling cart available for him)." / "Task 1: Cashier can add or modify
  customer" / "Task 3: Give Cashier Customer menu". Task 1 and Task 3 are really one change (open
  the same screens the same way), so they're written up together below.

  **Task 2 — the real bug behind the *previous* entry's Issue 2, found while chasing this one.**
  "Switch cashier" re-identifies who a sale belongs to by PIN alone, deliberately *without*
  logging the browser out of its own JWT (see `terminal.component.ts`'s class doc comment) — so
  the instant a cashier switches to a teammate's PIN, every order call afterward carries
  `cashierSessionId` = the teammate's session but the authenticated caller = whoever the browser
  was originally logged in as. `OrderService.requireOwnCashierSessionOrAdmin` (and the identical
  `CashierSessionService.requireOwnSessionOrAdmin`) only ever allowed that session's own cashier
  or an ADMIN — so unless the browser happened to be logged in as an admin, `listOpenOrders`,
  `startNewOrder`, and everything else scoped to that cashier session came back 403, the terminal
  never got a single tab, and the switched-to cashier saw no cart, no way to sell, and no
  indication why. Fixed both checks the same way: also allow a caller who has their *own* open
  cashier session on the *same shift* as the one being acted on — i.e. anyone already legitimately
  signed on to this shift can act on a teammate's session on that shift once switched to it, which
  is exactly what "Switch cashier" is for, while a cashier with no session anywhere on this shift
  is still refused (the original "guessing another session's id" protection is untouched). Two
  files: `OrderService.java`, `CashierSessionService.java` (168 files total, static review clean,
  no new files).

  **Task 1 / Task 3 — cashiers can now add and edit customers, and see the menu for it.**
  `CustomerController.create`/`update` were `@PreAuthorize("hasRole('ADMIN')")` — which meant the
  terminal's own inline "Add customer" panel (an earlier task) had been silently unusable for a
  cashier the whole time, always 403ing. Opened both endpoints to any logged-in user, but split
  `applyRequest` into `applyBasicFields` (name/phone/email/taxId — always applied) and
  `applyCreditFields` (creditEnabled/creditLimit/defaultCreditTermDays — only ever applied when the
  caller is ADMIN), so "Only Admin can enable Customer Credit" stays true: a non-admin creating a
  customer gets one with credit off by default, and a non-admin editing an existing customer can't
  touch its credit fields no matter what the request contains. On the frontend, `/admin/customers`,
  `/admin/customers/new`, and `/admin/customers/:id/edit` dropped their `adminGuard` (list/new/edit
  are now `authGuard` only); `/admin/customers/statement` and the per-customer `record-payment`
  route stayed ADMIN-only, since those are financial reporting, not "add or modify a customer."
  `CustomerFormComponent`'s "Store credit" section is now `[disabled]="!isAdmin"` on all three
  inputs rather than gone — same visible-but-disabled pattern as the terminal's discount inputs
  from an earlier pass, so a cashier editing a customer can still see their existing credit status,
  just can't change it (the real boundary is server-side either way). `CustomerListComponent`'s
  "Record payment" link is now also `*ngIf="isAdmin"`. In the sidebar, the "Customers" group moved
  out of the ADMIN-only `ng-container` it shared with Inventory/Users/Settings and into its own
  block open to any logged-in user, with only its "Statement" sublink still gated inline to ADMIN.
  Backend: `CustomerController.java` (168 files, static review clean, no new files). Frontend:
  `app.routes.ts`, `shell.component.ts`, `customer-list.component.ts`, `customer-form.component.ts`.

  Both `ng build --configuration development` and the production `ng build` pass cleanly for all
  three tasks together.

- **Returns now integrate with EFRIS too.** Fourier's own spec: "The return will also integrate
  with EFRIS, the only change are: 1. instead of sending 'transaction_type':'S' send
  'transaction_type':'R' 2. instead of sending 'erp_code_ref':'0' send 'erp_code_ref': value of
  erp_code of the original receipt." Until now `EfrisSubmissionService` hardcoded
  `transaction_type` to the literal `"S"` and nothing on the `returns` side called EFRIS at all —
  a processed return just stayed a plain refund record with no fiscal submission, no matter how
  EFRIS was configured.

  `EfrisInvoicePayload` gained a `transactionType` field (was previously absent entirely, since
  every submission used to be a normal sale); `EfrisSubmissionService` now sends
  `payload.transactionType()` instead of the old hardcoded `"S"`, and `OrderService.buildEfrisPayload`
  passes `"S"` explicitly to keep a sale's own behavior identical. `SalesReturn` gained the same
  eight nullable EFRIS fiscal columns `SaleOrder` already had (`sdcId`/`sdcDate`/
  `sdcReceiptNumber`/`internalData`/`signatureData`/`cisNumber`/`cisDate`/`mrcData`) plus
  `efrisSubmittedAt`/`efrisError`/`efrisResponse`, and a new `returnNumber` column — a return needs
  its own `erp_code` distinct from the sale it's reversing (assigned the same `"POS-" +
  currentTimeMillis()` shape as `SaleOrder.invoiceNumber`, right at `processReturn` time, since a
  return has no draft/open state to defer it to). `ReturnsService` gained
  `submitToEfrisIfApplicable`/`buildEfrisPayload`, an exact mirror of `OrderService`'s own methods
  of the same name (same guards — already fiscalized, EFRIS not enabled, no `EfrisSetting` for this
  terminal — same "return still completes, flagged for review" policy on failure) with Fourier's
  two specified deltas: `transactionType` is `"R"`, and `erpCodeRef` is the *original* sale's own
  `erpCode` (its `invoiceNumber`) rather than `"0"`. The terminal used for a return's own EFRIS
  connection is the *original* sale's terminal (`salesReturn.getOrder().getSession().getRegister()`)
  since a return carries no register/session of its own. `OrderService.resolvePaymentMode`/
  `resolveProductCode` were changed from `private` to `public` so `ReturnsService` could reuse them
  rather than duplicating that logic a second time — a return reports the *original* sale's payment
  mode, since nothing new was tendered at return time. `ReturnsController.processReturn` now calls
  `submitToEfrisIfApplicable` right after creating the return, exactly like `OrderController.checkout`
  does for a sale, and a new `POST /api/returns/{id}/efris/retry` mirrors the sale's own
  `/orders/{id}/efris/retry` "Retry EFRIS submission" endpoint.

  `SalesReturnDto` gained the same fields (plus `registerId`, so the return receipt can fetch that
  terminal's EFRIS logos) and `return-receipt.component.ts` picked up the exact same EFRIS
  treatment `checkout/receipt.component.ts` already had: the URA/EFRIS logo letterhead flanking the
  "Return Receipt" title, the `efrisError` failure banner with its own "Retry EFRIS submission"
  button, the SDC/CIS INFORMATION block, and the same `sdc_date#sdc_id#sdc_receipt_number#+internal_data#+signature_data`
  QR code — all reading off `SalesReturn`'s new fields instead of `Order`'s, otherwise byte-for-byte
  the same layout and logic.

  Backend: `EfrisInvoicePayload.java`, `EfrisSubmissionService.java`, `OrderService.java`,
  `SalesReturn.java`, `ReturnsService.java`, `ReturnsController.java`, `SalesReturnDto.java` (168
  files total, static review clean, no new files — every change landed in an existing file).
  Frontend: `core/models.ts`, `core/api/returns-api.service.ts`, `return-receipt.component.ts`.
  Both `ng build --configuration development` and the production `ng build` pass cleanly.

- **The Z-report is now printable, the same way a receipt is.** Fourier's own request: "Add print
  session report (Z-report) same we print receipt. Not PDF actually." `SessionReportComponent`
  (`/sessions/:id/report`) used to be an ordinary screen inside `ShellComponent` — the app's normal
  dark-themed sidebar chrome — with no print treatment at all: no print button, no print-specific
  CSS, nothing hidden on print. Rebuilt as a direct copy of `checkout/receipt.component.ts`'s own
  printable-page shape: no shell, the same light `.receipt`-style monospace layout, the same
  business letterhead (logo/name/address/phone/email/tax ID, from `StoreSettingsApiService`, same
  graceful "just don't show it" degradation if nothing's configured), a `no-print` **Print** button
  that calls `window.print()`, and `@media print { .no-print { display: none !important; } }` to
  hide the Print/Back buttons on the actual printout — same CSS block, copied as-is. Deliberately
  *not* a PDF, per Fourier's own words: no export step, no `jsPDF`/similar dependency, just the
  browser's own print dialog, exactly like every receipt in this app already works.

  `SessionCloseComponent.submit()` now navigates to the report with `?autoPrint=1` after closing a
  shift, the same auto-print-then-the-browser's-own-dialog convention checkout and return
  processing already use — the print dialog opens automatically the moment the Z-report loads,
  and dismissing it (printed or cancelled) sends the cashier back to `/sessions` via the identical
  `afterprint` listener pattern `receipt.component.ts`/`return-receipt.component.ts` use. A Z-report
  reopened later from the "Report" link on a closed session's `/sessions` row has no `autoPrint`
  flag and behaves exactly as before this pass: the report just loads, Print is a manual click.

  Frontend only, two files: `session-report.component.ts` (effectively rewritten),
  `session-close.component.ts` (one line — the added query param). No backend change; 168 files,
  static review clean. Both `ng build --configuration development` and the production `ng build`
  pass cleanly.

- **2026-09-16 — Negative sign on return receipt amounts.** Task: "add also negative sign (-) on
  the amount on return receipt printoud." The return receipt (`return-receipt.component.ts`)
  printed refund amounts as plain positive-formatted numbers (e.g. `15,000.00`), with nothing on
  the printout itself distinguishing a refund from a sale amount. Added a leading minus sign to
  both places a refund amount is shown: the per-line `refundAmount` in the line-items table (now
  `−15,000.00` per line, still `—` when the line has no recorded refund amount) and the
  `refundTotal` in the totals table. Same minus-sign glyph (U+2212, not a hyphen) `session-close
  .component.ts` and `session-report.component.ts` already use for their own "Cash refunds" lines,
  so a refund reads as a negative amount consistently everywhere this app prints one.

  Frontend only, one file: `return-receipt.component.ts` (two template expressions changed). No
  backend change; 168 files, static review unaffected. Both `ng build --configuration development`
  and the production `ng build` pass cleanly.

- **2026-09-16 — Z-report shows a per-cashier breakdown, with start/end times.** Two requests taken
  together: "1. The session report should show start and end time. 2. The report for
  register_session (Shift) should show all cashier_session (session report)." The Z-report
  (`session-report.component.ts`) already showed the *shift*'s own opened/closed timestamps, but
  had nothing about the individual cashiers who actually worked it — relevant because a shift can
  have more than one cashier session inside it (the "Switch cashier" workflow, or an admin's
  "assign", lets several people ring sales on the same till during the same shift; see
  `CashierSession`'s own class javadoc). Added a "By cashier" section to the report listing every
  `CashierSession` that belongs to the shift, oldest first, each with its own start time, end time
  (or `—` while still open), status, and its own slice of the shift's sales (sale count, gross
  sales) — this is what answers both asks at once: per-cashier start/end times, and the full list
  of cashier sessions on the shift.

  Backend: `OrderService.computeCashierSessionTotals(cashierSessionId)` (new — same `aggregate()`
  helper `computeSessionTotals` already uses, just scoped to one cashier session's own orders via
  the existing `findByCashierSessionIdAndStatusOrderByCreatedAtAsc` repository method instead of
  the whole shift's). `CashierSessionService.listEntitiesForShift(registerSessionId)` (new — same
  query `listForShift` already runs, returning entities instead of DTOs since
  `RegisterService.sessionReport` needs each session's id to also pull its totals). New DTO
  `CashierSessionReportDto` (cashierSessionId, cashierId, cashierName, startedAt, endedAt, status,
  saleCount, grossSales, taxCollected, cashIn, nonCashIn). `SessionReportDto` gained a
  `cashierSessions: List<CashierSessionReportDto>` field, populated by `RegisterService
  .sessionReport`. No new endpoint — this rides the existing `GET /api/sessions/{id}/report` any
  logged-in user can already call for their own shift's Z-report.

  Frontend: `core/models.ts` gained a `CashierSessionReport` interface and `SessionReport
  .cashierSessions`; `session-report.component.ts` renders the new section (only when the list is
  non-empty) between the sales-data table and the cash-reconciliation table, one compact block per
  cashier — name and status on one line, start → end time on the next, sale count and gross sales
  on the last — styled consistently with the rest of the receipt (same font, same muted-gray
  secondary text this component already uses elsewhere).

  Five files (four backend, one frontend), all existing plus one new DTO; 169 files, static review
  clean. Both `ng build --configuration development` and the production `ng build` pass cleanly.

- **2026-09-16 — Split the CashierSession report out from the RegisterSession (shift) Z-report.**
  Task: "We need to differentiate CashierSession Z Report with RegisterSession Z Report." The plain
  "Sessions" screen (`session-list.component.ts`) lists individual `CashierSession`s — one row per
  cashier's own stint on a till — but its "Report" link pointed at `session-report.component.ts`,
  the whole *shift*'s (`RegisterSession`'s) Z-report: every cashier who worked that till, the
  shift's cash-drawer reconciliation, the lot. A cashier clicking "Report" on their own row saw the
  entire shift's numbers, not just their own. Fixed by giving `CashierSession` its own, smaller
  report, separate from the shift-level one.

  Backend: new `GET /api/cashier-sessions/{id}/report`, `CashierSessionService.report(id, caller)`
  (same own-session-or-admin-or-shares-this-shift gate `getById`/`close` already use), and a new DTO
  `CashierSessionZReportDto` (cashierSessionId, registerSessionId, registerId, registerName,
  shiftStatus, cashierId, cashierName, startedAt, endedAt, status, saleCount, grossSales,
  taxCollected, cashIn, nonCashIn) built from `OrderService.computeCashierSessionTotals` — the same
  helper the shift report's own "By cashier" breakdown (added just above) already uses. Deliberately
  excludes cash-drawer reconciliation and refunds, same reasoning as that breakdown's rows: both are
  shift-wide, physical-till concepts, and `SalesReturn` has no cashier-session reference to
  attribute a refund to one cashier's stint even if it wanted to.

  Frontend: new `cashier-session-report.component.ts` at `/cashier-sessions/:id/report` — same
  printable `.receipt` shape (light/monospace, business letterhead, `no-print` Print button) every
  other receipt-like screen in this app uses, titled **"Cashier Session Report"** (not "Z-Report")
  so the two are never mistaken for each other on the printed page itself. Shows register name,
  shift status, this cashier's own start/end time and status, then sale count/gross sales/tax
  collected/cash in/non-cash in — no cash reconciliation, no refunds. `session-list.component.ts`'s
  "Report" link now points here instead of the shift report. `session-report.component.ts` (the
  shift-level Z-report — reached from the admin "Shifts" screen, and auto-printed on closing a
  shift) is otherwise unchanged; its own "By cashier" section still gives a quick per-cashier
  overview inline, but a cashier's own definitive numbers now live on this separate report.
  `core/models.ts` gained `CashierSessionZReport`; `cashier-session-api.service.ts` gained
  `getReport()`; new route registered in `app.routes.ts`.

  Nine files touched in all — three backend (`CashierSessionZReportDto.java` new,
  `CashierSessionService.java`, `CashierSessionQueryController.java`) and six frontend
  (`cashier-session-report.component.ts` new, `core/models.ts`, `cashier-session-api.service.ts`,
  `app.routes.ts`, `session-list.component.ts`, plus a doc-comment-only touch on
  `session-report.component.ts`). Backend: 170 files, static review clean (up from 169 — the one
  new file is `CashierSessionZReportDto.java`, expected). Both `ng build --configuration
  development` and the production `ng build` pass cleanly.

- **2026-09-16 — Z-report's back link now says "Back to Shifts".** Task: "On Z-Report Change Back
  to Session link int Back to Shifts." A leftover from before the CashierSession/RegisterSession
  report split just above: the shift-level Z-report's (`session-report.component.ts`) footer link
  still read "Back to sessions" and pointed at `/sessions` — the plain "Sessions" screen (one row
  per `CashierSession`). That's no longer where this report is reached from; it's the admin
  "Shifts" screen (`shift-list.component.ts`, `/admin/shifts`, one row per `RegisterSession`) and
  the auto-print-after-closing-a-shift flow, both admin-only. Changed the link's label to "Back to
  Shifts" and its target to `/admin/shifts`, and updated the same auto-print `afterPrintHandler`
  (fires when the print dialog is dismissed after `?autoPrint=1`) to navigate there too, so both the
  manual click and the auto-print flow land in the same place now.

  Frontend only, one file: `session-report.component.ts` (link label/target, `afterPrintHandler`).
  No backend change; 170 files, static review unaffected. Both `ng build --configuration
  development` and the production `ng build` pass cleanly.

- **2026-09-16 — Removed the unused generic app key/value settings table, frontend and backend.**
  Fourier asked what the Admin > Apps > Configure screen's "there are no other fixed configuration
  fields yet — add whatever other key/value settings you already know below" warning was for; once
  it was clear nothing reads those rows back (EFRIS, the only app in the catalog, got its own
  structured per-terminal settings instead, and no other app exists to use the generic table), he
  asked to remove it — "Frontend + backend, fully."

  Backend: deleted `InstalledAppSetting.java` (the entity), `InstalledAppSettingRepository.java`,
  `AppSettingDto.java`, and `UpdateAppSettingsRequest.java` outright. `InstalledApp.java` lost its
  `@OneToMany List<InstalledAppSetting> settings` field (and the now-unused `CascadeType`/
  `OneToMany`/`ArrayList` imports). `AppsService.java` lost the `InstalledAppSettingRepository`
  dependency and its `getSettings`/`saveSettings` methods; `uninstall`'s own doc comment was
  trimmed to match (it no longer has settings to cascade-delete). `AppsController.java` lost the
  `GET`/`PUT /api/apps/{key}/settings` endpoints. Doc-comment-only touches on `AppCatalog.java`,
  `EfrisSetting.java`, and `EfrisSettingService.java` to drop their now-dangling references to the
  deleted class. No migration needed — this app has no SQL migration files at all
  (`spring.jpa.hibernate.ddl-auto=update` manages the schema from the entities directly), so the
  `installed_app_setting` table simply stops being touched by a fresh schema going forward.

  Frontend: removed the generic key/value `<table>`, its "+ Add field"/"Save" buttons, and the
  warning `<div class="alert alert-warning">` above it from `app-settings.component.ts`, along with
  the `rows`/`saving` fields, the `SettingRow` interface, `addRow`/`removeRow`/`save()`, and the
  `getSettings` call in `ngOnInit` — the EFRIS per-terminal table (and its own independent per-row
  Save) is untouched, it's a separate block on the same screen. `AppsApiService` lost
  `getSettings`/`saveSettings`; `core/models.ts` lost the `AppSetting` interface. Confirmed nothing
  else in the frontend referenced any of these before removing them.

  Thirteen files touched: ten backend (four deleted — `InstalledAppSetting.java`,
  `InstalledAppSettingRepository.java`, `AppSettingDto.java`, `UpdateAppSettingsRequest.java`;
  three substantively edited — `InstalledApp.java`, `AppsService.java`, `AppsController.java`;
  three doc-comment-only — `AppCatalog.java`, `EfrisSetting.java`, `EfrisSettingService.java`) and
  three frontend (`app-settings.component.ts`, `apps-api.service.ts`, `core/models.ts`). 166 Java
  files, static review clean (down from 170 — the four deletions, as expected). Both
  `ng build --configuration development` and the production `ng build` pass cleanly.

- **2026-09-16 — Apps catalog entry renamed to EFRIS Rwanda e-Invoicing.** Task 1: "In Apps Change
  EFRIS Uganda e-Invoicing Uganda Revenue Authority (URA) to EFRIS Rwanda e-Invoicing Rwanda
  Revenue Authority (RRA)." Scoped to exactly what "In Apps" names — the display name and
  description shown on the Admin > Settings > Apps catalog screen (`AppCatalog.java`'s one
  `Definition`): `"EFRIS Uganda e-Invoicing"` → `"EFRIS Rwanda e-Invoicing"`, and its description's
  `"Uganda Revenue Authority (URA)"` / `"URA's exact requirements"` → `"Rwanda Revenue Authority
  (RRA)"` / `"RRA's exact requirements"`. Also fixed one now-stale doc comment that quoted the old
  display name as an example (`app-list.component.ts`).

  Deliberately left everything else Uganda/URA-named alone for this pass: the `AppKey.EFRIS_UG`
  enum constant (an internal identifier, not shown in the Apps UI — renaming it would ripple into
  persisted `installed_app` rows, `AppsController.parseKey`, and every `key === 'EFRIS_UG'` check
  in the frontend), the per-terminal "URA Logo" column/`uraLogoDataUrl` field on the EFRIS settings
  table and receipts (`EfrisSetting.java`, `app-settings.component.ts`, `receipt.component.ts`/
  `return-receipt.component.ts`'s letterhead), and every historical dated README entry that
  accurately described this integration as Uganda-focused at the time it was written — those stay
  as the historical record. If those also need to become Rwanda-facing, that reads as its own
  follow-up task.

  Two files: `AppCatalog.java` (backend) and a doc-comment-only touch on `app-list.component.ts`
  (frontend). 166 Java files, static review unaffected (no files added/removed). Both
  `ng build --configuration development` and the production `ng build` pass cleanly.

- **2026-09-16 — Finished the Uganda → Rwanda rename everywhere: the `EFRIS_UG` enum, the "URA
  Logo" field, and every remaining prose mention.** Follow-up to Task 1 above — "yes do it for
  all." This is the deeper pass the previous entry deliberately deferred.

  **`AppKey.EFRIS_UG` → `AppKey.EFRIS_RW`** (`AppKey.java`), updated everywhere it's referenced:
  `AppCatalog.java`'s catalog entry, `OrderService`/`ReturnsService`'s `appsService.isEnabled(...)`
  checks, and every frontend `key === 'EFRIS_UG'` comparison (`app-settings.component.ts` x2,
  `product-form.component.ts`'s EFRIS-installed check). ⚠️ **This constant is persisted** —
  `InstalledApp.appKey` is `@Enumerated(EnumType.STRING)`, so any database that already has an
  `EFRIS_UG` row (i.e. EFRIS was installed/enabled before this update) will show the app as **not
  installed** after upgrading, since `AppKey.valueOf("EFRIS_UG")` no longer matches anything. Fix
  with one statement before or after deploying the new build: `UPDATE installed_app SET app_key =
  'EFRIS_RW' WHERE app_key = 'EFRIS_UG';` (verify the actual table/column names for your database
  first — Hibernate's default naming strategy gives `installed_app`/`app_key`, but confirm with
  `\d installed_app` in psql if unsure).

  **`uraLogoDataUrl`/`ura_logo_data_url` → `rraLogoDataUrl`/`rra_logo_data_url`** — the per-terminal
  "URA Logo" field, renamed "RRA Logo" everywhere it's shown. Backend: `EfrisSetting.java` (entity
  field + column), `UpdateEfrisSettingRequest.java`, `EfrisTerminalDto.java`, `EfrisLogosDto.java`,
  `EfrisSettingService.java`'s mapping code. Frontend: `core/models.ts` (`EfrisTerminal`/
  `EfrisLogos`), `efris-api.service.ts`, `app-settings.component.ts` (column header, image/file
  input, `onLogoSelected`'s field-name type, `saveTerminalSettings`'s payload), and the letterhead
  in both `checkout/receipt.component.ts` and `returns/return-receipt.component.ts` (`alt="URA
  logo"` → `alt="RRA logo"`, plus the layout comment "URA on the left and EFRIS on the right" →
  "RRA on the left"). ⚠️ **This column is also persisted** — `ddl-auto=update` adds the new
  `rra_logo_data_url` column alongside the old `ura_logo_data_url` one rather than renaming it in
  place, so any logo already uploaded under the old name needs a one-time carry-over: `UPDATE
  efris_setting SET rra_logo_data_url = ura_logo_data_url WHERE ura_logo_data_url IS NOT NULL;`
  (the old column is harmless left in place, or drop it afterward with `ALTER TABLE efris_setting
  DROP COLUMN ura_logo_data_url;` if you'd rather clean it up).

  **Remaining prose-only mentions** of "Uganda"/"URA" updated to "Rwanda"/"RRA" for consistency,
  now that nothing in the live app is Uganda-specific anymore: the `"B"` default tax code comments
  in `OrderService`/`ReturnsService`/`EfrisInvoiceLine.java` ("URA/RRA's standard-rate category
  letter" → "RRA's..."), the "EFRIS (Uganda URA e-invoicing) fiscal receipt data/fields" doc
  comments on `SaleOrder.java`, `SalesReturn.java`, `OrderDto.java`, and their frontend mirrors in
  `core/models.ts`, and `app.routes.ts`'s catalog comment.

  **Deliberately left alone**, same reasoning as the previous entry: every direct quote of
  Fourier's own original task wording ("add two logo (URA LOGO, EFRIS LOGO)...", still in quotation
  marks in several doc comments — rewriting a historical quote would misrepresent what was actually
  asked at the time), every already-shipped dated README entry describing past Uganda-era work, and
  the `UGX`/"Ugandan Shilling" entry in `system-settings.component.ts`'s currency picker — that's
  one option in a general East African currency list (which already also offers `RWF`/"Rwandan
  Franc"), not something tied to EFRIS or this rename.

  Twenty files touched: thirteen backend (`AppKey.java`, `AppCatalog.java`, `OrderService.java`,
  `ReturnsService.java`, `EfrisInvoiceLine.java`, `SaleOrder.java`, `SalesReturn.java`,
  `OrderDto.java`, `EfrisSetting.java`, `UpdateEfrisSettingRequest.java`, `EfrisTerminalDto.java`,
  `EfrisLogosDto.java`, `EfrisSettingService.java`) and seven frontend (`core/models.ts`,
  `efris-api.service.ts`, `return-receipt.component.ts`, `checkout/receipt.component.ts`,
  `app-settings.component.ts`, `product-form.component.ts`, `app.routes.ts`). 166 Java files,
  static review clean (no files added/removed — every change was an edit to an existing file).
  Both `ng build --configuration development` and the production `ng build` pass cleanly.

- **2026-09-16 — Added a new app to the catalog: EFRIS Chad e-Invoicing (`AppKey.EFRIS_TD`).**
  Task: "Now I want to add new app (EFRIS Chad) with will need the following settings: url,
  basic_auth_username, basic_password, sdc_number, branch_code, mrc, integration_key. The
  integration with EFRIS Chad is via HTTP(S) with basic authentication," plus sample request/
  response JSON for a future submission call. Fourier's own words: "I will share the resulting
  parsing later" — so, deliberately, this pass is install/enable/settings-storage only, the same
  scope EFRIS Rwanda itself started with before its own submission logic was ever discussed. **No
  HTTP request is sent to EFRIS Chad anywhere in this codebase yet** — nothing reads the new
  settings back except the admin screen that saves them.

  **New package `com.posapp.apps.efrischad`**, six new files, deliberately a wholly separate
  entity/table from `EfrisSetting` (Rwanda's) rather than an extension of it — the two
  integrations' auth models don't overlap (Rwanda: JSON-RPC `db`/`uid`/`password`; Chad: HTTP
  Basic Auth plus `sdc_number`/`branch_code`/`mrc`/`integration_key`), so sharing one shape would
  mean a pile of always-null fields per country. `EfrisChadSetting.java` (entity, table
  `efris_chad_setting`, one row per `Register` same as `EfrisSetting`), `EfrisChadSettingRepository
  .java`, `EfrisChadTerminalDto.java`, `UpdateEfrisChadSettingRequest.java`,
  `EfrisChadSettingService.java`, `EfrisChadSettingController.java` (`/api/apps/efris-chad/
  terminals`, ADMIN-only, mirrors `EfrisSettingController` but with no `/logos` endpoint — Chad's
  field list didn't include any letterhead logos this time).

  **`AppKey.java`**: added `EFRIS_TD`. **`AppCatalog.java`**: added its catalog entry — the
  description sticks to the technical/protocol details Fourier actually gave (HTTP(S) + basic
  auth + SDC number/branch code/MRC/integration key) and deliberately does **not** invent an
  official Chad tax-authority name/acronym the way "Rwanda Revenue Authority (RRA)" was named for
  EFRIS Rwanda, since Fourier didn't provide one for Chad.

  **Frontend**: `core/models.ts` — new `EfrisChadTerminal` interface (`registerId`,
  `registerName`, `location`, `url`, `basicAuthUsername`, `basicPassword`, `sdcNumber`,
  `branchCode`, `mrc`, `integrationKey`). New `efris-chad-api.service.ts` (`listTerminals`/
  `saveTerminalSettings`, mirrors `efris-api.service.ts`). `app-settings.component.ts` — new
  `*ngIf="key === 'EFRIS_TD'"` per-terminal settings table (same shape as the EFRIS Rwanda table
  above it: Terminal, Location, URL, Basic Auth Username, Basic Password, SDC Number, Branch Code,
  MRC, Integration Key, per-row Save), plus the matching component-class fields/methods
  (`efrisChadTerminals`, `chadTerminalsError`, `savingChadTerminalId`, `loadChadTerminals()`,
  `saveChadTerminalSettings()`), wired into `ngOnInit` the same way as the EFRIS Rwanda block.

  Seven files added (six backend + `efris-chad-api.service.ts`), four edited (`AppKey.java`,
  `AppCatalog.java`, `core/models.ts`, `app-settings.component.ts`). 172 Java files, static review
  clean. Both `ng build --configuration development` and the production `ng build` pass cleanly.

  **Deliberately not built yet, per Fourier's own "I will share the resulting parsing later"**:
  the actual HTTP(S) submission call to EFRIS Chad, request payload construction from an order/
  return (matching the sample `details_request` shape), and response parsing (`response_code ==
  "P"` for success; parsing `response_data.sdc_data`/`total_amounts`/`tax_amounts`/`rate_amounts`,
  which arrive as comma-separated strings, not structured objects, per the sample response). This
  is a deliberate follow-up once Fourier shares that mapping — same pattern as EFRIS Rwanda's own
  `EfrisSubmissionService`, added well after its settings screen first shipped.

- **2026-09-16 — Three related rules around EFRIS install/mapping/receipts.** Task: "1. The app
  installed and used can not be unstalled. 2. One Terminal can have only one EFRIS mapped with.
  2. [sic] The printout of receipt will always use the used app settings. If it used RWANDA or
  CHAD."

  **1. An app with terminal settings saved can't be uninstalled.** `AppsService.uninstall` now
  refuses (400) when at least one terminal has settings saved for that app — a direct
  `efrisSettingRepository.count() > 0` / `efrisChadSettingRepository.count() > 0` check, not a
  plugin-style usage-checker interface (this codebase has exactly two apps; see the javadoc on
  `AppsService.uninstall` for why a generic abstraction for two call sites would be the kind of
  speculative structure this session has otherwise avoided). "Used" here means *has terminal
  settings saved*, not *currently enabled* — a disabled app with settings still on file is still
  blocked, by design (Fourier's own choice when asked). The admin has to clear every terminal's
  settings for that app first (see point 2's new Clear action) before uninstalling becomes
  possible.

  **2. A terminal can only be mapped to one EFRIS integration at a time.** `EfrisSettingService
  .saveTerminalSettings` (Rwanda) now refuses to newly map a register that already has an
  `EfrisChadSetting` row, and `EfrisChadSettingService.saveTerminalSettings` (Chad) refuses the
  same the other way — each service takes a direct dependency on the other's repository (a
  two-app rule, not a general framework concern; see each service's own javadoc). Re-saving a
  terminal's *own* already-mapped app is always fine; only *newly* mapping the *other* app while a
  row already exists is blocked. New `DELETE /api/apps/efris/terminals/{id}` and `DELETE
  /api/apps/efris-chad/terminals/{id}` endpoints (`clearTerminalSettings`, idempotent) are how an
  admin unmaps a terminal — to switch it to the other integration, or just to stop using EFRIS on
  that till (also what unblocks point 1's uninstall check once every terminal is cleared).
  Frontend: `app-settings.component.ts` gets a "Clear" button next to "Save" in both terminal
  tables (with a confirm prompt, since it's destructive), and `efris-api.service.ts`/
  `efris-chad-api.service.ts` each get a matching `clearTerminalSettings` call.

  **3. Receipts already use "the used app's" settings — verified, no code changes needed.** Once
  point 2 guarantees at most one EFRIS mapping per terminal, the existing receipt code already
  does the right thing without any changes: `EfrisSettingService.getLogosForRegister` (backend)
  already returns `EfrisLogosDto.EMPTY` — never an error — for a terminal with no `EfrisSetting`
  row, so a terminal mapped to Chad instead of Rwanda now correctly shows no RRA/EFRIS logos on
  its receipts (`checkout/receipt.component.ts` and `returns/return-receipt.component.ts` both
  already degrade gracefully on a null/empty `efrisLogos`). Likewise, the "SDC INFORMATION" fiscal
  block on both receipts is already gated on `o.sdcId`/`r.sdcId` — a field only Rwanda's (not yet
  built) submission logic would ever populate — so it already never renders for a Chad-processed
  sale. EFRIS Chad has no logo fields at all (per its own settings screen), so there's currently
  nothing Chad-specific for a receipt to show either way; this becomes relevant again once Chad's
  own submission/response parsing is built in a future pass.

  Backend: five files edited (`AppsService.java`, `EfrisSettingService.java`,
  `EfrisSettingController.java`, `EfrisChadSettingService.java`,
  `EfrisChadSettingController.java`), no files added. Frontend: three files edited
  (`app-settings.component.ts`, `efris-api.service.ts`, `efris-chad-api.service.ts`). 172 Java
  files, static review clean. Both `ng build --configuration development` and the production
  `ng build` pass cleanly.

- **2026-09-16 — EFRIS Chad's actual submission call, "as we did for EFRIS Rwanda."** Task: "As we
  did for EFRIS Rwanda, if EFRIS Chad is set then call configured with the shared sample request."
  This is the follow-up to "I will share the resulting parsing later" — a completed sale is now
  actually submitted to EFRIS Chad, over HTTP(S) with Basic Authentication, using Fourier's own
  sample request/response shape from that earlier message. Sale-only for this pass (no return
  submission yet — see below).

  Three genuine ambiguities were cleared up with Fourier before building this, since guessing wrong
  on a live fiscal-compliance call is expensive to get wrong:
  - **payment_mode**: Chad's sample shows `"PAYMENT_METHOD_CASH"`, but this app's payment methods
    are freely admin-configurable strings with no known Chad equivalents beyond that one example.
    Fourier's choice: derive it as `"PAYMENT_METHOD_" + code.toUpperCase()` for each distinct
    `Payment.method`, comma-joined for a split-tender sale (same joining convention Rwanda's own
    `payment_mode` already uses).
  - **item_nature (goods vs. service)**: nothing in this app distinguished goods from services
    before Chad needed one for its `item_nature` field. Fourier's choice: add a real per-product
    field rather than a blanket default — see below.
  - **The sample's numeric `"invoice_number": 38`**: Fourier's instruction was to drop this field
    from the request entirely rather than build a per-terminal sequence-number counter for it, so
    it's not sent at all. (Distinct from `external_reference`, which *is* sent — see below.)

  **New `Product.service` (boolean, default `false` = goods)**, snapshotted onto
  `OrderLine.service` at add-line time, same convention as every other per-line snapshot
  (`taxCode`, `unspCode`, `unitCost`). Feeds Chad's `item_nature`
  (`ITEM_NATURE_GOODS`/`ITEM_NATURE_SERVICE`); `item_type` itself is always the sample's own
  `"ITEM_TYPE_FINISHED_PRODUCT"` literal, since the one sample line doesn't show that varying by
  goods vs. service. Product form: a new "This is a service, not a physical good" checkbox, shown
  once EFRIS Chad is installed (mirrors the existing "EFRIS commodity code" field's install-gated
  visibility, which now also mentions it doubles as Chad's `classification_code`). Files: backend
  `Product.java`, `ProductDto.java`, `ProductRequest.java`, `CatalogService.java`, `OrderLine.java`,
  `OrderService.java` (`buildLine`); frontend `models.ts`, `catalog-api.service.ts`,
  `product-form.component.ts`.

  **New package pieces mirroring EFRIS Rwanda's own submission plumbing:**
  `com.posapp.apps.efrischad.dto.EfrisChadConnection` (url/basicAuthUsername/basicPassword/
  sdcNumber/branchCode/mrc/integrationKey — mirrors `EfrisConnection`), `EfrisChadInvoiceLine` and
  `EfrisChadInvoicePayload` (mirror `EfrisInvoiceLine`/`EfrisInvoicePayload`, Chad's own field set),
  `EfrisChadSubmissionResult` (mirrors `EfrisSubmissionResult`, but see below for why it doesn't
  parse fiscal fields yet), and `EfrisChadSubmissionService` — builds the exact JSON shape from
  Fourier's sample (`registered_name`/`tin_number` from `StoreSettings`, `sdc_number`/
  `branch_code`/`mrc`/`integration_key` from that terminal's `EfrisChadSetting`,
  `classification_code` reusing `OrderLine.unspCode` — the same UNSPSC-style code Rwanda's own
  `unsp_code` already uses, `tax_code` reusing `OrderLine.taxCode` with the sample's own `"TN"`
  fallback (mirrors `OrderService.DEFAULT_EFRIS_TAX_CODE`'s `"B"` fallback for Rwanda),
  `external_reference` reusing `SaleOrder.invoiceNumber` unchanged since it's already generated as
  `"POS-" + System.currentTimeMillis()` — the exact shape the sample's own
  `"POS-1789562530534"` is), sends it as a plain HTTP(S) POST with an `Authorization: Basic ...`
  header (credentials never logged, matching Rwanda's own password-redaction discipline for its
  JSON-RPC body), and treats `response_code == "P"` as success, exactly Fourier's own rule ("When
  response_code is 'P' means SUCCESS").

  **`OrderService.submitToEfrisIfApplicable` now dispatches**: tries EFRIS Rwanda first (if enabled
  and this order's terminal has a Rwanda connection configured), then EFRIS Chad (same check, Chad
  connection). "One Terminal can have only one EFRIS mapped with" (enforced earlier) guarantees at
  most one branch ever actually has a connection to try, so this is a dispatch, not a real race —
  whichever integration a terminal is mapped to is the one that runs a given sale through. The
  existing `/api/orders/{id}/efris/retry` endpoint and receipt "Retry EFRIS submission" button work
  unchanged for Chad too, since both just re-call this same method.

  **Deliberately doesn't parse EFRIS Chad's fiscal response fields yet.** Rwanda's own
  `EfrisSubmissionResult` only got its exact `sdc_data`/`cis_data` positional split ("[0] sdcId,
  [1..3] sdcReceiptNumber...") after its submission call was already live, once Fourier gave that
  spec explicitly. No equivalent spec has been given for Chad's `response_data.sdc_data`/
  `total_amounts`/`tax_amounts`/`rate_amounts` comma-separated strings — guessing at what each
  position means would risk mislabeling real fiscal data. So this pass parses only what was
  actually specified (`response_code == "P"` for success) and stores the raw response body; a
  Chad-processed order's `efrisSubmittedAt`/`efrisResponse`/`efrisError` are set the same as
  Rwanda's, but `SaleOrder`'s Rwanda-specific fiscal columns (`sdcId`, `sdcDate`, ...) stay
  untouched for a Chad sale. Receipts already degrade correctly as a result (see the 2026-09-16
  "receipts already use the used app's settings" entry above) — no "Chad SDC INFORMATION" block
  exists yet, since there's no field-by-field spec to build one from. That parsing, and any Chad
  return submission (no return `transaction_type` has been given yet — the sample's own
  `"transaction_type": "V"` is the only value sent, always, for now), are the natural next
  follow-ups.

  Five new backend files (`EfrisChadConnection.java`, `EfrisChadInvoiceLine.java`,
  `EfrisChadInvoicePayload.java`, `EfrisChadSubmissionResult.java`,
  `EfrisChadSubmissionService.java`), seven backend files edited (`Product.java`,
  `ProductDto.java`, `ProductRequest.java`, `CatalogService.java`, `OrderLine.java`,
  `OrderService.java`, `EfrisChadSettingService.java`). Three frontend files edited (`models.ts`,
  `catalog-api.service.ts`, `product-form.component.ts`). 177 Java files, static review clean.
  Both `ng build --configuration development` and the production `ng build` pass cleanly.

- **2026-09-16 — Updated `seed_100k_products.sql` for the new `unsp_code`/`service` columns.**
  Task: "update also seed_100k_products.sql." The script's `INSERT` list hadn't been touched since
  `unsp_code` was added for EFRIS Rwanda, so every seeded row silently got `unsp_code = NULL` and,
  after this session's EFRIS Chad pass, would have gotten `service = false` (the column default)
  for every one of the 100,000 rows — fine for the listing/paging/search load-test this script
  exists for, but useless for exercising an EFRIS submission's per-line lookups or the product
  form's new "This is a service" checkbox against bulk data. Now: `unsp_code` is set on 1 in 10
  rows (an 8-digit placeholder commodity code), `service` is `true` on 1 in 10 rows — both
  documented in the script's own header comment alongside the existing barcode/code/icon
  distribution notes, with matching sanity-check queries appended. One file edited
  (`backend/scripts/seed_100k_products.sql`), no application code touched.

- **2026-09-17 — Fixed EFRIS Chad's `cis_date`: the sale's own completion time, not submission
  time.** Fourier's explicit field-by-field mapping confirmed every other field already matched
  the previous pass, but named `cis_date` as "Order date in this format '2026-01-04 10:03:41'" —
  the earlier implementation instead sent wall-clock time at the moment the HTTP request was
  built (`LocalDateTime.now()`), which is the same as the order's completion time on a normal
  checkout but silently wrong for a *retried* submission (a fixed URL, a transient network issue,
  etc. — see the receipt's "Retry EFRIS submission" button): a retry minutes or hours later would
  have reported the retry's own time as if that's when the sale happened.

  `EfrisChadInvoicePayload` gained a new `orderDate` field (an `Instant` — `SaleOrder.completedAt`,
  the same timestamp stamped once at checkout and never touched again), and
  `OrderService.buildEfrisChadPayload` now passes `order.getCompletedAt()` through instead of
  nothing. `EfrisChadSubmissionService` no longer calls `LocalDateTime.now()` at all — a new
  `formatOrderDate` helper converts that `Instant` to the exact `"yyyy-MM-dd HH:mm:ss"` string the
  sample shows (falling back to "now" only if a payload somehow arrives with no order date at all,
  which shouldn't happen for a completed sale). Three files edited
  (`EfrisChadInvoicePayload.java`, `OrderService.java`, `EfrisChadSubmissionService.java`), no
  files added. 177 Java files, static review clean. `ng build --configuration development` passes
  (no frontend files touched by this fix).

- **2026-09-17 — EFRIS Chad: parse `sdc_data` on a successful submission into the same fiscal
  columns Rwanda's own submission uses.** Task: "If the response is SUCCESS when
  `response_code`=`P` then parse the result of sdc_data (split by ,) as follow: Second value is
  sdc_id / Third value is MRC / Fourth is sdc_receipt_number / Next is sdc_date / Next is
  signature_data / Next is internal_data." This is the split spec `EfrisChadSubmissionResult`'s own
  javadoc said was missing (see the 2026-09-16 EFRIS Chad submission entry above) — Fourier gave it
  the same way he originally gave Rwanda's own `sdc_data`/`cis_data` split, after the submission
  call itself was already live.

  `EfrisChadSubmissionResult` now carries `sdcId`, `mrc`, `sdcReceiptNumber`, `sdcDate`,
  `signatureData`, `internalData` alongside `success`/`errorMessage`/`rawResponse`, and its
  `success(String sdcData, String rawResponse)` factory splits `sdcData` on commas (padding with
  nulls so a short/garbled response never throws — same `splitLenient` convention Rwanda's
  `EfrisSubmissionResult` already uses) into those fields by Fourier's exact 1-indexed positions
  (0-indexed: `[1]`→sdcId, `[2]`→mrc, `[3]`→sdcReceiptNumber, `[4]`→sdcDate, `[5]`→signatureData,
  `[6]`→internalData — `[0]`, the tin_number, isn't named by his spec and is unused).
  `EfrisChadSubmissionService.parseResponse` now reads `response_data.sdc_data` out of the JSON
  body and passes it into that factory instead of the previous no-op `success(body)`.
  `OrderService.submitToEfrisChad` now sets those six fields onto `SaleOrder` on success — reusing
  the *same* columns Rwanda's own `submitToEfrisRwanda` populates (`sdcId`, `mrcData`,
  `sdcReceiptNumber`, `sdcDate`, `signatureData`, `internalData`), since these are the same
  conceptual SDC fiscal fields, not Rwanda-specific ones (mirroring the same reuse decision already
  made for `unsp_code`↔`classification_code` and `taxCode`↔`tax_code`). `cisNumber`/`cisDate` stay
  untouched — Chad's response has no equivalent.

  **No frontend changes were needed.** The receipt's "SDC INFORMATION" block (`receipt.component
  .ts`) was already gated generically on `o.sdcId`/`o.sdcDate`/`o.mrcData`, not on which
  integration produced them, and its `dateTimeParts` helper just splits on the first space — which
  Chad's `"yyyy-MM-dd HH:mm:ss"` `sdc_date` format satisfies exactly like Rwanda's. So a successful
  Chad submission now makes that whole block (including the MRC line and the QR code) render
  correctly on its own, while the Rwanda-only "CIS RECEIPT NUMBER"/"CIS DATE" lines correctly stay
  hidden (still gated on `cisNumber`/`cisDate`, which remain null for Chad).

  Three files edited (`EfrisChadSubmissionResult.java`, `EfrisChadSubmissionService.java`,
  `OrderService.java`), no files added. 177 Java files, static review clean. No frontend files
  touched.

- **2026-09-17 — EFRIS Chad: `cis_date`/`cis_number` corrections, plus Chad's own two letterhead
  logos.** Three follow-up tasks in one pass:

  **Task 1: "The cis_data for sale_order should be the one send to json cis_date."** The sale's
  fiscal `cisDate` is now set to exactly the `cis_date` string the request itself sent (computed
  once in `EfrisChadSubmissionService#submit` and threaded through to both `buildRequestTree` and,
  on success, `EfrisChadSubmissionResult`) rather than left null. **Task 2: "The cis_number is
  picked from tag invoice_number of json response."** `cisNumber` is now read from the response's
  `response_data.invoice_number` (works whether EFRIS Chad sends it as a JSON number, as the
  sample shows, or a string). `OrderService.submitToEfrisChad` sets both onto `SaleOrder` on
  success, alongside the `sdc_data`-derived fields from the previous pass — reusing Rwanda's own
  `cisNumber`/`cisDate` columns, same "same conceptual fiscal field, not Rwanda-specific" reasoning
  as every other SDC/CIS field reuse in this integration. The receipt's CIS lines ("CIS RECEIPT
  NUMBER"/"CIS DATE"), previously always hidden for a Chad sale, now render correctly too — no
  frontend change needed, same generic `*ngIf="o.cisNumber"`/`*ngIf="o.cisDate"` gating as before.

  **Task 3: "Add also Chad Revenue Authority logo and EFRIS logo on EFRIS Chad setting."** Mirrors
  EFRIS Rwanda's own two-logo feature field-for-field: `EfrisChadSetting` gained `craLogoDataUrl`
  (Chad's own revenue authority — deliberately its own field rather than reusing Rwanda's
  `rraLogoDataUrl`, since "which country's revenue authority" is part of the field's identity, not
  an interchangeable label) and `efrisLogoDataUrl`; `UpdateEfrisChadSettingRequest` and
  `EfrisChadTerminalDto` carry them through the existing save/list flow; a new non-admin-gated
  `GET /api/apps/efris-chad/terminals/{registerId}/logos` (returning a new `EfrisChadLogosDto`,
  mirroring `EfrisLogosDto`) lets any logged-in cashier fetch them for printing, same permission
  split as Rwanda's own `/logos` endpoint. Admin > Settings > Apps > EFRIS Chad's terminal table
  gained "CRA Logo"/"EFRIS Logo" columns with the same file-upload-to-data:-URL pattern as the
  Rwanda table's own two logo columns.

  On the receipt side (`checkout/receipt.component.ts` and `returns/return-receipt.component.ts`),
  rather than looking up which integration a terminal is mapped to before deciding which `/logos`
  endpoint to call, both `EfrisApiService.getLogosForRegister` (Rwanda) and the new
  `EfrisChadApiService.getLogosForRegister` (Chad) are called in parallel, and two new getters
  (`authorityLogoDataUrl`, `printEfrisLogoDataUrl`) merge whichever one actually returned a logo —
  safe because "One Terminal can have only one EFRIS mapped with" (an earlier task) already
  guarantees at most one of the two ever has a row for a given terminal. The letterhead's left
  image alt text changed from the Rwanda-specific "RRA logo" to the neutral "Revenue authority
  logo" since it can now be either country's.

  Backend: one file added (`EfrisChadLogosDto.java`), five edited (`EfrisChadSetting.java`,
  `UpdateEfrisChadSettingRequest.java`, `EfrisChadTerminalDto.java`, `EfrisChadSettingService.java`,
  `EfrisChadSettingController.java`) for Task 3, plus three edited for Tasks 1–2
  (`EfrisChadSubmissionResult.java`, `EfrisChadSubmissionService.java`, `OrderService.java`). 178
  Java files, static review clean. Frontend: six files edited (`models.ts`,
  `efris-chad-api.service.ts`, `app-settings.component.ts`, `checkout/receipt.component.ts`,
  `returns/return-receipt.component.ts`). Both `ng build --configuration development` and the
  production `ng build` pass cleanly.

- **2026-09-17 — Audit log: who did what and when.** Task: "Implement an audit log of who did
  what and when." Scoped, when asked, to a curated set of "key business actions" rather than
  every mutating request (the alternative on offer): settings changes (business details, system
  settings, tax rates, payment methods, EFRIS Rwanda/Chad terminal settings), user management
  (create/update/reset password/set PIN/self password change), product create/update/deactivate,
  register create/update, app install/uninstall/enable/disable, voiding a sale, and processing a
  return. A cashier ringing up items or editing a cart is deliberately not logged — that would
  make the audit trail noise instead of a short, readable list of who changed what.

  New `com.posapp.audit` package: `AuditLog` (id/who/what/when — extends `BaseEntity`, whose
  `createdAt` *is* this entity's "when," since a row is written once and never updated),
  `AuditLogRepository` (a filtered/paged `search` query mirroring `ProductRepository
  .searchAllForAdmin`'s null-safe style, plus a distinct-actions lookup for the viewer's filter
  dropdown), `AuditLogDto`, `AuditLogService` (`record(action, description, entityType?,
  entityId?)` — pulls the acting user off `SecurityContextHolder` itself, the same context
  `JwtAuthFilter` populates on every request, so none of the eleven services below needed a new
  parameter threaded through their existing method signatures), and `AuditLogController`
  (`GET /api/audit-log` search + `GET /api/audit-log/actions`, both ADMIN-only).

  Eleven existing services now call `auditLogService.record(...)` right after their own save,
  each composing a plain-English sentence rather than leaving the viewer to reconstruct one from
  raw before/after fields: `StoreSettingsService` (business details, system settings),
  `TaxRateService`/`PaymentMethodService`/`UserService`/`RegisterService` (create/update, calling
  out a rate/role/active-flag/price change specifically when one happened), `AppsService`
  (install/uninstall/enable/disable), `EfrisSettingService`/`EfrisChadSettingService`
  (save/clear terminal settings — clear only logs when there was actually a row to remove, so
  re-clearing an already-unmapped terminal isn't a false event), `CatalogService`
  (create/update/deactivate product, with the update entry calling out a price change), and
  `OrderService`/`ReturnsService` (voiding a sale, processing a return). A password reset/change
  or PIN set is logged as having happened, never with the actual secret in the description.

  Admin > Settings > Audit Log (`admin/audit-log`, ADMIN-only, same tier as Reports/Apps): a
  server-side-paged, filterable table (search text, action-type dropdown, from/to date range) —
  same paging shape as the Products table, since this log only grows over time. New
  `AuditLogEntry` model, `AuditLogApiService`, `AuditLogListComponent`; one route and one sidenav
  link added.

  Backend: five files added (`AuditLog.java`, `AuditLogRepository.java`, `AuditLogService.java`,
  `AuditLogDto.java`, `AuditLogController.java`), eleven services edited to call it
  (`StoreSettingsService`, `TaxRateService`, `PaymentMethodService`, `UserService`,
  `RegisterService`, `AppsService`, `EfrisSettingService`, `EfrisChadSettingService`,
  `CatalogService`, `OrderService`, `ReturnsService`). 183 Java files, static review clean.
  Frontend: two files added (`audit-log-api.service.ts`, `audit-log-list.component.ts`), three
  edited (`models.ts`, `app.routes.ts`, `shell.component.ts`). Both `ng build --configuration
  development` and the production `ng build` pass cleanly.

- **2026-09-17 — Fix: Audit Log date-range filter crashed with a Postgres JDBC error.** Filtering
  the new Audit Log screen by from/to date threw `[ERROR: could not determine data type of
  parameter $7]` at runtime. Root cause: `AuditLogRepository.search`'s JPQL only ever used the
  `from`/`to` `Instant` parameters inside a bare `:from is null`/`:to is null` check — with no
  other typed context for that bind position, PostgreSQL's JDBC driver can't infer the
  parameter's type and fails to plan the whole query. This is the same bug class already solved
  once in this codebase for `ProductRepository.search`'s `q` String parameter (there, by always
  passing `""` instead of `null`); this fix applies the equivalent fix for `Instant`s. `from`/`to`
  are now normalized in `AuditLogService.search` to non-null sentinel bounds (`Instant.EPOCH` for
  an unset "from", `9999-12-31T23:59:59Z` for an unset "to") instead of being passed through as
  `null`, and `AuditLogRepository.search`'s query drops both `is null` branches for a plain `a
  .createdAt >= :from and a.createdAt <= :to` range check. Filtering with only one of the two
  dates set, or neither, or both, all now work. Two files touched (`AuditLogRepository.java`,
  `AuditLogService.java`); no frontend or API contract change. 183 Java files, static review
  clean.

- **2026-09-18 — Inventory depth: suppliers, purchase orders, receiving, and stock
  adjustments.** Task: "Inventory depth. Today stock is a single quantity field you edit
  directly. A complete POS usually adds purchase orders and supplier records, a stock-receiving
  flow that increments quantity with a paper trail, stock adjustments/write-offs with a reason
  code, and low-stock reordering (the dashboard already flags low stock, but nothing acts on
  it)." Scoped (when asked) to suppliers + purchase orders + receiving, stock
  adjustments/write-offs, and acting on the dashboard's low-stock flag — all against the existing
  single-store data model (no multi-location transfers this pass, since that only makes sense
  once there's more than one location to transfer between).

  New `com.posapp.inventory` package. `Supplier` (name/contact/phone/email/address/notes/active)
  — `SupplierService` hard-deletes an unreferenced supplier, same as `CatalogService
  .deleteCategory` does for an unused category, but falls back to deactivating one a purchase
  order already references rather than leaving that PO's "ordered from" dangling.
  `PurchaseOrderStatus` (DRAFT -> SENT -> (PARTIALLY_RECEIVED ->)* RECEIVED, or DRAFT/SENT ->
  CANCELLED) plus `PurchaseOrder`/`PurchaseOrderLine` — only a DRAFT can still be edited;
  `PurchaseOrderService.receiveLines` is the actual "stock-receiving flow that increments
  quantity with a paper trail": each call bumps a line's `quantityReceived` and, in the same
  transaction, increments the matching product's live stock via the existing
  `CatalogService.adjustStock` — the PO plus this call's own AuditLog entry together are the
  paper trail. Accepts a partial delivery and can be called more than once against the same PO.
  `StockAdjustmentReason` (DAMAGE/THEFT/EXPIRY/RECOUNT/OTHER) plus `StockAdjustment` — every
  direct stock correction that isn't a sale, return, or PO receipt now goes through
  `StockAdjustmentService.create` instead of an admin editing `Product.stockQuantity` directly:
  it snapshots stockBefore/stockAfter, requires a reason code, rejects a delta that would take
  stock negative, and (same as everywhere else) records who/when via `AuditLogService`.
  `CatalogService` gained `getProductOrThrow` — a plain, non-active-filtered lookup the inventory
  package needs (restocking/correcting a product regardless of its current active flag) without
  reusing `getProductForOfflineSync`, whose javadoc already claims a narrower single caller.
  Deliberately *not* a `Product.preferredSupplier` FK: `com.posapp.inventory` already depends on
  `com.posapp.catalog` (purchase order lines and stock adjustments reference `Product`), so a
  reference the other way — `Product` pointing at `Supplier` — would create the same kind of
  circular package dependency this codebase has always avoided (see `ReturnsService`'s own
  javadoc on `order`/`returns` for the earlier instance of this rule); the purchase-order form's
  supplier picker is a plain dropdown instead.

  Backend: 19 files added across `com.posapp.inventory` (`Supplier`, `SupplierRepository`,
  `SupplierService`, `SupplierController`, `PurchaseOrderStatus`, `PurchaseOrder`,
  `PurchaseOrderLine`, `PurchaseOrderRepository`, `PurchaseOrderService`,
  `PurchaseOrderController`, `StockAdjustmentReason`, `StockAdjustment`,
  `StockAdjustmentRepository`, `StockAdjustmentService`, `StockAdjustmentController`, and five
  `dto/` records/classes), plus `CatalogService.getProductOrThrow` and an `AuditLogService`
  class-javadoc update listing the new curated actions (`SUPPLIER_CREATED/UPDATED/DELETED`,
  `PURCHASE_ORDER_CREATED/UPDATED/SENT/CANCELLED/RECEIVED`, `STOCK_ADJUSTED`). 206 Java files,
  static review clean.

  Frontend: new `Supplier`, `PurchaseOrder`(+`PurchaseOrderLine`), `StockAdjustment` models;
  `supplier-api.service.ts`, `purchase-order-api.service.ts`, `stock-adjustment-api.service.ts`.
  Admin > Inventory gained three new screens: Suppliers (list/form), Purchase orders
  (list/form/detail — the detail screen is where a draft is sent or cancelled, and where a
  sent/partially-received order is actually received against, with per-line "receiving now"
  inputs), and Stock adjustments (list/form, with a product search picker and a reason-code
  dropdown). The dashboard's low-stock table (Task: "nothing acts on it") gained a per-row
  "Reorder" link and a bulk "Create purchase order for all" button, both landing on the
  purchase-order form pre-filled with a default quantity of 10 per line — a single PO belongs to
  one supplier, so a low-stock list spanning more than one usual supplier still produces one
  draft the admin assigns a supplier to and adjusts before saving, rather than being auto-split.
  Ten frontend files added, four edited (`models.ts`, `app.routes.ts`, `shell.component.ts`,
  `dashboard.component.ts`). Added a `.badge-warning` style (PARTIALLY_RECEIVED) to
  `styles.scss` — the existing badge set only had success/secondary/danger. Both `ng build
  --configuration development` and the production `ng build` pass cleanly.

- **2026-09-18 — Fix: sidebar accordion forgot which group was open on every navigation.** Bug
  report: "When I click on Inventory => Products, after loading product, POS Menu open its
  submenu instead of keeping Inventory submenu opened." Root cause: `ShellComponent` isn't a
  persistent layout wrapping a `<router-outlet>` — `AppComponent` is just `<router-outlet>`, and
  every routed page embeds its *own* `<app-shell>...</app-shell>` in its own template. So
  navigating to a different route destroys the old page (and the `ShellComponent` instance
  embedded in it) and constructs a brand new one — and `openGroup` was a plain field on that
  component, initialized to `'pos'` every time. The accordion wasn't "closing Inventory and
  reopening POS" on click; it was being torn down and rebuilt from scratch on *every single*
  navigation, in every group, and just happened to get noticed on Inventory → Products.

  Fixed by moving `openGroup` out of `ShellComponent` entirely into a new
  `SidenavStateService` (`providedIn: 'root'`, a true singleton for the app's lifetime — not
  reconstructed when the page underneath it is), with an Angular `signal` the same way
  `AuthService.currentUser` already does. `ShellComponent.isOpen`/`toggleGroup` now just
  delegate to it. Also added a one-time "open the right group already" default for the *first*
  page a session lands on (e.g. a bookmark straight into `/admin/suppliers`, or a hard refresh)
  by mapping `window.location.pathname` to a group at service-construction time — before this,
  landing directly on an Inventory page via URL/refresh always opened POS instead. One file
  added (`core/sidenav-state.service.ts`), one edited (`shell.component.ts`). Both `ng build`
  configurations pass cleanly.

- **2026-09-18 — Add a supplier inline from the New purchase order form.** Task: "You can add
  new Supplier from New purchase order form." Placing an order with a supplier that isn't set up
  yet used to mean losing the draft's lines-so-far to go fill out the full Admin > Suppliers
  form and come back. Now a "+ New supplier" button next to the picker opens an inline panel
  (name, contact person, phone, email — deliberately a smaller field set than the full Admin >
  Suppliers form, which still has address/notes too; a quick add to unblock this PO, not a
  replacement for it) right on the purchase-order form. Saving calls the same
  `SupplierApiService.create` the full form uses, adds the new supplier into the picker's list,
  and selects it on the draft right away — same "just did it, so use it" convenience `addLine`
  already gives a freshly-searched product. One file edited
  (`purchase-order-form.component.ts`); no backend change (reuses `POST /api/suppliers`). Both
  `ng build` configurations pass cleanly.

- **2026-09-18 — Task 1: Inventory Report; Task 2: Inventory Setting (allow negative stock).**
  Two independently-scoped tasks landed together.

  *Task 1 — Inventory Report.* Asked which of "stock on hand & valuation" vs. "stock movement
  history" it should show; answer was **Both**. Rather than bespoke components, extended the
  existing config-driven POS Reports framework (`REPORT_DEFS` / one generic
  `ReportViewerComponent` / `ReportController`) with a new `Inventory` section and two reports,
  since both are flat per-row tables that fit that framework exactly:
  - **Stock On Hand & Valuation** (`GET /api/reports/stock-valuation`) — a snapshot (no date
    range, like Outstanding/Overdue Credit), one row per active product: SKU, product, category,
    stock qty, unit cost, stock value (`qty × costPrice`, i.e. valued at cost, not at the
    customer-facing price), and a Low stock/OK status using the same
    `app.pos.low-stock-threshold` config the dashboard's low-stock list already uses.
  - **Stock Movement History** (`GET /api/reports/stock-movement`) — a date-ranged ledger, one
    row per dated stock-changing event. Discovered while building this that checkout/returns
    never touch `Product.stockQuantity` at all in this app (confirmed by `OrderService`'s own
    existing javadoc — stock is a purely manually/PO-managed number, never sale-driven), so the
    only two structured event sources are `StockAdjustment` (manual corrections/write-offs,
    already existed) and purchase-order receiving — which *didn't* already have a clean
    per-product-per-event record, only a cumulative `PurchaseOrderLine.quantityReceived` and a
    prose `AuditLog` line that can bundle several products together. Added a new entity,
    `StockReceipt` (mirrors `StockAdjustment`: product, qty, order number, unit cost, all
    snapshotted, timestamped via `BaseEntity.createdAt`), written once per product inside
    `PurchaseOrderService.receiveLines` alongside its existing `AuditLog` entry — this is what
    lets the ledger show accurate per-product, per-delivery rows instead of collapsing a
    multi-product receipt into one line. Both reports support the existing category filter;
    neither uses the register filter (stock isn't tied to a register).

  Backend: `StockReceipt` + `StockReceiptRepository` (new), `StockAdjustmentRepository` gained
  `findByCreatedAtBetweenOrderByCreatedAtDesc`, `PurchaseOrderService.receiveLines` now also
  writes a `StockReceipt` row per product received, `ReportService` gained `stockValuation`/
  `stockMovementHistory`, `ReportController` gained the two endpoints (JSON + Excel/PDF export,
  same as every other report), two new row DTOs (`StockValuationRow`, `StockMovementRow`).
  Frontend: two new `REPORT_DEFS` entries under a new `Inventory` section; `ReportListComponent`'s
  section list updated so it actually renders. No new components — the existing report picker and
  viewer handle both automatically.

  *Task 2 — Inventory Setting (allow negative stock).* Asked whether this should be per-product
  or store-wide; answer was **store-wide**. Added `StoreSettings.allowNegativeStock` (defaults to
  `true` — before this setting existed, stock was never checked anywhere in the app, so this
  preserves today's behavior for every existing deployment until an admin turns it off), its own
  independent request DTO/endpoint (`InventorySettingsRequest`, `PUT /api/settings/inventory`) —
  same "each admin screen only ever saves the fields it owns" split `StoreSettingsService`
  already uses for Business Details vs. System settings — and a new Admin > Inventory >
  Settings screen (`InventorySettingsComponent`) rather than folding the checkbox into System
  settings. Enforced in two places: `OrderService.checkout` now calls a new
  `validateStockAvailability`, grouping a cart's lines by product id (not line-by-line — a
  discounted and non-discounted line for the same product don't merge, so the check has to sum
  them) and rejecting a sale that would oversell any product when the setting is off; it's
  deliberately called only from the online `checkout()` path, never from the shared
  `completeCheckout()` that `syncOfflineSale` also uses — an offline sale already happened for
  real by the time it syncs, so this setting doesn't change that path's existing "never checked"
  policy. `StockAdjustmentService.create` got the same guard for manual corrections. Audit log
  entries record on/off changes the same as every other settings screen.

  Verified with `verify_java.py` and both `ng build` configurations (dev + production); both
  pass cleanly.

- **2026-09-18 — Five smaller tasks: stock qty on the terminal, Inventory Report moved into the
  Inventory menu, purchase order printing, a Purchase Orders report, and a Supplier Statement.**

  1. **Show stock quantity on Product terminal.** Every product tile in the cashier terminal's
     grid now shows its current stock quantity under the price ("Stock: 12"), or "Out of stock"
     in red at zero or below. A heads-up, not a block — this store may have "Allow negative
     stock" on, so a cashier can still legitimately sell past zero; the tile stays clickable
     either way. `ProductDto`/the frontend `Product` model already carried `stockQuantity`
     end-to-end, so this was template-only (`terminal.component.ts` — works in offline mode too,
     since it reads the same cached `Product` objects). Noticed while doing this that `.text-danger`
     was already referenced in a couple of places (this component's own offline-queue row, the
     receipts list) but never actually defined in `styles.scss`, so those silently rendered in the
     default text color — added the class for real (using the same `--danger-text` token
     `alert-danger` already uses), which fixes those pre-existing spots too.

  2. **Move Inventory Report as submenu to Inventory Menu.** The two Inventory reports (Task 1
     above) were only reachable via the general Admin > Reports picker, mixed in with unrelated
     sales/profit/credit reports. Added a second, smaller picker — `InventoryReportListComponent`
     at `/admin/inventory/reports`, same `REPORT_DEFS` data filtered to `section === 'Inventory'`,
     same target route (`/admin/reports/:key`, still the one shared `ReportViewerComponent` — no
     new viewer needed) — reached from a new "Reports" link under the Inventory sidenav group
     instead. `ReportListComponent`'s own section list dropped `'Inventory'` so those two reports
     show up on one picker, not both. Also fixed a latent instance of the sidebar-accordion bug
     from two passes ago: `SidenavStateService`'s URL-to-group map had no entry at all for
     `/admin/inventory/*`, so landing directly on the (already-shipped) Inventory Settings screen
     via bookmark/refresh opened the wrong accordion group — added a `/admin/inventory` prefix
     that covers both that screen and the new Reports link.

  3. **Print purchase order.** A printable purchase order (`/admin/purchase-orders/:id/print`,
     `PurchaseOrderPrintComponent`) — reached via a "Print" link on both the purchase-order list
     and its detail page. Same light/printable treatment as the sale and return receipts (a dark
     background is a bad idea on paper), but no EFRIS fiscal data — a PO is this store buying from
     a supplier, not a customer-facing fiscal sale. Shows the business letterhead (from
     `StoreSettings`, same as the sale receipt), the supplier's name/contact/address (looked up
     client-side from `SupplierApiService.listAll()` by id — `PurchaseOrderDto` only ever carried
     `supplierName`, and there's no reason to prefer a stale snapshotted address over the
     supplier's current one when printing an order to send them), the line items with quantity
     ordered/unit cost/line total, and a grand total (computed client-side — no `total` field on
     `PurchaseOrderDto`). No backend change.

  4. **Add Purchase order reports.** A new "Purchase Orders" report under Admin > Inventory >
     Reports — every PO placed in a chosen date range (any status/supplier), with order #,
     supplier, status, order/expected dates, line count, qty ordered, qty received, and total
     cost (`Σ quantityOrdered × unitCost` per line — what the order commits the store to paying,
     shown alongside qty received so a reader can see at a glance how much is still outstanding
     without opening it). Built as a normal `REPORT_DEFS` entry (`purchase-orders` endpoint, date
     range only — no register, since a PO isn't tied to one; no category, since one PO can span
     several) — fits the existing generic table exactly, same as Task 1's two reports.
     `PurchaseOrderRepository` gained `findByOrderDateBetweenOrderByOrderDateDesc`; `ReportService`
     gained `purchaseOrders` plus a shared `purchaseOrderRow` builder (reused by Task 5 below);
     `ReportController` gained the endpoint; new `PurchaseOrderReportRow` DTO.

  5. **Add Supplier Statement.** A per-supplier ledger — every PO ever placed with one supplier
     in a date range, oldest first, plus running totals — reached from a new "Supplier statement"
     link under Admin > Inventory > Suppliers, at `/admin/suppliers/statement`. Hand-built
     (`SupplierStatementComponent`) rather than plugged into the generic report framework, same
     "needs a picker + no register/category filter" reasoning as the existing Customer Statement,
     which it mirrors closely (down to reusing the same `ReportApiService`/`ReportFilter`, now
     with an added `supplierId` field alongside the existing `customerId`). Backend:
     `PurchaseOrderRepository` gained `findBySupplierIdAndOrderDateBetweenOrderByOrderDateAsc`;
     `ReportService.supplierStatement` 404s on an unknown supplier id (same convention as
     `customerStatement`) and reuses the same row-builder Task 4 added; `ReportController` gained
     `GET /api/reports/supplier-statement` (`supplierId` required, unlike every other report's
     optional filters — same shape as `/customer-statement`); new `SupplierStatementRow` DTO.

  Verified with `verify_java.py` (213 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **Supplier payments, Sale Orders + Customer payments, and a "Sales" sidenav menu.** Fourier's
  request, verbatim: "Add Supplier payment / Add Sale order and Customer payment (So that we can
  have POS & Sales) / Then let have Sale as Menu and Move Customers into Sales (It becomes
  Submenu)." Four design decisions were confirmed with Fourier up front (all four "(Recommended)"
  options): a Sales Order is a **new, separate order type** mirroring Purchase Orders — no
  register/cashier session involved at all, stock only moves on fulfillment; a Sales Order's
  payment **reuses the existing customer credit/payment system** end to end, so it shows up
  alongside register sales in the same Outstanding Credit report and the same Customer Statement;
  a Supplier payment is **allocated against POs with an outstanding balance**, mirroring the
  customer side exactly; and the new "Sales" sidenav group holds Customers, Customer statement,
  Sale Orders, and Customer payments, replacing the old standalone Customers group.

  **Supplier payment** (`com.posapp.inventory`) — `SupplierPayment`/`SupplierPaymentAllocation`
  are the accounts-payable mirror of `CustomerPayment`/`CustomerPaymentAllocation`: every payment's
  `amount` must be allocated across one or more `PurchaseOrder`s, each allocation capped at what's
  still owed on that PO (`SupplierPaymentService.amountOwedForOrder` — zero for a DRAFT/CANCELLED
  PO, since nothing's been committed to the supplier yet). `SupplierPaymentService.totalCost`
  mirrors `ReportService.purchaseOrderRow`'s own `Σ quantityOrdered × unitCost` calculation, so
  "what's owed" always agrees with what Purchase Orders/Supplier Statement already show.
  `SupplierPaymentController` (`/api/suppliers/{id}/outstanding-balance|outstanding-orders|payments`)
  and a second `SupplierPaymentListController` (`/api/supplier-payments`, store-wide) back a new
  `RecordSupplierPaymentComponent` (`/admin/suppliers/:id/record-payment` — same "manual,
  oldest-first suggested" allocation table and inline printable receipt as `RecordPaymentComponent`)
  and `SupplierPaymentListComponent` (`/admin/supplier-payments`), both reached from Suppliers.
  Purchase Orders and Supplier Statement gained **Paid**/**Outstanding** columns
  (`SupplierPaymentService.amountPaidForOrder`/`amountOwedForOrder`), and a new **Outstanding
  Payables Report** (`/api/reports/outstanding-payables`, `section: 'Inventory'` in
  `report-defs.ts`) mirrors Outstanding Credit Report on the purchasing side.

  **Sale Order** (new `com.posapp.salesorder` package) — `SalesOrder`/`SalesOrderLine` mirror
  `PurchaseOrder`/`PurchaseOrderLine`'s DRAFT → CONFIRMED → (PARTIALLY_FULFILLED →)* FULFILLED /
  CANCELLED lifecycle (`SalesOrderStatus`), created for a customer with no register/cashier session
  anywhere in the flow. No tax modeling (mirrors `PurchaseOrderLine`, deliberately distinct from
  the POS `SaleOrder`/`OrderCalculator`'s inclusive-tax machinery). Confirming an order
  (`SalesOrderService.confirm`) is the moment its whole amount (`Σ quantityOrdered × unitPrice` —
  `SalesOrderService.totalAmount`, a static helper so `CustomerCreditService` can call it without a
  circular Spring bean dependency) becomes something the customer owes: it runs the same
  `CustomerCreditService.assertWithinLimit` credit-limit gate a POS credit sale goes through at
  checkout, and stamps `dueDate` from the customer's `defaultCreditTermDays`, same as checkout
  does. Fulfilling (`SalesOrderService.fulfillLines`) is the only step that moves stock
  (`CatalogService.adjustStock`, gated by the same store-wide "Allow negative stock" Inventory
  setting checkout already enforces) — accepts partial fulfillment across more than one call, same
  as `PurchaseOrderService.receiveLines`. `SalesOrderController` (`/api/sales-orders`, admin-only)
  backs `SalesOrderListComponent`/`SalesOrderFormComponent`/`SalesOrderDetailComponent`
  (`/admin/sales-orders`), mirroring the Purchase Order screens' own list/form/detail split.

  **Widening the customer credit/payment system for Sale Orders** — rather than a new payment
  mechanism, `CustomerPaymentAllocation` gained a second nullable FK, `salesOrder` (alongside the
  existing `order: SaleOrder`), an "exclusive arc": exactly one of the two is ever set on a given
  row, enforced in `CustomerCreditService.recordPayment`, not the entity. A new `OrderType` enum
  (`POS_SALE` / `SALES_ORDER`) discriminates which one a row/allocation/outstanding-order-DTO
  targets. `CustomerCreditService.outstandingForSalesOrder` mirrors `outstandingForOrder` (zero for
  a DRAFT/CANCELLED Sales Order — nothing owed yet, or never will be); `outstandingBalance`,
  `listOutstandingOrders`, and `recordPayment` all now merge POS credit sales and Sales Orders into
  one result rather than reading only `SaleOrder`s. `ReportService.customerStatement`/
  `outstandingCredit`/`overdueCredit` were widened the same way, so a Sales Order shows up
  alongside register sales in the Customer Statement and both credit reports, exactly as promised.
  A new **Sales Orders report** (`/api/reports/sales-orders`, `section: 'Sales'`) lists every Sales
  Order in a date range with its own outstanding amount.

  `RecordPaymentComponent`'s allocation table now shows a **Type** column (POS sale vs. Sales
  order) and threads `orderType` through every allocation it submits — no separate screen needed,
  since the backend already returns one merged, oldest-first list. Two new store-wide payment list
  screens: `CustomerPaymentListComponent` (`/admin/customer-payments` — `CustomerPaymentController`
  gained `GET /api/customer-payments`, `CustomerCreditService.paymentHistory()` no-arg overload)
  and the Supplier one described above.

  **Sidenav reorg** — the old standalone "Customers" group is gone; a new **"Sales"** group (💵)
  holds Customers, Customer statement, Sale orders, Customer payments, and a Sales-scoped Reports
  picker (`SalesReportListComponent`, mirrors `InventoryReportListComponent`'s own pattern —
  `/admin/sales/reports`). `SidenavStateService.GROUP_ROUTE_PREFIXES`'s `'customers'` entry became
  `'sales'` with the widened prefix list. Inventory gained a "Supplier payments" link.

  Backend package layout: `com.posapp.inventory` (Supplier payment — same package as
  `PurchaseOrder`/`Supplier`, since it's purchasing-side accounting) and a new top-level
  `com.posapp.salesorder` (Sale Order — deliberately not `com.posapp.order`, to keep it visibly
  separate from the POS `SaleOrder`/checkout code it never touches). `com.posapp.credit` (existing)
  absorbed the widening. No circular Spring bean dependency anywhere in this chain:
  `SalesOrderService` depends on `CustomerCreditService`, which depends on `SalesOrderRepository`
  (a repository, never `SalesOrderService` itself) plus a static `SalesOrderService.totalAmount`
  helper call — repository/static-method dependencies only ever point one way.

  Verified with `verify_java.py` (236 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **2026-09-18 — Four small follow-ups: new-customer-from-Sale-Order, Supplier Tax ID, report
  "back" links, Stock Movement History before/after quantities.** Task, verbatim: "1. can add new
  Customer from New sale order form 2. add TAX ID on supplier 3. all link to back report shoudl go
  to the parent not to the POS reports 4. On Stock Movement History, no pos and sale transactions,
  also add the previous quantity and current quantity after transaction."

  **1. New customer from the Sale Order form** — `SalesOrderFormComponent` gained an inline
  "+ New customer" panel next to its Customer picker (the picker disables while it's open), the
  same pattern as `PurchaseOrderFormComponent`'s own "+ New supplier" panel: name/phone/email only
  (no credit fields — those stay a deliberate decision made on the full Admin > Customers form),
  and saving selects the new customer on the draft right away.

  **2. Tax ID on Supplier** — `Supplier` gained a `taxId` field, the same nullable/purely-
  informational treatment as `Customer.taxId` (nothing in purchasing math reads it). Threaded
  through `SupplierDto`/`SupplierRequest`/`SupplierService.applyRequest` on the backend and
  `models.ts`/`SupplierPayload`/`SupplierFormComponent` (new "Tax ID" input) on the frontend, plus a
  "Tax ID" column on the supplier list for parity with the Customer list.

  **3. Report "back" links go to their actual parent, not always POS Reports** —
  `ReportViewerComponent` is one shared screen for every report regardless of which picker linked
  to it (`ReportListComponent` at `/admin/reports`, `InventoryReportListComponent` at
  `/admin/inventory/reports`, `SalesReportListComponent` at `/admin/sales/reports`), but its "back"
  link was hardcoded to `/admin/reports` no matter which picker actually owned the report. Added
  `reportListRouteForSection()` in `report-defs.ts`, mapping a `ReportDef`'s own `section` to its
  owning picker's route, and `ReportViewerComponent` now derives its back link from that instead of
  assuming POS.

  **4. Stock Movement History: no POS/Sale rows (already true); added Before/After quantity
  columns** — re-verified `ReportService.stockMovementHistory` only ever merges
  `StockAdjustmentRepository`/`StockReceiptRepository`; sales and Sale Order fulfillment were
  already never a source (see `StockMovementRow`'s own javadoc on why) and stayed that way. Added
  `stockBefore`/`stockAfter` snapshot columns to `StockMovementRow`: `StockAdjustment` already had
  these fields, but `StockReceipt` didn't, so it gained them and `PurchaseOrderService.receiveLines`
  now snapshots the product's stock immediately before/after each `CatalogService.adjustStock` call,
  same convention `StockAdjustmentService` already used for adjustments. Flowed through
  `ReportController`'s export headers and `report-defs.ts`'s `stock-movement` column list ("Before"
  / "After", both `int`-formatted).

  Verified with `verify_java.py` (236 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **2026-09-18 — Bug fix: can't confirm a Sale Order for "Walk-in Customer".** Bug: "not
  confirming sale order => Customer 'Walk-in Customer' is not enabled for credit purchases."
  Root cause is by design, not a defect in the credit check itself: confirming a Sale Order always
  puts its whole amount on the customer's credit tab (`SalesOrderService.confirm` calls
  `CustomerCreditService.assertWithinLimit` with the order's full total, unconditionally — there's
  no "pay now" path for a Sale Order the way there is at POS checkout), so the seeded, always-
  non-credit "Walk-in Customer" (`Customer.walkIn`) can never be confirmed. It was still offered in
  `SalesOrderFormComponent`'s customer picker, so a user could build out a whole draft against it
  and only discover this dead end at Confirm.

  Fix (frontend only, `SalesOrderFormComponent`): Walk-in Customer is now excluded from the New/Edit
  Sale Order form's customer dropdown entirely. An existing draft that already has it set (saved
  before this fix) instead shows an inline warning identifying the problem and what to do about it
  ("pick a different customer, or enable credit for one from Admin > Customers first") rather than
  silently failing later. The draft can still be saved either way — only *confirming* requires a
  credit-enabled customer — so this doesn't block editing an already-broken draft to fix it.

  Verified with `verify_java.py` (236 files — no backend change this pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **2026-09-18 — Follow-up: Sale Order confirmation no longer requires credit-enabled at all
  (supersedes the Walk-in Customer fix above).** Fourier's correction after the previous entry:
  "the POS Module stores data into sale_order table while Sale Module store data into sales_order
  table. The rules of enabling credit on customer should only be required in POS Module not in
  Sale Module." The previous fix treated Walk-in Customer's block as the whole problem and worked
  around it in the frontend; this pass removes the actual cause. `SalesOrderService.confirm` no
  longer calls `CustomerCreditService.assertWithinLimit` at all — confirming a Sale Order now just
  stamps `dueDate` and moves DRAFT → CONFIRMED, for any customer, regardless of
  `Customer.creditEnabled` or `creditLimit`. Only `OrderService.checkout` (POS, the `sale_order`
  table) still gates on those two fields; the two modules' credit rules are now fully independent,
  per the user's own framing of the two tables.

  `SalesOrderFormComponent`'s customer picker reverted back to listing every customer, including
  Walk-in Customer — the exclusion/warning added in the previous pass is no longer needed since
  there's nothing left for it to warn about. `CustomerCreditService` still reads Sales Orders via
  `SalesOrderRepository`/`SalesOrderService.totalAmount` (unchanged) purely to compute outstanding/
  paid amounts for reporting and payment allocation — that part of the "reuse the existing credit/
  payment system" design from the original Sale Order feature is untouched, so a confirmed Sale
  Order still shows up in Outstanding/Overdue Credit reports, the Customer Statement, and Record
  Payment exactly as before; only the confirmation-time gate was removed.

  Verified with `verify_java.py` (236 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **2026-09-18 — Six-item batch: why Customer Payment doesn't show POS payments, payment mode on
  Customer Payment, Record Payment on Purchase Order, Sale Order Return, Purchase Return, and a
  unified Stock Movement History.**

  1. **Why doesn't Customer Payment show payments made in the POS Module?** Not a bug — the two
     are deliberately different concepts. `CustomerPayment` is only ever created by
     `CustomerCreditService.recordPayment` (the Record Payment screen), i.e. a *later* settlement
     against something already owed — a Sale Order or a POS credit sale being paid down after the
     fact. A POS sale paid in full **at the register, at time of sale** never creates a
     `CustomerPayment` at all: it's captured directly as one or more `Payment` rows on the
     `SaleOrder` itself (`OrderService.completeCheckout`), the simpler "tendered at checkout"
     concept that never needed later allocation against an outstanding balance because nothing was
     ever left outstanding. Both are visible, just in different places: a `SaleOrder`'s own
     `Payment`s show on its receipt and the Z-report/shift reports; `CustomerPayment`s show on the
     Customer Payments list and a customer's own statement. No code changed for this item — it's
     the existing, intended design, documented here since it wasn't obvious from the UI alone.

  2. **Add payment mode to Customer Payment.** `CustomerPayment` gained a `method` field (`CASH`,
     `CHEQUE`, etc.) — reusing the same admin-configurable `PaymentMethodEntity`/code system
     checkout and POS refunds already validate against
     (`PaymentMethodService.validateActiveCodeOrThrow`), rather than inventing a second payment-
     method list. `RecordPaymentRequest.method` is now required; `CustomerCreditService
     .recordPayment` validates it the same way `SupplierPaymentService`/`ReturnsService` already
     validate theirs. The Record Payment screen (`record-payment.component.ts`) gained a Payment
     method dropdown (defaults to the first active method), a Method column on its own history
     table and the store-wide Customer Payments list, and a line on the printable receipt.

  3. **Add Record Payment shortcut to the Purchase Order view/list.** Every `PurchaseOrderDto` now
     carries its own `amountPaid`/`amountOutstanding`, computed by `PurchaseOrderService.toDto`
     via `SupplierPaymentService` — the same "every order DTO knows its own paid/outstanding
     figures" shape `SalesOrderDto` already had for Sale Orders. A "Record payment" button/link
     shows on the Purchase Order detail page and each outstanding row of the Purchase Orders list
     (`*ngIf="amountOutstanding > 0"`), jumping straight to that supplier's own Record Supplier
     Payment screen — which already lists every one of that supplier's outstanding POs to allocate
     against, this one included, so no new allocation UI was needed.

  4. **Sale Order Return** (Task: "Sale Order Return (Cancellation after Confirmation and Payment)
     Sale Module"). Mirrors the existing POS `ReturnsService`/`SalesReturn` pattern
     (`com.posapp.returns`) but scoped to a CONFIRMED/PARTIALLY_FULFILLED/FULFILLED `SalesOrder`:
     new `SalesOrderReturn`/`SalesOrderReturnLine` entities, `SalesOrderReturnService`, controller,
     and DTOs — partial, per-line, with an optional reason. Only the *fulfilled* portion of a line
     can be returned (nothing was ever physically handed over beyond what was fulfilled); each
     return reverses stock for the returned quantity (`CatalogService.adjustStock(+qty)`) and never
     touches the Sale Order's own status — a return is a side-ledger, exactly like a POS return
     leaves the original sale's status alone. Fourier's own choice, asked directly: the money side
     is **informational only** — a return that leaves the customer having overpaid is tracked as a
     "refund due" figure (`CustomerCreditService.refundDueForSalesOrder`), not an instruction for
     the system to actually pay anything out, so unlike a POS return there's no `refundMethod`
     field at all. `CustomerCreditService.outstandingForSalesOrder` was widened to subtract
     returned amounts before subtracting what's paid, and `SalesOrderDto` grew `amountPaid`/
     `amountOutstanding`/`amountReturned`/`refundDue` fields (computed by `SalesOrderService.toDto`
     via `CustomerCreditService` — safe to depend on it there since `CustomerCreditService` never
     injects a `SalesOrderService` bean back, only reads `SalesOrderRepository`/the static
     `totalAmount` helper, so there's no circular Spring dependency). Frontend: a "Process a
     return" panel and return history section on `SalesOrderDetailComponent` (only shown once
     there's fulfilled-but-unreturned quantity), Paid/Outstanding/Returned/Refund-due stat tiles,
     an Outstanding column on the Sale Orders list, and a store-wide Sale Order Returns list
     screen (`/admin/sales-order-returns`) mirroring the POS Returns list.

  5. **Purchase Return** (Task: "Supplier Purchase Return (Cancellation after Confirmation and
     Payment) Inventory Module"). The purchasing-side mirror of the above: new
     `PurchaseReturn`/`PurchaseReturnLine` entities (`com.posapp.inventory`),
     `PurchaseReturnService`, controller, and DTOs — partial, per-line, with a reason. Only the
     *received* portion of a line can be returned; reverses stock for the returned quantity in the
     opposite direction from a Sale Order return (`adjustStock(-qty)`, since it's leaving the store
     back to the supplier) and never touches the Purchase Order's own status. Same informational-
     only money model, confirmed separately for this direction too: `SupplierPaymentService
     .amountOwedForOrder` now subtracts returned amounts before subtracting what's paid, and a new
     `refundDueFromSupplierForOrder` tracks what the *supplier* now owes the store back once a
     return brings the net owed below what's already been paid to them. `PurchaseOrderDto` grew
     matching `amountReturned`/`refundDue` fields alongside the `amountPaid`/`amountOutstanding`
     item 3 added. Frontend: a "Process a return" panel and return history section on
     `PurchaseOrderDetailComponent`, Returned/Refund-due-from-supplier stat tiles, and a store-wide
     Purchase Returns list screen (`/admin/purchase-returns`).

  6. **Unify Stock Movement History across every stock-affecting source** (Task: "Any transaction
     affecting stock should be shown to Stock Movement History (Pos sales, Sale orders, Purchases
     orders)"). `ReportService.stockMovementHistory` went from two structured sources
     (`StockAdjustment`, `StockReceipt`) to seven: those two, plus POS sale (`OrderLine`, which
     gained `stockBefore`/`stockAfter` snapshot fields populated in
     `OrderService.completeCheckout`), POS return (`SalesReturnLine`, same two fields, populated in
     `ReturnsService.processReturn`), Sale Order fulfillment (a new `SalesOrderFulfillment` entity
     — one row per product per `SalesOrderService.fulfillLines` call, the fulfillment-side
     counterpart to `StockReceipt`, needed because `SalesOrderLine.quantityFulfilled` is only a
     cumulative running total with no per-event timestamp), and this batch's own two new return
     types, Sale Order return and Purchase return (their `SalesOrderReturnLine`/`PurchaseReturnLine`
     entities each got the same `stockBefore`/`stockAfter` pair). Also widened the scope beyond the
     task's three literal examples to include POS/Sale Order/Purchase returns too — the general
     principle ("any transaction affecting stock") would have been inconsistently applied
     otherwise, especially with two of those return features being built in this very batch.
     `StockMovementRow`'s javadoc, which incorrectly claimed sales/returns never touch stock (they
     always have — `OrderService.completeCheckout` and `ReturnsService.processReturn` both call
     `CatalogService.adjustStock`; the claim was simply wrong when it was written and this pass
     both fixes the report and corrects the comment), is now accurate: seven `type` values
     ("Adjustment", "Purchase receipt", "POS sale", "POS return", "Sale order fulfillment", "Sale
     order return", "Purchase return"), one row per dated event, still floored by the "no product
     snapshot, no row" rule for any line whose product was null or predates its snapshot fields.
     The frontend needed no changes at all — `report-defs.ts`'s `stock-movement` column set is
     already generic (`type`/`reference`/`note` as plain text columns), so the three new source
     types just render.

  Verified with `verify_java.py` (256 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **2026-09-18 — Three-item follow-up: merge POS payments into Customer Payment history, a
  track-stock attribute on Product, and require a customer on every POS sale.**

  1. **Customer Payment history now includes POS-tendered payments too.** Fourier's own follow-up
     to item 1 of the previous batch: "no POS payment for cash sale. But when I sale with Credit
     and record payment after it appear" — confirming that behavior was correct but asking the two
     kinds of payment to live in one list. Rather than forcing a checkout `Payment` row into the
     `CustomerPayment` entity/allocation model, `CustomerPaymentDto` gained a `source: PaymentSource`
     discriminator (`SETTLEMENT` vs `POS_SALE`, a new enum) and a second static factory,
     `fromPosPayment(Payment, SaleOrder, BigDecimal)`, that synthesizes a DTO row directly from a
     checkout `Payment` and its parent `SaleOrder` — `id`/`recordedByName` null (nothing was
     "recorded", it was tendered at checkout), a single synthetic allocation pointing back at the
     sale itself so the "Applied to" column still reads sensibly. `OrderRepository` gained
     `findAllCompletedWithCustomer`/`findCompletedForCustomer` (completed, customer-attached sales,
     newest first); `CustomerCreditService.paymentHistory()`/`paymentHistory(Long)` now merge actual
     `CustomerPayment` rows with these synthesized POS-sale rows, sorted by date descending, with a
     small balance cache so a customer with many rows doesn't recompute `outstandingBalance` once
     per row. Frontend: `CustomerPayment.id` widened to `number | null`, new `source` field; both the
     store-wide Customer Payments list and the per-customer history table on Record Payment gained a
     Type column (badge: "POS sale" vs "Settlement").

  2. **Track stock attribute on Product.** Fourier: "Add track stock attribute on Product." Asked
     what turning it off should actually do; chosen answer: skip stock deduction/checks entirely — a
     non-tracked product behaves like a service or made-to-order item, always sellable regardless of
     `stockQuantity`, and selling it never changes that number. New `Product.trackStock` (boolean,
     default `true`, so every existing product is unaffected). Checked in exactly the three places
     the confirmed answer named: `OrderService.validateStockAvailability`/`completeCheckout` (POS
     checkout — skips the stock-availability check and the decrement for a non-tracked line),
     `SalesOrderService.fulfillLines` (Sale Order fulfillment — same skip, and no
     `SalesOrderFulfillment` stock-movement row is created for it), and
     `CatalogService.listLowStock`/`ProductRepository` (a new
     `findByActiveTrueAndTrackStockTrueAndStockQuantityLessThanOrderByStockQuantityAsc` query
     excludes non-tracked products from low-stock alerts entirely). To keep a return the mirror image
     of the sale it reverses, `ReturnsService.processReturn` and `SalesOrderReturnService` also skip
     restocking a non-tracked line, since nothing was ever deducted for it. Purchase-order receiving
     and manual stock adjustments are deliberately left alone — an admin's own explicit inventory
     action, not an automatic side effect of a sale. Frontend: a "Track stock for this product"
     checkbox on the product form (hides the stock-quantity field when off), the admin product list
     shows "Not tracked" instead of a number, and the POS terminal's product tiles simply omit the
     stock caption for a non-tracked product instead of ever showing "Out of stock".

  3. **Every POS sale now requires a customer.** Fourier, mid-session: "Never allow any sale
     transaction even into POS without Customer information." The terminal already defaults every
     new cart to the seeded "Walk-in Customer" (an earlier task replaced the old null/"No customer"
     dropdown option with this), so in normal use a cart already always carries *some* customer —
     this closes the remaining gap for anything that could reach checkout without going through that
     flow. `OrderService.checkout` now throws a 400 ("A customer is required for every sale...") if
     `order.getCustomer()` is still null at checkout time, checked only on the online path — same
     exemption reasoning as `validateStockAvailability`'s: `syncOfflineSale` replays a sale that
     already genuinely happened while offline, so rejecting the sync over a missing customer would
     only strand it, not undo anything. Frontend: `CheckoutComponent` mirrors the guard client-side
     (a clear message and a disabled "Complete sale" button when `order.customerId` is null) so a
     cashier gets an immediate, obvious reason rather than a raw error from the backend.

  Verified with `verify_java.py` (257 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **2026-09-19 — Manufacturing Module (new).** Fourier: "We are now good with POS and Sale Module,
  Let move on with adding more Module for ERP. Add Manufacturing Module." Scoped via his own
  numbered list — "1.Bill of Materials 2.Production plans 3.Manufacturing orders 4.Indirects Costs
  5.Workstations 5.Settings (Production routings etc)" — and, when asked to pick between a simpler
  or fuller design for Production Plans and Indirect Costs, "Am new on Manufacturing but I need
  full features," read as "the fuller option, everywhere." A brand-new `com.posapp.manufacturing`
  backend package plus a full Angular admin section, six sub-areas:

  1. **Bill of Materials (`Bom`/`BomLine`)** — the recipe for one finished-good `Product`: a list
     of component `Product`s and, per component, `quantityPerUnit` — deliberately "how many of
     this component to make ONE unit of the finished good," not a batch-yield ratio, so every
     quantity in this app stays a plain whole-number `int` with zero rounding (see `BomLine`'s
     javadoc). Deliberately single-level — a component is always a plain `Product`, never another
     product's own Bom — to keep cost roll-up and stock consumption a one-pass calculation with no
     cycle detection; `Bom`'s javadoc documents this as the extension point if sub-assemblies are
     ever wanted. A product can have more than one Bom (e.g. an alternate recipe); `active` marks
     the default. `BomDto.estimatedUnitMaterialCost` sums `component.costPrice * quantityPerUnit`
     at today's prices — an estimate, refreshed live, distinct from the cost a real production run
     actually locks in (below). Frontend: a searchable finished-good picker plus a searchable
     component-line editor, both mirroring the Purchase Order form's product-search convention.

  2. **Workstations (`Workstation`)** and **Settings/Routings (`Routing`/`RoutingOperation`)** —
     Fourier's own naming, "Settings (Production routings etc)." A flat admin-managed lookup
     (`Workstation`: code/name/`hourlyRate`, same shape as `PaymentMethodEntity`/`TaxRate`) and an
     ordered list of named operations (`Routing`: "Cut," "Assemble," "Paint," "Pack," each
     optionally at a workstation, with `estimatedMinutesPerUnit`). Both are entirely optional — a
     Bom without a routing still works end to end for materials-in/finished-goods-out; a routing
     only adds a labor/time estimate on top. A `ManufacturingOrder` snapshots its Bom's routing at
     confirm time, the same "lock in what was referenced" convention as every other order type in
     this app. Frontend: simple CRUD forms for Workstations; a nested add/remove-row operation
     editor for Routings.

  3. **Indirect Costs (`OverheadCost`/`OverheadAllocationMethod`)** — Fourier's own term for
     overhead, confirmed (via "full features") as named cost pools rather than one flat store-wide
     percentage: an admin defines any number of pools (e.g. "Electricity," "Factory rent share"),
     each with its own `allocationMethod` — `FLAT` (a fixed total for the whole order, spread
     across its runs proportional to how much of the planned quantity each run produced, so a
     fully-produced order's runs always sum to exactly the flat rate), `PER_UNIT` (rate × quantity
     produced this run), or `PERCENT_OF_MATERIAL_COST` (rate% × that run's own material cost).
     Picked pools are snapshotted onto `ManufacturingOrderOverhead` (name/method/rate) at order
     creation, so a later edit to the admin record never rewrites a past order's own cost — the
     same convention `Payment.method`/`OrderLine.taxRatePercent` already use.

  4. **Manufacturing Orders (`ManufacturingOrder`/`ManufacturingOrderRun`/
     `ManufacturingOrderConsumption`)** — the same two-stage, run-based lifecycle as
     `PurchaseOrderStatus`/`SalesOrderStatus`: DRAFT (freely editable) → `confirm` (locks it,
     snapshots the routing) → one or more `produce` calls (PARTIALLY_PRODUCED) → fully produced
     (PRODUCED), or `cancel` from DRAFT/CONFIRMED. Each `produce` call is a real production run: it
     computes required component quantities via `BomService.requiredComponentQuantities`, checks
     stock (unless "Allow negative stock" is on, same setting Purchase/Sales Orders already
     respect), decrements every stock-tracked component (`Product.trackStock` — an untracked
     component is never checked or adjusted, same convention as POS/Sale Order checkout),
     increments the finished good's stock, and computes that run's own material/overhead/total
     cost, snapshotting a `stockBefore`/`stockAfter` pair for every stock-tracked movement so this
     run reads correctly forever after. The Inventory Report's Stock Movement History now includes
     two more source types, "Manufacturing output" and "Manufacturing consumption" (nine sources
     total — see `StockMovementRow`'s updated javadoc). Frontend: a create form (pick a Bom,
     planned quantity, optional overhead pools) and a detail screen with confirm/produce/cancel
     actions, a live cost breakdown (material/overhead/total/unit cost), and a run history table
     with per-run component consumption.

  5. **Production Plans (`ProductionPlan`/`ProductionPlanLine`)** — full MRP-style, per "I need
     full features": `ProductionPlanService.generate` aggregates demand from every unfulfilled
     line on a CONFIRMED/PARTIALLY_FULFILLED back-office Sale Order (not POS sales — those settle
     instantly with no "unfulfilled" concept) plus a caller-supplied low-stock threshold (mirroring
     `CatalogService.listLowStock`'s own threshold-per-call shape rather than a stored per-product
     reorder point), for every product with an active Bom. `suggestedQuantity` = shortfall against
     sales demand + whatever's still needed to top current stock up to the threshold after that
     demand is covered. Deliberately does **not** explode multi-level BOMs and does **not**
     auto-generate Purchase Orders for shortfalls — both documented as scope boundaries in
     `ProductionPlan`'s class javadoc, left for a future pass. A generated plan is a stored, dated
     snapshot (not a live filter); each line shows live component shortfalls (recomputed against
     today's stock every time the plan is viewed, not frozen at generation time — see
     `ProductionPlanDto`'s javadoc for why) and a "Generate order" action that converts one
     reviewed line straight into a real DRAFT Manufacturing Order. Frontend: a list with an inline
     "Generate a new plan" panel, and a detail screen with the demand/shortfall table and per-line
     order generation.

  All six areas are ADMIN-only, reachable from a new "Manufacturing" sidenav group (same
  accordion/admin-gated treatment as Inventory/Sales/Users/Settings) with sublinks for Bill of
  Materials, Production Plans, Manufacturing Orders, Workstations, Routings, and Indirect Costs.

  Verified with `verify_java.py` (305 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **Accounting Module** (Fourier's own request: "We need to add Accounting module. Then all POS,
  Sale, Inventory and Manufacturing module will be integrated when accounting is enabled") — a
  full double-entry bookkeeping engine (`com.posapp.accounting`), off by default and gated behind
  one new store-wide switch (`StoreSettings.accountingEnabled`, Admin > Accounting > Settings) so
  a store that never turns it on gets no journal entries and pays no cost, exactly today's
  behavior unchanged.

  1. **Chart of Accounts (`Account`/`AccountType`)** — the five standard types (Asset, Liability,
     Equity, Revenue, Expense). Turning Accounting on for the first time seeds a standard starter
     chart (`AccountService.seedDefaultChartIfNeeded`, idempotent — a no-op once any account
     exists): Cash, Accounts Receivable, Inventory, Accounts Payable, Tax Payable, Accrued
     Manufacturing Overhead, Owner's Equity, Sales Revenue, Cost of Goods Sold, Inventory
     Shrinkage Expense, Operating Expenses, and a Suspense account (see point 4 below). The dozen
     accounts every other module posts to automatically are flagged `systemAccount` and can't be
     deactivated; everything else (including the two starter accounts nothing posts to
     automatically — Owner's Equity, Operating Expenses) is freely editable, and an admin can add
     more at any time. Frontend: a grouped-by-type list, a create/edit form, and a per-account
     dated running-balance ledger view.

  2. **Journal Entries (`JournalEntry`/`JournalEntryLine`)** — every posting, automatic or manual,
     is one balanced entry (its lines' debits and credits always total exactly the same amount).
     Admin > Accounting > Journal Entries lists every entry (tagged by source — "POS sale,"
     "Purchase receipt," "Manual," etc.) with a detail view of its lines, plus a "New manual
     entry" screen (`JournalEntryService.createManual`) for one-off adjustments no module posts by
     itself — recording an owner contribution, paying rent, or settling an actual utility bill
     against the Accrued Manufacturing Overhead accrual point 6 below creates (both validated
     client-side and, as the real source of truth, server-side: at least two lines, exactly one of
     debit/credit set per line, and the totals must match).

  3. **Automatic posting, one call per existing module** — `AccountingService` is the one class
     every other module calls into, right after its own real side effect has already happened and
     saved, never before: `OrderService.completeCheckout` (POS sale — cash/credit split, revenue,
     tax, COGS/inventory relief for stock-tracked lines only), `ReturnsService.processReturn` (POS
     return — full reversal), `SalesOrderService.fulfillLines` (Sales Order fulfillment — revenue/
     COGS for exactly the quantity fulfilled that call, COGS priced at the product's live
     `costPrice` since `SalesOrderLine` carries no cost snapshot of its own, unlike a POS
     `OrderLine`), `SalesOrderReturnService.processReturn` (Sales Order return), `PurchaseOrder
     Service.receiveLines` (purchase receipt — Inventory debit / Accounts Payable credit),
     `PurchaseReturnService.processReturn` (purchase return), `SupplierPaymentService.record
     Payment` and `CustomerCreditService.recordPayment` (both settlements — every tender method,
     cash or otherwise, lands in the one Cash account, since this app has no per-method bank/
     clearing sub-accounts), `StockAdjustmentService.create` (a positive delta is a recount gain,
     a negative one a real shrinkage loss, both priced at the product's `costPrice`), and
     `ManufacturingOrderService.produce` (see point 6 below for why only overhead absorption
     posts). Every posting call is a no-op when Accounting is disabled, and every one defends
     itself with the Suspense-account fallback in point 4 rather than ever throwing — a posting
     happens strictly after its real-world event, so it must never roll one back.

  4. **A posting can never block or undo the real transaction it's recording.** If a would-be
     entry's lines don't balance to the exact penny — which shouldn't happen from how any of the
     `postXxx` methods above are built, but could in principle drift from an edge case like an
     offline-synced sale's client-estimated price/tax not exactly matching the server's final
     recomputed figures (see `SaleOrder.syncNote`) — `AccountingService.post` plugs the
     difference into a dedicated Suspense account and logs a warning rather than throwing. The
     Balance Sheet report flags this directly (point 5 below) so it's never silently missed.

  5. **General Ledger, Trial Balance, Profit & Loss, and Balance Sheet (`AccountingReportService`/
     `AccountingReportController`)** — the four standard reports, all built from `JournalEntryLine`
     rows and kept deliberately separate from the pre-existing POS/Inventory/Sales report engine
     (`com.posapp.reports`), which they share no data with. The Ledger and Trial Balance take a
     date range (mirroring `ReportController`'s own `from`/`to` → `Instant` conversion, defaulting
     to the last 30 days); the Balance Sheet is a snapshot as of one date (default: today) and
     carries a live `currentEarnings` figure (all-time Revenue minus Expenses not yet closed to
     Owner's Equity) plus a `balanced` sanity flag comparing Assets against Liabilities + Equity +
     current earnings. Frontend: a per-account ledger screen (reached from the Chart of Accounts
     list) and three report screens under a new "Accounting" sidenav group.

  6. **Manufacturing's own posting is deliberately overhead-only.** This app has one pooled
     `Product.stockQuantity`/`costPrice` pair, not separate raw-material/WIP/finished-goods GL
     accounts, so a production run's material consumption and finished-goods output are an exact
     wash within the one Inventory account — nothing to post. Only the overhead actually absorbed
     into a run creates a real movement: Inventory debit / a new `ACCRUED_MANUFACTURING_OVERHEAD`
     liability account credit (a run with zero overhead posts nothing at all). That liability sits
     there until an admin settles it with a manual journal entry once the real bill is actually
     paid (point 2 above) — chosen over inventing a permanently-unreconciled "Applied Overhead"
     contra-expense account, since this app has no separate utility-bill-payment feature yet.

  Reachable from a new "Accounting" sidenav group (same accordion/admin-gated treatment as every
  other module here) with sublinks for Chart of Accounts, Journal Entries, Trial Balance,
  Profit & Loss, Balance Sheet, and Settings.

  Verified with `verify_java.py` (328 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **Accounting Reports expansion** (Fourier's own follow-up request, a six-group list: Accounting
  Reports, Journals Reports, Expenses Segmented/Periodic Reports, Incomes Segmented/Periodic
  Reports — 24 report menu items in total, plus two new entry modules he scoped via four
  `AskUserQuestion` choices, all "Recommended": add dedicated Expense/Income entry screens rather
  than deriving them from existing data; add Cost Centers now; a simple Assets report rather than
  a full fixed-asset register; and a general Tax Report/Declaration summary rather than a
  jurisdiction-specific filing format).

  1. **Cost Centers (`CostCenter`)** — a new, deliberately flat lookup (code + name + active, no
     hierarchy — same shape as `Account` itself), optional on every `JournalEntryLine` (nullable
     `costCenter` field), a manual journal entry's own lines (`JournalEntryFormComponent`'s new
     per-line dropdown), and the new Expense/Income entries below (tagged on the expense/revenue
     side of the posting only, never the Cash/Payable/Receivable offset — see
     `AccountingService.postExpense`/`postIncome`'s own javadoc). Admin > Accounting > Cost
     Centers is its own CRUD screen; Admin > Journals Reports > Cost Centers is a separate
     *activity* report (`AccountingReportService.costCenterActivity`) showing each one's own
     debit/credit/net totals for a date range.

  2. **Expenses / Incomes (`Expense`/`Income`)** — the "Add new Expense & Income entry screens"
     choice: a category (an Expense-type/Revenue-type `Account` — no separate category taxonomy),
     a payee (`PayeeType` — Vendor/Staff/Client/Other, backed by a `Supplier`/`AppUser`/`Customer`
     lookup or free text, always snapshotted into `payeeName` so a later rename or deletion never
     changes the record), an amount, a date, and a `paid`/`received` flag that picks the cash side
     of the posting: `true` (the default) hits `CASH` immediately, `false` posts a bill/receivable
     against `ACCOUNTS_PAYABLE`/`ACCOUNTS_RECEIVABLE` instead. Each save posts its own balanced
     journal entry (`AccountingService.postExpense`/`postIncome`, sourceType `EXPENSE`/`INCOME`)
     the same way every other module's own integration does — see `AccountingService`'s class
     javadoc for the full caller list, now including these two. Admin > Accounting > Expenses/
     Incomes are read-only lists (each row already posted) plus a "New" entry form.

  3. **Accounting Reports** (Tax Report, Tax Declaration, Income Statement, Balance Sheet, Profit
     & Loss, Financial Transactions, Cash Flow Report, Assets) — Income Statement is an alias
     route to the existing Profit & Loss screen (the standard alternate name for the same report);
     Balance Sheet/Profit & Loss are the existing point-5 reports, just re-listed under this new
     sidenav group. Financial Transactions (`AccountingReportService.financialTransactions`) is
     every journal entry line across every account in a date range, flattened into one table — a
     multi-account counterpart to the existing single-account Ledger. Cash Flow Report
     (`AccountingReportService.cashFlow`) uses the **direct method** (chosen over indirect, which
     would need non-cash adjustments like depreciation this app doesn't model): every movement of
     the `CASH` account is Operating by default, or Financing when it's the cash side of a manual
     entry whose other line hits `OWNERS_EQUITY` (an owner contribution/draw); Investing is always
     empty (not modeled). Tax Report and Tax Declaration both open the same screen
     (`TaxSummaryDto`) — a general summary, not tied to any tax authority's filing format: tax
     collected, broken down by `OrderLine.taxCode`/`taxRatePercent` (the same per-line tax the POS
     Reports package already tracks, just grouped by rate here), against the `TAX_PAYABLE`
     account's own net ledger movement for the period (this app models no input/purchase tax, so
     there's no separate "tax paid" figure to net against it). Assets is a simple listing of
     Asset-type accounts and their as-of-date balances, reusing the Balance Sheet's own
     computation (`AccountingReportService.assets`) rather than a full fixed-asset register with
     depreciation.

  4. **Journals Reports** (Trial Balance & Nets, Trial Balance, Trial Nets, Ledger, Cost Centers,
     Journals Report, Chart of Accounts Directory) — the first three all read the *exact same*
     `/api/accounting/reports/trial-balance` data (`TrialBalanceComponent` now takes a route-level
     `mode`: `'totals'` hides the Balance column, `'nets'` hides Debit/Credit, `'full'` shows all
     three) rather than three near-identical backend computations. Ledger and Chart of Accounts
     Directory both link straight to the existing Chart of Accounts screen (pick an account there
     for its own Ledger; the account list itself already *is* the directory) — no new backend or
     component needed for either. Journals Report (`AccountingReportService.journals`) is the
     existing Journal Entries list, date-scoped. Cost Centers here is the activity report from
     point 1 above.

  5. **Expenses/Incomes Segmented + Periodic Reports** (by Category/Vendor/Staff/Client, and
     Daily/Weekly/Monthly/Yearly — 8 report menu items per direction, 16 total) — collapsed into
     **two** backend methods (`AccountingReportService.expensesGrouped`/`incomesGrouped`, one
     `groupBy` parameter: `category`/`vendor`/`staff`/`client`/`daily`/`weekly`/`monthly`/`yearly`)
     and **one** frontend component (`ExpenseIncomeReportComponent`, driven by route data) instead
     of sixteen near-duplicate reports — the same "config-driven, one component renders every
     variant" architecture the original POS Reports package established with `REPORT_DEFS`/
     `ReportViewerComponent`, just sized to how simple these particular reports are (one label
     column, one amount column — no category/register filters or Excel/PDF export needed).

  Reachable from a single "Reports" sublink inside the existing "Accounting" sidenav group,
  opening one picker screen (`AccountingReportMenuComponent` — a row of buttons per section, click
  one to open its own report) rather than putting all 24 report links directly in the sidebar.
  This went through three revisions after this same pass's first attempt, which put four *extra*
  sidenav accordion groups next to "Accounting" (one per report section): Fourier's follow-up
  ("Move those report to the Report Menu and accounting Report... same as we did for POS Report")
  asked for exactly the POS Reports picker pattern instead — see that pattern's own
  `ReportListComponent`/`InventoryReportListComponent` — which first landed as *two* sidenav
  sublinks/picker screens ("Accounting Report" for the eight Accounting Reports, "Report Menu" for
  the other 23); Fourier then asked to fold the two together ("Move also Accounting Report to the
  report Menu"), so the standalone `AccountingReportListComponent` screen, its route, and its
  sidenav link were retired and its eight entries (Tax Report, Tax Declaration, Income Statement,
  Balance Sheet, Profit & Loss, Financial Transactions, Cash Flow Report, Assets) became a fourth
  "Accounting Reports" section inside `AccountingReportMenuComponent`, alongside Journals Reports,
  Expenses Reports, and Incomes Reports — all 24 reports under their own section headers on one
  screen, the same "grouped by section" layout `ReportListComponent` itself uses. Fourier then
  asked to rename the sublink/screen from "Report Menu" to just "Reports," which is its current
  name — the route path (`admin/accounting/reports/menu`) and component name
  (`AccountingReportMenuComponent`) were left unchanged, only the user-facing label and the
  screen's own `<h3>` heading changed. The original
  "Accounting" group also gained Cost Centers, Expenses, and Incomes sublinks alongside its
  existing Chart of Accounts/Journal Entries/Settings.

  Verified with `verify_java.py` (355 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **Report drill-down details + Excel/PDF export** (Fourier's own follow-up: "The report show the
  summary, Add all details and Export to Excel and Pdf") — every one of the 24 accounting reports
  (everything under "Reports" except the two that just link to the existing Chart of Accounts
  screen, Ledger and Chart of Accounts Directory) now does two more things beyond its original
  summary:

  1. **Drill-down detail.** A report row that summarizes more than one underlying transaction
     (an account's Trial Balance/Profit & Loss/Balance Sheet/Assets total, a cost center's
     activity, a tax rate's collected amount, the Tax Payable net-movement figure, or an
     Expenses/Incomes grouped-report row) can be clicked to expand a nested table of every
     journal-entry line, sale-order line, or Expense/Income entry that rolled into it, in date
     order. The backend computes this once per report as a `details` list riding along on the
     existing row DTO (a new shared `ReportDetailLineDto` — see its own javadoc for the
     debit/credit convention each report family uses) rather than a second endpoint the frontend
     would have to call per row. Financial Transactions, the Cash Flow Report, and (on-screen)
     the Journals Report needed no new backend data for this at all — their rows already are
     (or already carry, for Journals Report's own `lines`) the individual transactions; they just
     needed the frontend to actually show that instead of only a totals row per entry.
  2. **Export Excel/PDF.** Every report screen gained the exact same "Export Excel"/"Export PDF"
     buttons the original POS Reports package already has, reusing that same generic
     `ReportExportService` (title + column headers + string rows in, an `.xlsx` or `.pdf` out) —
     no second exporter was written. `AccountingReportController` now accepts a `format` query
     param (`json` by default, or `xlsx`/`pdf`) on every report endpoint; a row's own `details`
     are flattened into indented sub-rows right beneath it in the exported file, via a shared
     `withDetails` helper, so the download shows the identical drill-down the on-screen table
     does.

  Ledger and Chart of Accounts Directory were left alone — both are still plain links to the
  existing Chart of Accounts CRUD screen, not a report with rows to expand or export.

  Verified with `verify_java.py` (356 files, brace/package checks pass — one new DTO,
  `ReportDetailLineDto`) and both `ng build` configurations (dev + production) — both pass
  cleanly.

- **2026-10-09 — Inventory completeness pass: warehouses, costing, stock counts, reorder, reservation, in-transit transfers, variants, batch/expiry/serial tracking.**
  Everything below lives under Admin > Inventory unless stated. The single-store limits described in the older Inventory entries and in "Known limitations" are superseded by this section.

  *Multi-warehouse and branches.* Stock is now held per warehouse **location** (`warehouse/`: `Warehouse`, `WarehouseLocation` with types RECEIVING / SALES / STORAGE / DAMAGED, `ProductStock`). `Product.stockQuantity` mirrors the primary warehouse's SALES location, so the POS keeps working unchanged. Purchase orders, stock adjustments, sales-order fulfilment, returns, manufacturing, packaging and restaurant all take a warehouse/location. Stock Transfers move stock between locations (PENDING → CONFIRMED) and, between different branches, between branches (see in-transit below). Stock Movement History has a warehouse filter and now also lists stock transfers (`Stock transfer out/in`, `... (dispatched)`, `... (received)`, `... returned to sender`; reference `Transfer #id`; transfers made before balances were recorded show 0 → 0). A startup guard (`WarehouseService.columnExists`) skips the old `is_default` / `product_stock.warehouse_id` migrations on databases that never had those columns.

  *Cost follows purchases.* Receiving a purchase order re-prices the product by weighted average: (old cost × on hand + received net value) ÷ (on hand + received qty), audited as `PRODUCT_COST_UPDATED`. Inventory Settings gained a **Costing method**: Weighted average (default), Last purchase cost, or Manual (receiving never changes cost). **Landed cost** (freight, duty) can be added on the receive panel; it is spread over the lines by net value, added to cost, and posted Dr Inventory / Cr Cash (if already paid) or Accounts Payable. Record freight there rather than as an expense or it is counted twice; the AP credit has no supplier-ledger entry, so it does not appear in a supplier statement.

  *Stock counts* (`/api/stock-counts`). Start a count for a warehouse (optionally one category) to snapshot every tracked, non-zero product per location; enter counted quantities, submit, then approve (ADMIN or `PERM_APPROVE_STOCK_ADJUSTMENT`). Approval posts one RECOUNT stock adjustment per counted line of (counted − system at start), so stock, audit and accounting behave like any adjustment and sales/receipts made during the count are preserved. Uncounted lines are ignored; approval is all-or-nothing.

  *Reorder levels and requisitions.* `Product.reorderLevel` / `maxStockLevel`. Admin > Reorder lists products whose position (sellable − reserved + open POs + in transit) is at or below the level, with a suggested quantity, and creates a DRAFT purchase requisition.

  *Supplier price lists.* `SupplierPrice` (supplier, product, unit cost, optional validity dates). A new PO form fills line costs from the chosen supplier's current prices.

  *Stock aging and dead stock.* Two reports (age buckets by days since last sale; no sales in 90 days) with category / warehouse / branch filters, built from stock movement history.

  *Sales-order reservation.* Computed, not stored: reserved = Σ(ordered − fulfilled) on confirmed / partially fulfilled orders; available = sellable − reserved. Confirming an order that stock cannot cover returns 409 with the shortage and the screen offers "confirm anyway" (audited `SALES_ORDER_RESERVATION_OVERRIDE`). The POS blocks a sale into reserved stock only when "Allow negative stock" is off. Reservation is global per product, not per location.

  *In-transit transfers.* For transfers between branches "Confirm" dispatches (stock leaves the source, status IN_TRANSIT); the receiving branch records the received quantity (≤ sent). A shortfall is booked as shrinkage in the sending branch; rejecting an in-transit transfer returns it to the sender. Same-branch transfers still move instantly. `EnumConstraintMigration` drops the stale `stock_transfer.status` check constraint that `ddl-auto=update` leaves behind.

  *Product variants.* Variants are ordinary products linked to a template (`variantParentId`, label, attributes). Product detail > Variants generates option combinations (up to 3 options, 200 combinations), copying price, cost, category, tax, reorder and tracking settings; the template is deactivated. Reports are per variant.

  *Batch, expiry and serial tracking* (`tracking/`: `StockBatch`, `StockSerial`, `TrackingService`, `TrackingContext`, `TrackingController`). Per-product mode None / Batch / Serial on the product form. Batches/serials are captured on the PO receive panel and on stock adjustments, and consumed inside `CatalogService.adjustStock` — the single stock chokepoint — so every module is covered. Batch products sell earliest-expiry-first (expired last); serial products use the numbers typed on the POS cart line or sales-order fulfil line, otherwise the oldest in stock; offline-synced sales always auto-pick. Transfers carry batches/serials with the stock (`adjustStockUntracked` + `moveNow` / `dispatch` / `arrive` / `returnToSender`). Reports: Batch Stock, Expiring Stock (expired or within 60 days), Serial Number Register; product detail shows Batches / Serial numbers panels. Stock that was never given a batch/serial (opening balances, count surpluses, customer returns) is "untracked" and never blocked.

  *Smaller changes.* The packaging rule form's quick-add child product now has a Tax rate dropdown.

  Limits: customer returns restock quantity only (a returned serial stays SOLD; re-add it via an adjustment); purchase returns and count shortages auto-pick batch/serial; selling an expired batch is allowed; serial numbers are unique per product, not global; changing a product's tracking mode does not convert existing records.

  Verification: frontend `tsc --noEmit` and `ngc --noEmit` clean. The backend was **not** compiled with Maven (Maven Central is unreachable from the build environment); it was checked with `javac -proc:none` over all sources, which found and fixed one real error. Run `mvn -q -DskipTests compile`, then start the app once so Hibernate creates `stock_count`, `stock_count_line`, `supplier_price`, `stock_batch`, `stock_serial` and the new columns on `product`, `purchase_order`, `store_settings`, `stock_transfer`, `stock_adjustment` and `order_line`.

## Known limitations / where to go next

- **This sandbox could not compile the backend.** Maven Central is blocked here, so
  `mvn spring-boot:run`/`mvn compile` were never actually run against this code — every backend
  file was written and manually re-checked (brace balance, package/import consistency, call graph
  between services) but not compiler-verified. **The frontend, by contrast, actually was built and
  verified here** — `npm install` and both `ng build` (dev and production configurations)
  completed cleanly against the real `@angular/*` packages, including Angular's strict template
  type-checking. Run `mvn spring-boot:run` as your first step and tell me about any compile error;
  it's straightforward to fix from the message. **This already happened once**: the seventeenth
  pass's inclusive-tax rework added a call to `OrderCalculator.taxOnAmount(...)` inside
  `ReturnsService`, but never added the import for `OrderCalculator` itself (`OrderLine`/
  `OrderService`/`SaleOrder`, the same package, were already imported — `OrderCalculator` was
  simply missed) — a real `does not exist` compile error the static brace/package checks here
  can't catch, since they don't resolve types across files. Fixed by adding the missing
  `import com.posapp.order.OrderCalculator;` line. Every other file in the backend was grepped for
  the same class of mistake (a bare `OrderCalculator.` call with no matching import) and none were
  found, so this looks like an isolated miss rather than a systemic gap.
- Returns endpoints aren't role-gated yet — registers/shifts are now `ADMIN`-only for writes (see
  "Auth" above), and so are catalog, customers, staff accounts, and the dashboard, but any
  logged-in cashier can still process a return. Add `@PreAuthorize("hasRole('ADMIN')")` to
  `ReturnsController` if you want that restricted too.
- Stock is held per warehouse location, with purchase orders, stock counts, in-transit transfers and
  optional batch/serial tracking (see the 2026-10-09 inventory entry under "What's included");
  `Product.stockQuantity` is only a mirror of the primary warehouse's SALES location. The older
  "single-store" notes elsewhere in this file are historical.
- Tax is per-product now (see **Taxes** above), but still no tax *jurisdictions* (e.g. different
  rates by region/customer) — one rate per product, store-wide. See the **Known trade-off** note
  under **Taxes** above for what happens to tax figures on sales rung before this pass's
  migration (they now read $0 tax, since those `OrderLine` rows have no snapshotted rate to read).
- No integration tests yet. `OrderService`/`RegisterService`/`ReturnsService` are where the
  money math lives — that's the highest-value place to add them first.
- The JWT secret in `application.properties` (`app.jwt.secret`) is a placeholder — replace it
  before any real deployment, and consider moving it to an environment variable.
- Every service method that maps a JPA entity with a `LAZY` association to a DTO needs
  `@Transactional` wrapping the *whole* method (not just the repository call) — a Spring Data
  repository call is transactional on its own, but that transaction closes as soon as the call
  returns, before any DTO mapping that follows it runs. `CatalogService` was missing this
  entirely and threw `LazyInitializationException: could not initialize proxy ... - no Session`
  on `Product.category` the first time it ran against real Postgres; it's now fixed there and in
  `UserService`, but keep the pattern in mind for any new service.
- The Receipts list (both the standalone `/receipts` page and the in-terminal panel) is capped at
  the 200 most recent completed sales — no pagination or date-range filter yet.
- Payment methods have no delete, only deactivate — intentional (a deleted method would orphan
  the string stored on historical `Payment`/`SalesReturn` rows), but there's currently no admin UI
  affordance explaining *why* delete is missing beyond the form's help text.
- Shifts (`/admin/shifts`) is a read view over existing session data — it doesn't add scheduling,
  shift assignment, or labor-cost reporting; "shift" here means "till session," not a rostering
  concept.
- Closing a cashier session (alone or via "Close all sessions") discards any cart that cashier
  never checked out — same as the terminal's **Discard** button — rather than transferring it to
  another cashier or leaving it "parked" for later. If that's too aggressive for your workflow
  (e.g. a cashier steps away mid-sale and someone else should be able to pick the cart back up),
  the discard call in `CashierSessionService.close`/`closeAllForShift` is the place to change —
  e.g. reassign the order's `cashierSession` to the shift's still-open opener session instead of
  voiding it.
- Both "opening the terminal" (this pass's PIN gate) and "Switch cashier" resolve who's active by
  PIN alone with a linear scan over active, PIN-enabled staff
  (`CashierSessionService.findByPinOrThrow`) — fine for a normal-sized staff roster, but not the
  approach to keep if this ever needed to scale to hundreds of active PINs.
- **A staff member with no PIN set can no longer use the terminal at all** — this pass's PIN gate
  (Task: "should ask PIN and open related session based on the PIN entered") removed the old
  silent "my own session" fallback that used to open the terminal for whoever was logged in with
  no PIN needed. Set every cashier's PIN from Admin > Staff before they need to ring a sale.
  A real browser **reload** of the terminal still re-shows the PIN screen every time, rather than
  resuming a session automatically — arguably a fix, not just a trade-off, since it also removes
  the earlier limitation where a page reload silently fell back to the JWT identity regardless of
  who had actually switched in. **Navigating back to the terminal in-app** (from Checkout or the
  receipt screen) no longer re-prompts, though (`TerminalSessionService` — see **Terminal**
  above); the in-memory cache that skips the PIN screen is scoped to the current browser tab's
  session, so opening the same register session in a *second* tab (or after a reload) still
  prompts for a PIN there, same as before.
- The new Reports export (`ReportExportService`, Apache POI + OpenPDF) is, like the rest of this
  backend, not compiler-verified in this sandbox (see the note above) — if either dependency's
  Maven coordinates/version need adjusting once you actually build, that file plus `pom.xml` is
  the whole surface area to check.
- The terminal's customer search (`filterCustomers`) filters an already-loaded, in-memory list
  client-side on every keystroke — fine at normal customer-list sizes, but it fetches the *whole*
  customer list once on terminal load rather than searching server-side, so a store with a very
  large customer base should switch this to a debounced `GET /api/customers?q=...`-style backend
  search instead.
- The terminal's inline **👤+ Add** customer panel has no duplicate check — creating "Jane Doe"
  twice from the terminal (once by two different cashiers, say) makes two separate customer rows;
  the same is already true of the Admin > Customers form, this pass just added a second place that
  can produce it.
- Checkout's per-method amount cap (Task: "if the total amount to pay is already input do not
  allow input extra amount") only prevents *overpaying* — it does not require the entered amounts
  to add up to the full total before "Complete sale" is enabled; checkout has always allowed
  submitting less than the total due (an underpayment), and this pass didn't change that.
- **My Profile** (Task: "Add my profile, change my password") only lets you edit your own display
  name — not your username, role, or active flag; those stay admin-only, on the Staff screen.
  There's no "forgot password" flow (there's no email/SMS integration in this app at all) — if a
  user is locked out, an admin has to reset it for them from Admin > Users, same as before this
  pass existed.
- Customer `taxId` (Task: "Add Tax ID on customer entity") is purely informational — nothing in
  checkout, tax calculation, or the reports validates its format or uses it for anything; it's
  just captured for display/printing on things like the new Customer Statement.
- The Customer Statement (Task: "Add Customers Report (Customer Statement)") has no pagination
  and, like the seven POS Reports, aggregates entirely from `findCompletedForCustomerStatement`'s
  result in Java — fine for one customer's normal purchase history, potentially slow for a
  long-standing customer with thousands of sales over a very wide date range.
- The sidebar's accordion behavior (Task: "Once one menu is open other should be closed") is
  purely a display-state change — every group's set of links and every route/access guard behind
  them is unchanged; only how many groups can be visually expanded at once changed (from "any
  number" to "at most one").
- **Store credit / AR** (this pass) has a few deliberate scope boundaries, not oversights:
  - There's no "unapply"/void for a recorded `CustomerPayment` — once allocations are saved,
    correcting a mistaken payment means recording a manual adjustment some other way (there's no
    UI for it yet); the ledger is append-only.
  - `defaultCreditTermDays` and `dueDate` are calendar days from checkout time, with no per-store
    business-day or holiday calendar — "overdue" in the two new credit reports is a plain
    `LocalDate` comparison against server "today" (UTC).
  - The Outstanding/Overdue Credit reports and `CustomerCreditService.outstandingBalance` iterate
    every one of a customer's credit orders in Java (same "aggregate in Java, not SQL" convention
    as the original seven reports) — fine at normal order-history sizes, potentially slow for a
    single customer with thousands of historical credit sales.
  - The terminal's inline "add a customer" panel still can't set `creditEnabled`/`creditLimit` —
    a customer added mid-sale from the terminal has credit off by default; an admin has to visit
    Admin > Customers afterward to enable it. (This is also where a pre-existing latent bug lives,
    unrelated to this pass: that panel calls the same `ADMIN`-only `POST /api/customers` endpoint
    every other customer-creation form uses, so a non-admin cashier using it would get a 403 —
    not something this pass introduced or was asked to fix, but worth knowing about.)
  - Every credit number (`creditAmount`, `creditLimit`, allocations) is plain `BigDecimal` with no
    currency field — same single-currency assumption as the rest of this app's money handling.
- **Auto-print-then-return-to-terminal after checkout** relies on the browser's `afterprint`
  event, which every modern desktop browser (Chrome/Edge/Firefox/Safari) fires once the native
  print dialog is dismissed — but it's not part of a formal spec, so a very old or unusual browser
  could fail to fire it, which would leave the cashier on the receipt screen with no automatic
  redirect (the manual **Back to terminal** link is still right there as a fallback either way).
  There's also no way from the web app to force a specific printer, skip the OS print dialog
  entirely, or print silently — "select a printer" still means the browser's own print dialog;
  a true silent/kiosk print (no dialog at all) needs either a kiosk-mode browser launch flag
  (e.g. Chrome's `--kiosk-printing`) configured on the till's machine, or a dedicated print/agent
  service, neither of which this app controls.
- **Business details / System settings** are stored and readable via `/api/settings`. As of the
  twenty-fourth pass, the **business name and logo** are wired into both the receipt header and
  the topbar (replacing "POS") — but **System settings' currency/time zone/date format still
  aren't wired into anything**: every screen that shows a price or a date still formats it plainly,
  with no currency symbol or per-store date pattern applied anywhere in the app. Wiring that in is
  a cross-cutting change touching every price/date display in the app — out of scope for adding
  the settings screen itself, and a natural next step.
- The receipt header and topbar branding (twenty-fourth pass) only ever read `GET /api/settings`
  and degrade to their pre-pass appearance (plain "Receipt" heading, plain "POS" brand label) on
  any failure or when Business details hasn't been filled in — neither screen has its own error
  message for a failed settings fetch, by design, since a broken settings call should never block
  printing a receipt or using the app.
- The **logo upload** (Business details) is capped at 2MB client-side and stored as a raw
  `data:` URL string in the database (`StoreSettings.logoDataUrl`, a `TEXT` column) — there's no
  server-side resizing/compression, so a large-but-under-2MB image is stored (and sent back on
  every `GET /api/settings`) at its original size. Fine for a small store logo; a very
  image-heavy deployment might want actual file storage (S3-style) plus a thumbnail instead.
- The **System settings** currency picker is a curated list of about ten common codes (with an
  editable symbol field for anything not listed), not the full ISO 4217 table — good enough for
  this app's expected userbase without maintaining an exhaustive currency list.
- **Change PIN** (My Profile, this pass) reuses the same active-user PIN-uniqueness check Admin >
  Staff's "Set/Change PIN" already enforces — a cashier trying to reuse a PIN another active staff
  member currently has gets the same 409 conflict an admin would. There's still no way to *view*
  your current PIN (PINs are hashed, same as passwords) — only to overwrite it with a new one.
- **Customer display** (the **📺 Customer display** button) still requires a login, on whichever
  device shows it — there's no unauthenticated "public kiosk" access mode, because `GET
  /api/orders/{id}` stays ownership-or-admin gated while an order is OPEN (the seventh pass's
  rule, deliberately left untouched). On separate hardware that means signing that device in as
  either the same cashier working the till or an admin; anyone else gets a plain "Display
  unavailable" screen with a 403, not someone else's cart. It has no "change due" for the same
  reason the app has never had one anywhere: checkout has capped payment amounts at the order
  total since the sixteenth pass, so there's no overpayment to compute change from — the display
  shows the completed payment breakdown per method instead, once checkout finishes. It's poll-based
  (every ~2.5s), not push-based — this app has no websocket/SSE infrastructure anywhere, so a
  couple of seconds of staleness after an item is scanned is expected, not a bug. The "on screen"
  pointer (`CashierSession.activeOrderId`) is a display concern only, exactly like
  `PaymentMethodEntity.sortOrder` — nothing in checkout, reporting, or till reconciliation reads
  it. And the thank-you screen's fallback-to-idle timer (8 seconds) is just that — a fallback in
  case the terminal doesn't move on to a new tab quickly; normally the *next* tab switch's
  `activeOrderId` change preempts it well before the timer fires.
- **Product paging caps** (this pass — "check the loading of 100000 products") are fixed
  ceilings, not configurable settings: the admin product list clamps `size` to 200 server-side no
  matter what's requested (`CatalogService.listAllForAdmin`), and the terminal's own search is
  hard-capped at 200 results (`CatalogService.TERMINAL_SEARCH_CAP`) with no UI to raise it —
  intentional (a terminal cashier scrolling through hundreds of tiles isn't the point; searching
  is), but worth knowing if a future store genuinely needs to browse more than 200 unfiltered
  matches at once. Neither cap was tested against a live 100,000-row database in this
  environment — there's no Postgres instance available here to run
  `backend/scripts/seed_100k_products.sql` against — so this is a design response to the
  performance problem an unbounded list would clearly have at that scale, not a benchmarked one;
  run the seed script locally and try both screens to confirm.
- **Return all items** (this pass) has no idea which lines already have a prior partial return —
  it fills every row with what was originally sold, not what's still outstanding, since the form
  never fetches an "already returned" figure per line (only the backend, at submit time, actually
  knows that — see `ReturnsService.processReturn`). On a fully-fresh order (no earlier return),
  this is correct on the first click; on an order that already had one, clicking it and submitting
  as-is gets rejected with the existing precise error naming exactly which line and how much is
  left, and the cashier adjusts that one row down and resubmits. A future pass could show a live
  "already returned"/"remaining" figure per line so this — and manual entry — can't produce an
  invalid amount in the first place.
- **Return receipt / "marked returned" on the sale receipt** (this pass) deliberately don't share
  code with each other despite looking related: the sale receipt's "already returned" lookup
  (`GET /api/returns/order/{orderId}`) lives in the `returns` package and is called *by* the sale
  receipt component, rather than `OrderDto`/`OrderLine` growing a `returnedQuantity` field, because
  `returns` already depends on `order` (`ReturnsService` calls `OrderService`) — the reverse
  dependency would be circular, the same constraint already documented for
  `CashierSession.activeOrderId` (register vs. order packages). One consequence: a return
  processed before this pass has `SalesReturnLine.refundAmount == null` (the receipt prints "—"
  for it), since that snapshot field didn't exist yet when it was written.
- **Offline mode's scope boundaries** (this pass) are deliberate, not oversights:
  - A **fresh PIN-gated terminal open still requires connectivity** — PIN validation is a server
    call this never tries to replicate offline (caching PIN hashes client-side would be a real
    security regression, not a convenience worth adding). Offline mode covers a terminal that's
    already open and mid-shift when the connection drops, not opening one from scratch with no
    network at all.
  - **A server-backed tab that was already open when connectivity dropped is frozen, not
    migrated** — there's no attempt to transplant an in-progress `currentOrder`'s lines into the
    local offline cart. It simply waits, untouched, until the terminal is back online; a cashier
    starts a *new* sale in the offline cart in the meantime. This was a deliberate scope line, not
    a technical wall — merging the two would mean duplicating a lot more of `OrderService`'s logic
    client-side for one edge case (mid-cart, not "already offline when the sale started").
  - **The offline product/category/payment-method/customer cache is a fixed ceiling, not the
    whole catalog for any store size** — `CatalogService.listForOfflineCache` caps at 5,000 active
    products, the same "fixed ceiling with no UI to raise it" shape as the terminal's own live
    search cap and the admin product list's page-size cap (see above). A store at the
    twenty-sixth pass's 100,000-product stress-test scale would need a smarter (partial/LRU) cache
    strategy to go fully offline — out of scope here, and flagged the same way that pass's own
    caps were.
  - **No live credit-limit check while offline** — Checkout's `maxAmountFor` caps a credit row at
    the customer's `CreditSummary.availableCredit`, fetched live from the server; offline mode has
    no such fetch, so a credit sale can be queued for more than the customer can actually still
    borrow. It's caught for real at sync time (the exact same `completeCheckout` code path
    `customerCreditService.assertWithinLimit` runs for online sales), and lands in the queue
    marked "failed" for a cashier/admin to review rather than being silently accepted.
  - **The IndexedDB queue lives on one device, not shared across terminals** — two terminals that
    both go offline queue their own sales independently and each syncs on its own; there's no
    cross-device view of "everything currently queued anywhere" short of opening the queue panel
    on each device once it's back online.
  - **A discarded 'failed' queued sale is genuinely gone** — `discardQueuedSale` removes it from
    IndexedDB outright with no undo and no server-side record of it ever having existed (it was
    never created there in the first place, precisely because it failed to sync). This is the
    intended escape hatch for a queued sale nobody can fix (e.g. a customer who's no longer
    enabled for credit at all), not something to click without being sure.
- **EFRIS now actually submits at checkout, but the integration is still narrower than a real
  deployment needs.** `OrderService.submitToEfrisIfApplicable` calls a real HTTP endpoint (see the
  "Real EFRIS submission at checkout" changelog entry above) once `EFRIS_UG` is enabled and a
  terminal has connection settings saved, but: there's no retry queue for a failed submission (the
  `efrisError` badge is the only signal — a cashier/admin has to notice it and handle the sale
  manually, e.g. by resubmitting through some other means); the `sdc_data`/`cis_data` response
  parsing is inferred from one worked example, not documented EFRIS behavior, so a real device's
  response could come back split differently than expected (see `EfrisSubmissionResult`'s javadoc
  for the one place to fix that); nothing warns an admin at Tax Rate/Product edit time that a
  `taxLabel`/`unspCode` is missing before it's needed at checkout; and the submission happens
  synchronously inline with the checkout HTTP response (a slow or hanging EFRIS endpoint delays
  that response, bounded only by `EfrisSubmissionService`'s own 20-second request timeout) rather
  than being queued for a background worker, since this codebase has no async/post-commit-hook
  infrastructure yet. Until those are addressed, `InstalledAppSetting`'s free-form key/value rows
  (the generic settings table, still present alongside the per-terminal connection table) aren't
  validated or read by anything.
- **[Superseded by the 2026-10-09 inventory entry: multi-warehouse, transfers and per-location stock now exist.]** **Inventory depth (this pass) covers a single store; multi-location transfers are deliberately
  out of scope.** Fourier's own choice when asked to scope this: the data model has no
  location/warehouse concept at all — one shared `Product.stockQuantity` pool, same as before —
  so "move stock between locations" isn't representable yet. Adding it later means giving
  `Register` (or a new `Location` entity) its own stock pool per product and reworking every place
  that reads/writes `stockQuantity` today (`CatalogService.adjustStock`, the new
  `PurchaseOrderService.receiveLines`/`StockAdjustmentService.create`, `OrderService`/
  `ReturnsService`'s own stock adjustments) to take a location — a real rework, not an additive
  change, so it's better done as its own pass once there's an actual second location to design
  against. Relatedly: a purchase order belongs to exactly one supplier, so the dashboard's bulk
  "Create purchase order for all" low-stock action always drafts a single PO even when the
  low-stock list spans products usually bought from different suppliers — the admin picks one
  supplier for the whole draft (or removes/relocates lines to a second manually-created PO) rather
  than this auto-splitting by preferred supplier, since `Product` has no supplier-preference field
  (see the "Inventory depth" changelog entry above for why one wasn't added: it would have created
  a circular dependency between the `catalog` and `inventory` packages).
- **Manufacturing is deliberately single-level and doesn't close the loop with Purchasing.** A Bom
  component is always a plain `Product`, never another product's own Bom (see `Bom`'s javadoc) —
  no multi-level sub-assemblies/explosion yet, so a "finished good" that's itself built from other
  manufactured sub-assemblies has to be modeled as one flat parts list today. Production Plans
  surface component shortfalls but never auto-generate a Purchase Order for a short raw material —
  an admin still has to notice a shortfall and place that PO by hand (`ProductionPlan`'s javadoc
  documents both as scope boundaries, not oversights). Production Plan demand is also
  single-warehouse, same ceiling as the rest of this app's stock model (see the multi-location
  note just above). And Workstation/Routing time estimates (`estimatedMinutesPerUnit` ×
  `Workstation.hourlyRate`) are informational only — nothing currently rolls them into a
  Manufacturing Order's own cost breakdown, which today is material + indirect costs alone; adding
  labor cost from routing time is the natural next increment if Fourier wants it.
- **The Accounting module models this app's economics as they exist today, not a full general
  ledger for every scenario a real bookkeeper might want.** Every non-store-credit tender (cash,
  card, mobile money, whatever an admin has set up as a `PaymentMethodEntity`) posts to the one
  Cash account — there are no per-method bank/clearing sub-accounts, so a card payment is treated
  identically to cash for GL purposes rather than modeling settlement delay or processor fees.
  Inventory is likewise one pooled asset account, matching `Product`'s own single
  `stockQuantity`/`costPrice` pair — there's no separate raw-material/WIP/finished-goods split (see
  the "Manufacturing's own posting is deliberately overhead-only" changelog entry above). There's
  no period-close/retained-earnings-rollover step — the Balance Sheet's `currentEarnings` is
  always computed live, all-time, rather than ever being closed into Owner's Equity by a year-end
  entry, so it keeps growing rather than resetting each period the way a real close would. There's
  no accrual-basis expense recognition beyond what's described above (e.g. no accounts-payable
  aging or 1099-style vendor reporting), and no multi-currency support — everything posts in the
  store's single configured currency. And turning Accounting on only affects transactions from
  that point forward — nothing retroactively back-posts journal entries for sales, purchases, or
  payments that happened before it was enabled.

- **The Accounting Reports expansion's own scope boundaries (this pass) are deliberate, per
  Fourier's own `AskUserQuestion` choices, not oversights:** the Assets report is a simple
  as-of-date listing of Asset-type accounts, not a fixed-asset register — there's no per-asset
  acquisition date/cost/depreciation schedule anywhere in this app. The Tax Report/Declaration is
  a general summary (tax collected by rate, from POS sale lines' own snapshotted tax rate; tax
  payable's own net ledger movement) rather than any specific tax authority's official filing
  form, and there's no "tax paid" figure at all — this app has never modeled input/purchase tax
  (no `PurchaseOrderLine` field carries a tax amount). Expense/Income's `paid`/`received` flag is
  a simple boolean, not a real accounts-payable/receivable aging workflow — there's no "mark this
  unpaid expense as paid later" screen; recording that today means a manual journal entry against
  `ACCOUNTS_PAYABLE`/`ACCOUNTS_RECEIVABLE` once it's actually settled, same as any other accrual
  in this app. Cost Centers are a flat, unlimited-depth-free list (no parent/child rollups), and
  tagging one is entirely optional everywhere it's offered — nothing requires it. And the sixteen
  Expenses/Incomes segmented+periodic reports only ever read `Expense`/`Income` rows created
  through the new dedicated entry screens — a POS sale's revenue, a Purchase Order's cost, or any
  other module's own automatic posting doesn't count toward them (that's what Profit & Loss/
  Financial Transactions are for).
