package com.posapp.config;

import com.posapp.accounting.AccountService;
import com.posapp.branch.BranchService;
import com.posapp.catalog.Category;
import com.posapp.catalog.CategoryRepository;
import com.posapp.catalog.Product;
import com.posapp.catalog.ProductRepository;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.inventory.StockAdjustment;
import com.posapp.inventory.StockAdjustmentRepository;
import com.posapp.inventory.StockAdjustmentStatus;
import com.posapp.paymentmethod.PaymentMethodEntity;
import com.posapp.paymentmethod.PaymentMethodRepository;
import com.posapp.register.Register;
import com.posapp.register.RegisterRepository;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import com.posapp.tax.TaxRate;
import com.posapp.tax.TaxRateRepository;
import com.posapp.user.AppUser;
import com.posapp.user.Role;
import com.posapp.user.UserRepository;
import com.posapp.warehouse.WarehouseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * First-run demo data so the app is usable immediately: an admin + cashier login (each with a
 * demo PIN too — admin 1234, cashier 5678 — for the multi-cashier-per-shift "join with PIN" flow,
 * see CashierSessionService), a handful of categories/products (barcoded, so the terminal's scan
 * flow has something to scan), a walk-in customer, and one register. Disable with
 * app.seed-demo-data=false once you have real data.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final RegisterRepository registerRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final TaxRateRepository taxRateRepository;
    private final StoreSettingsRepository storeSettingsRepository;
    private final AccountService accountService;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    /**
     * Fourier: "multi-location/multi-warehouse tracking" foundation phase — see this field's call
     * sites in {@link #run} and WarehouseService's own javadoc for the overall plan. DataSeeder
     * already has direct {@code productRepository} access for its other backfills above, so this
     * only needs the new service itself, not a new ProductRepository dependency.
     */
    private final WarehouseService warehouseService;
    /** Fourier: "How business branches can be introduced?" — seeds the default head-office branch; see Branch's javadoc. */
    private final BranchService branchService;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedEnabled;
    private final BigDecimal defaultTaxRatePercent;
    private final String defaultCurrencySymbol;

    public DataSeeder(UserRepository userRepository,
                       CategoryRepository categoryRepository,
                       ProductRepository productRepository,
                       CustomerRepository customerRepository,
                       RegisterRepository registerRepository,
                       PaymentMethodRepository paymentMethodRepository,
                       TaxRateRepository taxRateRepository,
                       StoreSettingsRepository storeSettingsRepository,
                       AccountService accountService,
                       StockAdjustmentRepository stockAdjustmentRepository,
                       WarehouseService warehouseService,
                       BranchService branchService,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.seed-demo-data:true}") boolean seedEnabled,
                       @Value("${app.pos.tax-rate:0.15}") BigDecimal defaultTaxRateFraction,
                       @Value("${app.pos.currency-symbol:$}") String defaultCurrencySymbol) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.registerRepository = registerRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.taxRateRepository = taxRateRepository;
        this.storeSettingsRepository = storeSettingsRepository;
        this.accountService = accountService;
        this.stockAdjustmentRepository = stockAdjustmentRepository;
        this.warehouseService = warehouseService;
        this.branchService = branchService;
        this.passwordEncoder = passwordEncoder;
        this.seedEnabled = seedEnabled;
        // app.pos.tax-rate is still read here (and nowhere else — see TaxRateService) purely to
        // seed the first admin-manageable TaxRate row with the same effective rate this app
        // always shipped with, so upgrading doesn't silently zero out tax on existing deployments.
        this.defaultTaxRatePercent = defaultTaxRateFraction.multiply(BigDecimal.valueOf(100));
        // Same story for app.pos.currency-symbol (previously unused anywhere) — it now just seeds
        // the singleton StoreSettings row's initial symbol; see StoreSettingsService afterward.
        this.defaultCurrencySymbol = defaultCurrencySymbol;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // StoreSettings moved ahead of the chart-related calls below (see seedStoreSettings's own
        // call site and comment, further down this method) so the singleton row — and its
        // currencySymbol default — exists before AccountService ever needs to read or lock in
        // StoreSettings.chartCountry.
        seedStoreSettings();
        // Fourier: "The Config app.accounting.chart-country can be forgotten easily, what if we
        // add this on the screen then at first time a user select the country?" — deliberately
        // NOT calling accountService.seedDefaultChartIfNeeded() here anymore. It used to run
        // unconditionally on every startup (consistent with "make all modules available without
        // enabling them" — Accounting has no on/off switch), but that's exactly what made the
        // on-screen setup wizard (ChartSetupController) impossible to ever reach: it ran before
        // any admin had a chance to log in, so the chart was always already seeded — with
        // whatever app.accounting.chart-country happened to say — by the time anyone saw a login
        // screen at all, on every profile, demo or not. Leaving the chart genuinely unseeded on a
        // brand-new store (repository.count() == 0) is what lets ChartSetupController report
        // needsSelection: true and the frontend's adminGuard redirect to the wizard. The chart
        // still gets seeded exactly as reliably as before — just later, by whichever happens
        // first: the wizard (an explicit admin choice), or AccountingService.post()'s own lazy
        // seedDefaultChartIfNeeded() call if real business activity (a sale, an expense) happens
        // before any admin ever visits the wizard, in which case app.accounting.chart-country is
        // still exactly the fallback it always was.
        //
        // backfillMissingAccounts() below is still called unconditionally every startup — it's
        // always a safe no-op on a brand-new, not-yet-seeded store (see its own count() == 0
        // guard), and still does real work for a store seeded before this wizard existed (e.g.
        // NIWE's), topping it up and locking in its StoreSettings.chartCountry on next restart —
        // see that method's own javadoc.
        accountService.backfillMissingAccounts();
        // Fourier: "Build a full permissions system" — Stock Adjustments gained a
        // PENDING/CONFIRMED/REJECTED lifecycle; every row created before this change had its
        // stock/accounting effect applied immediately under the old behavior, so it's backfilled
        // to CONFIRMED (not re-applied — the effect already happened) rather than showing up as a
        // still-pending approval nobody asked for. Runs every startup, same "not gated by a count
        // check" convention as backfillProductTaxRates below — idempotent, since it only ever
        // touches rows whose status is still null.
        backfillLegacyStockAdjustments();
        // Fourier: "multi-location/multi-warehouse tracking ... start on the foundation phase" —
        // runs unconditionally, same tier as accountService.backfillMissingAccounts() above: a
        // real deployment with demo data disabled still needs the default "Main" warehouse seeded
        // and every pre-existing product's stockQuantity mirrored into it. Called a second time at
        // the end of this method (see the comment down there) to also cover products seedProducts()
        // is about to create below, when demo data is enabled.
        warehouseService.backfillProductStock(productRepository.findAll());
        // Branches: the default head office must exist before the warehouses are attached to it.
        branchService.seedDefaultBranchIfNeeded();
        warehouseService.assignMissingBranches();
        if (!seedEnabled) {
            return;
        }
        seedUsers();
        Map<String, Category> categories = seedCategories();
        seedRegister();
        seedPaymentMethods();
        TaxRate standardTaxRate = seedTaxRate();
        seedProducts(categories, standardTaxRate);
        seedCustomer();
        backfillWalkInCustomer();
        // Runs every startup (not gated by a count check): any product that predates per-product
        // tax — created before this migration, or added some other way with no rate assigned —
        // gets the same Standard rate it effectively had under the old single-flat-rate model, so
        // upgrading an existing deployment doesn't silently zero out its tax on next checkout.
        backfillProductTaxRates(standardTaxRate);
        // Second call, same method as above: seedProducts() writes straight to productRepository
        // rather than going through CatalogService.createProduct, so it never triggers
        // WarehouseService.syncDefaultWarehouseStock on its own. Re-running the idempotent backfill
        // here is what gives those freshly-seeded demo products a ProductStock row too; it's a
        // no-op for every product the call above already covered.
        warehouseService.backfillProductStock(productRepository.findAll());
    }

    /** See the call site in {@link #run}. */
    private void backfillLegacyStockAdjustments() {
        List<StockAdjustment> legacy = stockAdjustmentRepository.findAll().stream()
                .filter(a -> a.getStatus() == null)
                .toList();
        for (StockAdjustment adjustment : legacy) {
            adjustment.setStatus(StockAdjustmentStatus.CONFIRMED);
            adjustment.setConfirmedAt(adjustment.getCreatedAt());
        }
        if (!legacy.isEmpty()) {
            stockAdjustmentRepository.saveAll(legacy);
        }
    }

    private void seedUsers() {
        if (!userRepository.existsByUsernameIgnoreCase("admin")) {
            AppUser admin = new AppUser();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("admin123"));
            admin.setPinHash(passwordEncoder.encode("1234"));
            admin.setFullName("Admin");
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
        }
        if (!userRepository.existsByUsernameIgnoreCase("cashier")) {
            AppUser cashier = new AppUser();
            cashier.setUsername("cashier");
            cashier.setPasswordHash(passwordEncoder.encode("cashier123"));
            cashier.setPinHash(passwordEncoder.encode("5678"));
            cashier.setFullName("Alex M.");
            cashier.setRole(Role.CASHIER);
            userRepository.save(cashier);
        }
    }

    private Map<String, Category> seedCategories() {
        record Seed(String name, String color) {}
        List<Seed> seeds = List.of(
                new Seed("Seed & Feed", "#8e44ad"),
                new Seed("Irrigation", "#2980b9"),
                new Seed("Beverages", "#16a085"),
                new Seed("Hardware", "#d35400")
        );
        for (Seed seed : seeds) {
            if (!categoryRepository.existsByNameIgnoreCase(seed.name())) {
                Category category = new Category();
                category.setName(seed.name());
                category.setColorHex(seed.color());
                categoryRepository.save(category);
            }
        }
        return categoryRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Category::getName, c -> c));
    }

    private void seedProducts(Map<String, Category> categories, TaxRate standardTaxRate) {
        if (productRepository.count() > 0) {
            return;
        }
        record Seed(String sku, String barcode, String name, String category, String price, String cost, int stock) {}
        // Cost is a rough demo figure (~65-70% of price) purely so the Profit reports have
        // something non-zero to show out of the box — admins should fill in real costs from
        // Admin > Products.
        List<Seed> seeds = List.of(
                new Seed("SEED-50KG", "1000000000011", "50kg Maize Seed Bag", "Seed & Feed", "120.00", "80.00", 40),
                new Seed("FEED-25KG", "1000000000028", "25kg Poultry Feed", "Seed & Feed", "45.00", "30.00", 60),
                new Seed("IRR-DRIPKIT", "1000000000035", "Drip Irrigation Kit", "Irrigation", "340.00", "230.00", 15),
                new Seed("IRR-HOSE20M", "1000000000042", "20m Garden Hose", "Irrigation", "58.00", "38.00", 25),
                new Seed("BEV-WATER1L", "1000000000059", "1L Bottled Water", "Beverages", "1.50", "0.90", 200),
                new Seed("BEV-SODA330", "1000000000066", "330ml Soda Can", "Beverages", "0.90", "0.55", 150),
                new Seed("HW-SPADE", "1000000000073", "Garden Spade", "Hardware", "22.00", "14.00", 30),
                new Seed("HW-WHEELBAR", "1000000000080", "Wheelbarrow", "Hardware", "95.00", "65.00", 8)
        );
        for (Seed seed : seeds) {
            Product product = new Product();
            product.setSku(seed.sku());
            product.setBarcode(seed.barcode());
            product.setName(seed.name());
            product.setUnitPrice(new BigDecimal(seed.price()));
            product.setCostPrice(new BigDecimal(seed.cost()));
            product.setStockQuantity(seed.stock());
            product.setCategory(categories.get(seed.category()));
            product.setTaxRate(standardTaxRate);
            productRepository.save(product);
        }
    }

    /** See the call site in {@link #run}. */
    private void backfillProductTaxRates(TaxRate standardTaxRate) {
        List<Product> untaxed = productRepository.findAll().stream()
                .filter(p -> p.getTaxRate() == null)
                .toList();
        for (Product product : untaxed) {
            product.setTaxRate(standardTaxRate);
        }
        if (!untaxed.isEmpty()) {
            productRepository.saveAll(untaxed);
        }
    }

    private void seedCustomer() {
        if (customerRepository.count() == 0) {
            Customer walkIn = new Customer();
            walkIn.setName("Walk-in Customer");
            walkIn.setWalkIn(true);
            customerRepository.save(walkIn);
        }
    }

    /**
     * Task: "since we have a default customer 'Walk-in Customer' it is better to set this
     * customer as default selected in the terminal ... instead of having null" — runs every
     * startup (not gated by a count check, same as {@link #backfillProductTaxRates}), so a store
     * that already existed before {@code Customer.walkIn} was added still gets the terminal's new
     * default-selection behavior without an admin having to do anything by hand. A no-op once any
     * customer already carries the flag (the seed above already sets it on a fresh install, and a
     * second run of this method must never re-flag a different row). Otherwise flags whichever
     * customer is named "Walk-in Customer" (case-insensitive) if one exists; if no customer has
     * that exact name either, this leaves every customer's flag false and the terminal simply
     * keeps defaulting to no customer, exactly as it did before this feature existed.
     */
    private void backfillWalkInCustomer() {
        if (customerRepository.existsByWalkInTrue()) {
            return;
        }
        customerRepository.findFirstByNameIgnoreCase("Walk-in Customer")
                .ifPresent(c -> {
                    c.setWalkIn(true);
                    customerRepository.save(c);
                });
    }

    /**
     * Admin > Settings > Business Details / System settings (Task family). Seeded once as a mostly
     * empty row — an admin fills in the business's actual name/address/etc. themselves — except
     * currencySymbol, which carries over app.pos.currency-symbol's value so upgrading an existing
     * deployment doesn't silently reset the symbol admins may already expect. Now called
     * unconditionally (moved ahead of the {@code app.seed-demo-data} gate in {@link #run}) rather
     * than only when demo data is on — a real deployment with demo data disabled previously never
     * got this row pre-created at all, which meant {@code StoreSettings.chartCountry} (see that
     * field's own javadoc) had nowhere to be locked into until some other code path happened to
     * create the row. Idempotent either way via the count check below, same as every other call
     * in this file.
     */
    private void seedStoreSettings() {
        if (storeSettingsRepository.count() == 0) {
            StoreSettings settings = new StoreSettings();
            settings.setCurrencySymbol(defaultCurrencySymbol);
            storeSettingsRepository.save(settings);
        }
    }

    private void seedRegister() {
        if (registerRepository.count() == 0) {
            Register register = new Register();
            register.setName("Front Counter");
            register.setLocation("Main Store");
            registerRepository.save(register);
        }
    }

    /**
     * These replace what used to be a hardcoded PaymentMethod enum — seeded once so
     * checkout/returns keep working out of the box; admins can add/rename/deactivate methods
     * afterward from Admin > Payment methods. Only CASH is flagged cash-equivalent, matching the
     * old enum comparison (payment.getMethod() == PaymentMethod.CASH) it replaces. CREDIT is the
     * one flagged store-credit (Task family: "Cash + credit partial payment" / "100% credit sale").
     */
    private void seedPaymentMethods() {
        record Seed(String code, String name, boolean cash, boolean credit) {}
        // Order here also seeds sortOrder (Task: "Make payment method order dynamic so I can set
        // the first, second on checkout screen") — 0, 1, 2... in this list order, so a fresh
        // install's checkout button order matches this same order out of the box; an admin can
        // rearrange it afterward from Admin > Settings > Payment methods.
        List<Seed> seeds = List.of(
                new Seed("CASH", "Cash", true, false),
                new Seed("CARD", "Card", false, false),
                new Seed("MOBILE_MONEY", "Mobile Money", false, false),
                new Seed("BANK_TRANSFER", "Bank Transfer", false, false),
                // Task: "Cash + credit partial payment" / "100% credit sale" — a normal tender row
                // flagged credit instead of a separate checkout flow; see PaymentMethodEntity.credit.
                new Seed("CREDIT", "Store Credit", false, true)
        );
        int order = 0;
        for (Seed seed : seeds) {
            if (!paymentMethodRepository.existsByCodeIgnoreCase(seed.code())) {
                PaymentMethodEntity pm = new PaymentMethodEntity();
                pm.setCode(seed.code());
                pm.setName(seed.name());
                pm.setCash(seed.cash());
                pm.setCredit(seed.credit());
                pm.setSortOrder(order);
                pm.setActive(true);
                paymentMethodRepository.save(pm);
            }
            order++;
        }
    }

    /**
     * Replaces what used to be the single hardcoded {@code app.pos.tax-rate} property (see
     * TaxRateService) — seeded once, active, at whatever that property is set to, so upgrading
     * doesn't change behavior out of the box. Admins manage rates afterward from
     * Admin > Settings > Taxes, and assign them per-product from Admin > Inventory. Returns the
     * Standard rate either way (freshly created, or the existing one on a re-run) so callers can
     * assign it to seeded/pre-existing products.
     */
    private TaxRate seedTaxRate() {
        if (taxRateRepository.count() == 0) {
            TaxRate taxRate = new TaxRate();
            taxRate.setCode("STD");
            taxRate.setName("Standard");
            taxRate.setRatePercent(defaultTaxRatePercent);
            taxRate.setActive(true);
            return taxRateRepository.save(taxRate);
        }
        return taxRateRepository.findByCodeIgnoreCase("STD").orElseGet(() -> taxRateRepository.findAllByOrderByNameAsc().get(0));
    }
}
