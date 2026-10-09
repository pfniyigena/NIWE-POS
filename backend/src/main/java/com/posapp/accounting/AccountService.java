package com.posapp.accounting;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.accounting.dto.AccountDto;
import com.posapp.accounting.dto.AccountRequest;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRUD for {@link Account} (the Chart of Accounts), plus {@link #seedDefaultChartIfNeeded}/{@link
 * #backfillMissingAccounts}, which create the starter chart. Accounting has no enable/disable
 * switch (Fourier: "make all modules available without enabling them"), so once a chart exists
 * it's always there — but {@code DataSeeder.run} deliberately does NOT force one into existence on
 * every startup the way it once did (see that method's own comment on why): a brand-new store is
 * left genuinely unseeded ({@code repository.count() == 0}) until either an ADMIN completes the
 * on-screen setup wizard, or a first posting lazily creates it via {@code AccountingService.post}
 * — see this class's next paragraph for why that matters.
 *
 * <p>Fourier: "I want to have possibility of selection on Chart while initiating the app at first
 * time" (deploying this for Rwanda and Chad clients), later refined — "The Config
 * app.accounting.chart-country can be forgotten easily, what if we add this on the screen then at
 * first time a user select the country?" — which country's chart gets seeded on a brand-new store
 * is a {@link ChartCountry}, decided either by {@link ChartSetupController}'s on-screen wizard (an
 * ADMIN is redirected there on first login whenever {@link #isChartSetupNeeded}), or by whatever
 * a posting forces a lazy seed before anyone visits it (see {@code AccountingService.post}), in
 * which case {@code app.accounting.chart-country} (default {@code RWANDA}; see that property's own
 * comment in {@code application.properties}/{@code application-niwe.properties}) is
 * the fallback. Either way the actual choice is locked into {@code StoreSettings.chartCountry} the
 * moment it's made (see {@link #resolveChartCountry}) — the property only ever matters in the
 * brief window before that, so editing it afterward has no effect. Rwanda has
 * no single mandated chart for private businesses (an IFRS country, not OHADA), so {@link
 * #RWANDA_CHART} is this app's own numbering extended with Rwanda's real statutory accounts
 * (VAT/RRA, PAYE, RSSB). Chad IS an OHADA member state, so {@link #CHAD_CHART} uses the real
 * SYSCOHADA numbering (512/521 Banques, 701 Ventes, 4431 TVA facturée, 471 Comptes d'attente, and
 * so on — researched from OHADA's own Uniform Act chart of accounts and cross-checked against a
 * second SYSCOHADA reference). A couple of Chad-specific codes — the combined payroll/apprenticeship
 * tax account and the general transport/fuel expense account — don't have one universally-quoted
 * standard sub-code, so their own {@code description} says so; have a local accountant confirm
 * those two before relying on this chart for an actual statutory filing.
 *
 * <p>What makes this work without touching a single automatic posting: {@link AccountingService}
 * and {@link AccountingReportService} never resolve an account by its display {@code code} (which
 * is what varies by country — Rwanda's Cash is "1000", Chad's is "521") — they resolve by {@link
 * Account#getWellKnownKey()}, a stable internal key (e.g. {@code "CASH"}) that's identical across
 * every country's chart. The constants below (unchanged in spelling from before this country split)
 * are exactly those keys. {@link #getByWellKnownKeyOrThrow} is the one place that does the lookup.
 */
@Service
public class AccountService {

    /** Cash and any other tender treated the same way for GL purposes (see AccountingService's javadoc on why card/other non-credit tenders aren't split into their own accounts). */
    public static final String CASH = "CASH";
    /** What customers owe the store — POS store-credit sales, unfulfilled Sale Order revenue, both settled here. */
    public static final String ACCOUNTS_RECEIVABLE = "ACCOUNTS_RECEIVABLE";
    /** One pooled inventory value account — see AccountingService's javadoc on why this app's single stockQuantity/costPrice pool doesn't get separate raw-material/WIP/finished-goods sub-accounts. */
    public static final String INVENTORY = "INVENTORY";
    /** What the store owes its suppliers. */
    public static final String ACCOUNTS_PAYABLE = "ACCOUNTS_PAYABLE";
    /** VAT/TVA collected on sales but not yet remitted to the local tax authority (RRA in Rwanda, DGI in Chad) — was labeled generically "Tax Payable" before the country split. */
    public static final String TAX_PAYABLE = "TAX_PAYABLE";
    /**
     * Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — VAT/TVA
     * paid on purchases, reclaimable against {@link #TAX_PAYABLE} when filing with the local tax
     * authority. Added once Purchase Orders/Bills began capturing their own line-level tax (see
     * {@code com.posapp.inventory.PurchaseOrderLine.taxRatePercent}'s javadoc) so a receipt's tax
     * portion has somewhere to land besides being buried inside {@link #INVENTORY}'s own cost.
     * Added via {@link #backfillMissingAccounts} to every already-seeded store, same
     * zero-migration-risk "just a new row" convention {@link #CUSTOMER_CREDIT_PAYABLE} used.
     */
    public static final String INPUT_VAT_RECOVERABLE = "INPUT_VAT_RECOVERABLE";
    /** Overhead absorbed into a Manufacturing Order's finished goods but not yet reconciled against an actual paid expense — see AccountingService's Manufacturing posting javadoc. */
    public static final String ACCRUED_MANUFACTURING_OVERHEAD = "ACCRUED_MANUFACTURING_OVERHEAD";
    /** A starter equity account for manual entries (owner contributions/draws; Chad's own chart uses this as its real "Capital social" account) — nothing posts here automatically. */
    public static final String OWNERS_EQUITY = "OWNERS_EQUITY";
    /** Net sales revenue (post-discount, pre-tax) — POS sales/returns and Sale Order fulfillment/returns all post here. */
    public static final String SALES_REVENUE = "SALES_REVENUE";
    /** Cost of goods actually sold — the mirror-image debit to every Sales Revenue credit, for stock-tracked lines only. */
    public static final String COST_OF_GOODS_SOLD = "COST_OF_GOODS_SOLD";
    /** Stock adjustment write-offs/losses (and their reverse, a recount finding extra stock). */
    public static final String INVENTORY_SHRINKAGE_EXPENSE = "INVENTORY_SHRINKAGE_EXPENSE";
    /** The one safety-valve account AccountingService plugs into when a would-be entry doesn't balance to the penny — see its javadoc. Should read $0.00 in the overwhelming majority of real stores. */
    public static final String SUSPENSE = "SUSPENSE";
    /**
     * Fourier: "CANCEL/CREDIT PAID INVOICE → Handle the payment → ... → Customer Credit" — what
     * the store owes a customer back after a paid standalone invoice is cancelled and that amount
     * is converted to store credit instead of refunded in cash (see {@code
     * AccountingService.postInvoiceCancelPaid} and {@code CustomerCreditService.addCustomerCredit}).
     * Added via {@link #backfillMissingAccounts} to every already-seeded store, including one
     * running live, the same zero-migration-risk way every other well-known account added after a
     * store's initial seed has been (no column/type change — just a new row).
     */
    public static final String CUSTOMER_CREDIT_PAYABLE = "CUSTOMER_CREDIT_PAYABLE";

    /** One row of a country's starter chart. {@code wellKnownKey} is null for every account nothing posts to automatically — see Account.wellKnownKey's own javadoc. The 5-arg constructor (no key) is what every such row below actually uses; the 6-arg canonical form is only for a row AccountingService/AccountingReportService resolve by key. */
    private record ChartRow(String wellKnownKey, String code, String name, AccountType type, boolean systemAccount, String description) {
        private ChartRow(String code, String name, AccountType type, boolean systemAccount, String description) {
            this(null, code, name, type, systemAccount, description);
        }
    }

    private static final String LEGACY_RWANDA_VAT_DESCRIPTION =
            "18% VAT collected on sales, not yet remitted to RRA. This app doesn't model input VAT on purchases, so there's no credit netted against it.";

    /** Current (post input-VAT) Rwanda VAT Payable description — see {@link #INPUT_VAT_RECOVERABLE}'s own javadoc for why the old wording above is no longer accurate. */
    private static final String RWANDA_VAT_DESCRIPTION =
            "18% VAT collected on sales, not yet remitted to RRA. Input VAT paid on purchases is tracked separately in Input VAT Recoverable (RRA), not netted against this account automatically — reconcile the two when filing.";

    /** Rwanda's starter chart (Fourier: "I want to change this to Rwanda chart") — see class javadoc for the reasoning; every well-known code from before the country split keeps the exact same {@code code} value, so an already-running Rwanda deployment (e.g. NIWE) sees no change to its own account numbers. */
    private static final List<ChartRow> RWANDA_CHART = List.of(
            new ChartRow(CASH, "1000", "Cash and Bank", AccountType.ASSET, true, null),
            new ChartRow(ACCOUNTS_RECEIVABLE, "1100", "Accounts Receivable", AccountType.ASSET, true, null),
            new ChartRow(INPUT_VAT_RECOVERABLE, "1150", "Input VAT Recoverable (RRA)", AccountType.ASSET, true,
                    "VAT paid on purchases, reclaimable against VAT collected on sales (see VAT Payable) — not netted automatically, reconcile the two when filing with RRA."),
            new ChartRow(INVENTORY, "1200", "Inventory", AccountType.ASSET, true, null),
            new ChartRow(ACCOUNTS_PAYABLE, "2000", "Accounts Payable", AccountType.LIABILITY, true, null),
            new ChartRow(TAX_PAYABLE, "2100", "VAT Payable (RRA)", AccountType.LIABILITY, true, RWANDA_VAT_DESCRIPTION),
            new ChartRow(ACCRUED_MANUFACTURING_OVERHEAD, "2200", "Accrued Manufacturing Overhead", AccountType.LIABILITY, true, null),
            new ChartRow("2300", "PAYE Payable (RRA)", AccountType.LIABILITY, false,
                    "Employee income tax withheld from payroll under Rwanda's PAYE bands (0/10/20/30%) — remitted to RRA by the 15th of the following month."),
            new ChartRow("2400", "Withholding Tax Payable (RRA)", AccountType.LIABILITY, false,
                    "Tax withheld on the business's own payments (e.g. 15% on fees to an unregistered supplier, 5% on commercial imports) — owed to RRA until remitted."),
            new ChartRow("2500", "RSSB Contributions Payable", AccountType.LIABILITY, false,
                    "Employer + employee pension, maternity and occupational-hazard contributions owed to RSSB until remitted."),
            new ChartRow("2600", "Staff Medical Insurance Payable (CBHIS)", AccountType.LIABILITY, false,
                    "Staff medical insurance (CBHIS) withheld from payroll, owed until remitted."),
            new ChartRow("2700", "Dividends Payable", AccountType.LIABILITY, false,
                    "Declared dividends not yet paid out, net of the 15% withholding tax due on distribution."),
            new ChartRow(CUSTOMER_CREDIT_PAYABLE, "2800", "Customer Credit Payable", AccountType.LIABILITY, true,
                    "Store credit owed back to customers — e.g. from cancelling a paid invoice and crediting the customer instead of refunding cash."),
            new ChartRow(OWNERS_EQUITY, "3000", "Owner's Equity", AccountType.EQUITY, false, null),
            new ChartRow("3100", "Share Capital", AccountType.EQUITY, false,
                    "Capital contributed by shareholders in exchange for equity — for an incorporated business."),
            new ChartRow("3200", "Retained Earnings", AccountType.EQUITY, false,
                    "Accumulated profit/loss carried forward from prior periods — posted by the year-end closing entry."),
            new ChartRow(SALES_REVENUE, "4000", "Sales Revenue", AccountType.REVENUE, true, null),
            new ChartRow("4100", "Other Income", AccountType.REVENUE, false,
                    "Income outside core sales — interest earned, asset disposals, and similar."),
            new ChartRow(COST_OF_GOODS_SOLD, "5000", "Cost of Goods Sold", AccountType.EXPENSE, true, null),
            new ChartRow(INVENTORY_SHRINKAGE_EXPENSE, "5100", "Inventory Shrinkage / Write-off Expense", AccountType.EXPENSE, true, null),
            new ChartRow("5200", "Operating Expenses", AccountType.EXPENSE, false, null),
            new ChartRow("5300", "Rent Expense", AccountType.EXPENSE, false, "Premises rent."),
            new ChartRow("5400", "Salaries & Wages Expense", AccountType.EXPENSE, false, "Gross payroll cost, before PAYE/RSSB withholding."),
            new ChartRow("5500", "Utilities Expense", AccountType.EXPENSE, false, "Water, electricity and similar utilities."),
            new ChartRow("5600", "Communication Expense", AccountType.EXPENSE, false, "Airtime, internet and similar communication costs."),
            new ChartRow("5700", "Transport & Fuel Expense", AccountType.EXPENSE, false, "Vehicle fuel and transport costs."),
            new ChartRow("5800", "Professional & Legal Fees", AccountType.EXPENSE, false, "Accounting, legal and consulting fees."),
            new ChartRow("5850", "Licenses & Government Fees", AccountType.EXPENSE, false, "District trading licence and other government fees."),
            new ChartRow(SUSPENSE, "5900", "Suspense / Rounding Differences", AccountType.EXPENSE, true, null)
    );

    /**
     * Chad's starter chart (Fourier: "I will deploy this for Rwanda and Chad clients") — real
     * SYSCOHADA numbering, since Chad is an OHADA member state and SYSCOHADA is its actual mandated
     * standard. Owner's Equity doubles as Chad's own "101 Capital social" (no separate Share Capital
     * row the way Rwanda has one — under SYSCOHADA they're the same account), which is why this
     * chart has no exact Rwanda counterpart for every row.
     */
    private static final List<ChartRow> CHAD_CHART = List.of(
            new ChartRow(CASH, "521", "Banques et Caisse (Cash & Bank)", AccountType.ASSET, true,
                    "Pooled cash, bank and mobile-money tender for GL purposes — this app doesn't split by payment method, or by SYSCOHADA's own Banques (521) / Caisse (571) distinction."),
            new ChartRow(ACCOUNTS_RECEIVABLE, "411", "Clients (Accounts Receivable)", AccountType.ASSET, true, null),
            new ChartRow(INPUT_VAT_RECOVERABLE, "4452", "État, TVA récupérable (Input VAT Recoverable)", AccountType.ASSET, true,
                    "TVA paid on purchases, reclaimable against TVA collected on sales (État, TVA facturée) — SYSCOHADA's own 445 'État' sub-account for recoverable VAT; not netted automatically, reconcile the two when filing with the DGI."),
            new ChartRow(INVENTORY, "311", "Marchandises (Inventory)", AccountType.ASSET, true, null),
            new ChartRow(ACCOUNTS_PAYABLE, "401", "Fournisseurs (Accounts Payable)", AccountType.LIABILITY, true, null),
            new ChartRow(TAX_PAYABLE, "4431", "État, TVA facturée (VAT Payable)", AccountType.LIABILITY, true,
                    "18% TVA collected on sales, not yet remitted to Chad's Direction Générale des Impôts (DGI). Input TVA paid on purchases is tracked separately in État, TVA récupérable, not netted against this account automatically — reconcile the two when filing."),
            new ChartRow(ACCRUED_MANUFACTURING_OVERHEAD, "408", "Fournisseurs, factures non parvenues (Accrued Manufacturing Overhead)", AccountType.LIABILITY, true, null),
            new ChartRow("4472", "Impôts sur salaires — IRPP (Payroll Income Tax Payable)", AccountType.LIABILITY, false,
                    "Personal income tax (IRPP) withheld from employee pay — owed to the DGI until remitted."),
            // Fourier: hit "value too long for type character varying(255)" on this exact row —
            // Account.description is a plain varchar(255) (no TEXT override, unlike e.g.
            // StoreSettings.logoDataUrl), and the original wording here ran to 277 characters.
            // Shortened to fit comfortably under that limit while keeping the same caveat.
            new ChartRow("4473", "Taxe sur les salaires et apprentissage (Payroll & Apprenticeship Tax Payable)", AccountType.LIABILITY, false,
                    "Chad's 7.5% employer payroll tax plus the 1.2% FONAP apprenticeship levy, grouped in one account — SYSCOHADA has no single standard code for this pair; confirm with a local accountant before a statutory filing."),
            new ChartRow("431", "Sécurité sociale — CNPS (CNPS Contributions Payable)", AccountType.LIABILITY, false,
                    "Employer (16.5%) + employee (3.5%) CNPS contributions owed until remitted."),
            new ChartRow("465", "Associés, dividendes à payer (Dividends Payable)", AccountType.LIABILITY, false,
                    "Declared dividends not yet paid out."),
            new ChartRow(CUSTOMER_CREDIT_PAYABLE, "419", "Clients créditeurs (Customer Credit Payable)", AccountType.LIABILITY, true,
                    "SYSCOHADA's own account for a customer with a credit balance — store credit owed back, e.g. from cancelling a paid invoice and crediting the customer instead of refunding cash."),
            new ChartRow(OWNERS_EQUITY, "101", "Capital social (Owner's Equity)", AccountType.EQUITY, false, null),
            new ChartRow("121", "Report à nouveau (Retained Earnings)", AccountType.EQUITY, false,
                    "Accumulated profit/loss carried forward from prior periods — posted by the year-end closing entry."),
            new ChartRow(SALES_REVENUE, "701", "Ventes de marchandises (Sales Revenue)", AccountType.REVENUE, true, null),
            new ChartRow("758", "Produits divers (Other Income)", AccountType.REVENUE, false,
                    "Income outside core sales — interest earned, asset disposals, and similar."),
            new ChartRow(COST_OF_GOODS_SOLD, "601", "Achats de marchandises (Cost of Goods Sold)", AccountType.EXPENSE, true,
                    "This app posts cost of goods sold directly per sale rather than through SYSCOHADA's own period-end stock-variation mechanic (603) — 601 is used here as the straightforward 'cost of merchandise sold' account."),
            new ChartRow(INVENTORY_SHRINKAGE_EXPENSE, "658", "Charges diverses (Inventory Shrinkage / Write-off Expense)", AccountType.EXPENSE, true, null),
            new ChartRow("628", "Autres charges externes (Operating Expenses)", AccountType.EXPENSE, false, null),
            new ChartRow("622", "Locations et charges locatives (Rent Expense)", AccountType.EXPENSE, false, "Premises rent."),
            new ChartRow("661", "Rémunérations directes versées au personnel (Salaries & Wages Expense)", AccountType.EXPENSE, false,
                    "Gross payroll cost, before IRPP/CNPS withholding."),
            new ChartRow("605", "Eau et électricité (Utilities Expense)", AccountType.EXPENSE, false, "Water, electricity and similar utilities."),
            new ChartRow("6281", "Frais de télécommunications (Communication Expense)", AccountType.EXPENSE, false, "Telephone, internet and similar communication costs."),
            new ChartRow("6181", "Voyages et déplacements (Transport & Fuel Expense)", AccountType.EXPENSE, false,
                    "Vehicle fuel, travel and transport costs — SYSCOHADA's closest standard match; adjust if your accountant prefers a dedicated fuel sub-account."),
            new ChartRow("6324", "Honoraires (Professional & Legal Fees)", AccountType.EXPENSE, false, "Accounting, legal and consulting fees."),
            new ChartRow("6412", "Patentes, licences et taxes annexes (Licenses & Government Fees)", AccountType.EXPENSE, false,
                    "Chad's annual business licence (patente) and other government fees."),
            new ChartRow(SUSPENSE, "471", "Comptes d'attente (Suspense / Rounding Differences)", AccountType.EXPENSE, true, null)
    );

    /** The old generic label {@code TAX_PAYABLE} shipped with, before either the Rwanda or the Chad chart existed — only ever used to detect and rename an un-customized row during {@link #backfillMissingAccounts}; an admin who already renamed it themselves is left alone. */
    private static final String LEGACY_TAX_PAYABLE_NAME = "Tax Payable";

    private final AccountRepository repository;
    private final AuditLogService auditLogService;
    private final StoreSettingsRepository storeSettingsRepository;
    /**
     * Fourier: "The Config app.accounting.chart-country can be forgotten easily, what if we add
     * this on the screen then at first time a user select the country?" — this property is no
     * longer the durable answer to which chart a store runs. It only matters before any account
     * exists: as the on-screen setup wizard's pre-filled suggestion ({@link
     * #getDefaultChartCountrySuggestion}), and as the safety-net country if something forces a
     * lazy seed (see {@code AccountingService.post}) before an admin ever visits the wizard. Once
     * the chart is actually seeded, the real answer is locked into {@code
     * StoreSettings.chartCountry} (see {@link #resolveChartCountry}) — editing this property
     * afterward changes nothing, which is the whole point.
     */
    private final ChartCountry configuredDefaultCountry;

    public AccountService(AccountRepository repository, AuditLogService auditLogService,
                           StoreSettingsRepository storeSettingsRepository,
                           @Value("${app.accounting.chart-country:RWANDA}") String chartCountryRaw) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.storeSettingsRepository = storeSettingsRepository;
        this.configuredDefaultCountry = ChartCountry.fromConfigValue(chartCountryRaw);
    }

    /** See {@link #configuredDefaultCountry}'s own javadoc — the locked-in {@code StoreSettings.chartCountry} wins once it's set; the property is only the fallback before that. */
    private ChartCountry resolveChartCountry() {
        ChartCountry locked = getOrCreateStoreSettings().getChartCountry();
        return locked != null ? locked : configuredDefaultCountry;
    }

    /** Same singleton-row convention as {@code StoreSettingsService.getOrCreate} (duplicated here rather than injecting that service, to avoid a cross-package service dependency for one field) — normally DataSeeder has already created the row by the time this runs; the create-on-demand path is just a safety net. */
    private StoreSettings getOrCreateStoreSettings() {
        return storeSettingsRepository.findAll().stream().findFirst()
                .orElseGet(() -> storeSettingsRepository.save(new StoreSettings()));
    }

    private List<ChartRow> chart(ChartCountry country) {
        return switch (country) {
            case RWANDA -> RWANDA_CHART;
            case CHAD -> CHAD_CHART;
        };
    }

    /** Idempotent — a no-op once any account exists, so this is always safe to call again. Deliberately NOT called from DataSeeder anymore (see that class's own comment on why) — this no-arg overload is only AccountingService.post()'s lazy safety net, for a store nobody has visited the setup wizard on yet. A fresh install gets the full starter chart for whichever country {@link #resolveChartCountry} resolves to, in one pass; an existing store's chart is topped up by {@link #backfillMissingAccounts} instead. See {@link #seedDefaultChartIfNeeded(ChartCountry)} for the setup wizard's own explicit-country entry point. */
    @Transactional
    public void seedDefaultChartIfNeeded() {
        if (repository.count() > 0) {
            return;
        }
        seedChart(resolveChartCountry());
    }

    /**
     * Fourier: "The Config app.accounting.chart-country can be forgotten easily, what if we add
     * this on the screen then at first time a user select the country?" — {@link
     * ChartSetupController}'s own entry point: seeds the chart with whichever country the admin
     * actually picked on screen (ignoring {@code app.accounting.chart-country} entirely) and locks
     * that choice into {@code StoreSettings.chartCountry}. Rejects outright once anything has
     * already been seeded — same one-time-choice reasoning as {@link #isChartSetupNeeded}; this is
     * deliberately not a tool for switching an already-seeded store to a different country (see
     * {@link #backfillMissingAccounts}'s own javadoc for why that's a real migration this app
     * doesn't attempt).
     */
    @Transactional
    public void seedDefaultChartIfNeeded(ChartCountry explicitCountry) {
        if (repository.count() > 0) {
            throw ApiException.conflict("The chart of accounts is already set up for this store — it can't be changed from the setup wizard.");
        }
        seedChart(explicitCountry);
    }

    /** True exactly in the window before the very first account exists — when {@link ChartSetupController} shows the setup wizard in place of the normal admin screens. */
    @Transactional(readOnly = true)
    public boolean isChartSetupNeeded() {
        return repository.count() == 0;
    }

    /** What the setup wizard pre-selects before the admin picks anything themselves — see {@link #resolveChartCountry}. */
    @Transactional(readOnly = true)
    public ChartCountry getDefaultChartCountrySuggestion() {
        return resolveChartCountry();
    }

    private void seedChart(ChartCountry country) {
        List<ChartRow> chart = chart(country);
        for (ChartRow row : chart) {
            saveDefaultRow(row);
        }
        StoreSettings settings = getOrCreateStoreSettings();
        settings.setChartCountry(country);
        storeSettingsRepository.save(settings);
        auditLogService.record("CHART_OF_ACCOUNTS_SEEDED",
                "Seeded the default " + country + " chart of accounts (" + chart.size() + " accounts)", "Account", null);
    }

    /**
     * Fourier: "I want to have possibility of selection on Chart while initiating the app at first
     * time" — for a store that was seeded before its country's current starter accounts existed
     * (e.g. before the Rwanda statutory accounts, or before {@code wellKnownKey} itself existed):
     * adds whatever starter rows above are still missing by matching first on {@code wellKnownKey},
     * then falling back to matching on the legacy {@code code} value for a row that predates that
     * column (backfilling just the key onto it, leaving its name/code/description exactly as an
     * admin may have already customized them); also renames {@code TAX_PAYABLE} from the old
     * generic "Tax Payable" to its country-specific VAT/TVA label if it's still exactly that default
     * text. Idempotent and safe every startup, same convention as {@link #seedDefaultChartIfNeeded}.
     *
     * <p>This tops up a store up within its own already-configured {@link ChartCountry} — it is
     * deliberately not a tool for switching an existing, already-seeded store from one country's
     * chart to another; that's a real re-chart migration (every automatic posting's history now
     * sits against the old country's accounts) this doesn't attempt.
     */
    @Transactional
    public void backfillMissingAccounts() {
        if (repository.count() == 0) {
            // Nothing to top up — seedDefaultChartIfNeeded (called first, same DataSeeder pass) just created the full chart.
            return;
        }
        ChartCountry country = resolveChartCountry();
        int added = 0;
        int keysBackfilled = 0;
        for (ChartRow row : chart(country)) {
            Account existing = row.wellKnownKey() != null ? repository.findByWellKnownKey(row.wellKnownKey()).orElse(null) : null;
            if (existing == null) {
                existing = repository.findByCode(row.code()).orElse(null);
            }
            if (existing == null) {
                saveDefaultRow(row);
                added++;
            } else if (row.wellKnownKey() != null && existing.getWellKnownKey() == null) {
                existing.setWellKnownKey(row.wellKnownKey());
                repository.save(existing);
                keysBackfilled++;
            }
        }
        String vatLabel = country == ChartCountry.CHAD ? "État, TVA facturée (VAT Payable)" : "VAT Payable (RRA)";
        String vatDescription = country == ChartCountry.CHAD
                ? "18% TVA collected on sales, not yet remitted to Chad's Direction Générale des Impôts (DGI). Input TVA paid on purchases is tracked separately in État, TVA récupérable, not netted against this account automatically — reconcile the two when filing."
                : RWANDA_VAT_DESCRIPTION;
        repository.findByWellKnownKey(TAX_PAYABLE)
                .filter(account -> LEGACY_TAX_PAYABLE_NAME.equals(account.getName()))
                .ifPresent(account -> {
                    account.setName(vatLabel);
                    account.setDescription(vatDescription);
                    repository.save(account);
                });
        // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — a
        // store already past the rename above (its VAT Payable account is already named
        // vatLabel, not the old generic LEGACY_TAX_PAYABLE_NAME) still has the now-inaccurate
        // "doesn't model input VAT" wording in its description; refresh it whenever it's still
        // exactly that old stale text, so this doesn't silently misdescribe what the Input VAT
        // Recoverable account added just above now does. An admin who customized the description
        // themselves (it no longer matches verbatim) is left alone.
        String legacyChadVatDescription = "18% TVA collected on sales, not yet remitted to Chad's Direction Générale des Impôts (DGI). This app doesn't model input TVA on purchases, so there's no credit netted against it.";
        repository.findByWellKnownKey(TAX_PAYABLE)
                .filter(account -> LEGACY_RWANDA_VAT_DESCRIPTION.equals(account.getDescription())
                        || legacyChadVatDescription.equals(account.getDescription()))
                .ifPresent(account -> {
                    account.setDescription(vatDescription);
                    repository.save(account);
                });
        // This store was seeded before StoreSettings.chartCountry existed (or before the setup
        // wizard did) — lock in the country it's actually been running as, so a later edit to
        // app.accounting.chart-country can never silently change what backfillMissingAccounts
        // compares against on some future restart.
        StoreSettings settings = getOrCreateStoreSettings();
        if (settings.getChartCountry() == null) {
            settings.setChartCountry(country);
            storeSettingsRepository.save(settings);
        }
        if (added > 0 || keysBackfilled > 0) {
            auditLogService.record("CHART_OF_ACCOUNTS_SEEDED",
                    "Chart of accounts top-up (" + country + "): added " + added + " account(s), backfilled " + keysBackfilled + " well-known key(s)",
                    "Account", null);
        }
    }

    private void saveDefaultRow(ChartRow row) {
        Account account = new Account();
        account.setCode(row.code());
        account.setName(row.name());
        account.setType(row.type());
        account.setSystemAccount(row.systemAccount());
        account.setDescription(row.description());
        account.setWellKnownKey(row.wellKnownKey());
        account.setActive(true);
        repository.save(account);
    }

    @Transactional(readOnly = true)
    public List<AccountDto> listAll() {
        return repository.findAllByOrderByCodeAsc().stream().map(AccountDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountDto> listActive() {
        return repository.findByActiveTrueOrderByCodeAsc().stream().map(AccountDto::from).toList();
    }

    /** The Expense/Income entry screens' own category picker (Admin &gt; Accounting &gt; Expenses/Incomes &gt; New) — active accounts of exactly one type, e.g. EXPENSE for an Expense's category, REVENUE for an Income's. */
    @Transactional(readOnly = true)
    public List<AccountDto> listActiveByType(AccountType type) {
        return repository.findByActiveTrueAndTypeOrderByCodeAsc(type).stream().map(AccountDto::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountDto getById(Long id) {
        return AccountDto.from(getEntityOrThrow(id));
    }

    @Transactional
    public AccountDto create(AccountRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw ApiException.badRequest("Code is required");
        }
        if (repository.existsByCodeIgnoreCase(request.getCode())) {
            throw ApiException.conflict("An account with code '" + request.getCode() + "' already exists");
        }
        Account account = new Account();
        account.setCode(request.getCode().trim());
        applyRequest(account, request);
        Account saved = repository.save(account);
        auditLogService.record("ACCOUNT_CREATED", "Created account " + saved.getCode() + " (" + saved.getName() + ")", "Account", saved.getId());
        return AccountDto.from(saved);
    }

    @Transactional
    public AccountDto update(Long id, AccountRequest request) {
        Account account = getEntityOrThrow(id);
        if (account.isSystemAccount() && !request.isActive()) {
            throw ApiException.badRequest("'" + account.getName() + "' (" + account.getCode()
                    + ") is used automatically by the Accounting module's own postings and can't be deactivated.");
        }
        applyRequest(account, request);
        Account saved = repository.save(account);
        auditLogService.record("ACCOUNT_UPDATED", "Updated account " + saved.getCode() + " (" + saved.getName() + ")", "Account", saved.getId());
        return AccountDto.from(saved);
    }

    /** Used internally by AccountingService to resolve a well-known account — never a repository leak outside this package, same "getEntityOrThrow, package-visible where needed" convention every other service in this app already follows. See Account.wellKnownKey's own javadoc for why this resolves by key rather than by the country-specific display code. */
    Account getByWellKnownKeyOrThrow(String wellKnownKey) {
        return repository.findByWellKnownKey(wellKnownKey)
                .orElseThrow(() -> new IllegalStateException("Missing well-known account '" + wellKnownKey
                        + "' — the chart of accounts wasn't seeded correctly, or predates the per-country chart/wellKnownKey split. Restart the app (DataSeeder backfills it automatically), or re-save Admin > Accounting > Settings."));
    }

    private Account getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Account not found: " + id));
    }

    private void applyRequest(Account account, AccountRequest request) {
        account.setName(request.getName());
        account.setType(request.getType());
        account.setDescription(request.getDescription());
        account.setActive(request.isActive());
    }

    /** For AccountingReportController's Balance Sheet section labels — the account types in fixed display order. */
    public static Map<AccountType, String> displayOrder() {
        Map<AccountType, String> order = new LinkedHashMap<>();
        order.put(AccountType.ASSET, "Assets");
        order.put(AccountType.LIABILITY, "Liabilities");
        order.put(AccountType.EQUITY, "Equity");
        order.put(AccountType.REVENUE, "Revenue");
        order.put(AccountType.EXPENSE, "Expenses");
        return order;
    }
}
