package com.posapp.config;

import com.posapp.settings.StoreSettingsService;
import com.posapp.user.CustomRole;
import com.posapp.user.CustomRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates two ready-made roles the first time the app starts with custom roles available: a Manager (sales,
 * inventory and operations, plus reports) and an Accountant (accounting and billing, plus read-only inventory,
 * sales and reports). They are ordinary roles — edit or delete them on the Roles screen. Runs once only; deleting
 * them never brings them back.
 */
@Component
public class CustomRoleSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CustomRoleSeeder.class);

    private final CustomRoleRepository repository;
    private final StoreSettingsService storeSettingsService;

    public CustomRoleSeeder(CustomRoleRepository repository, StoreSettingsService storeSettingsService) {
        this.repository = repository;
        this.storeSettingsService = storeSettingsService;
    }

    @Override
    public void run(String... args) {
        try {
            if (storeSettingsService.isCustomRolesSeeded()) return;
            seed("Manager", "Runs day-to-day operations: sales, inventory and purchasing, manufacturing, restaurant, subscriptions and help desk, with access to all reports. No access to staff, settings, backup or accounting.",
                    Set.of("SALES_MANAGE", "INVENTORY_MANAGE", "OPERATIONS_MANAGE", "REPORTS_VIEW"));
            seed("Accountant", "Keeps the books: accounting, invoices, bills and payments, with read-only access to sales, inventory and all reports. No access to staff or settings.",
                    Set.of("ACCOUNTING_MANAGE", "BILLING_MANAGE", "SALES_VIEW", "INVENTORY_VIEW", "REPORTS_VIEW"));
            storeSettingsService.markCustomRolesSeeded();
        } catch (Exception ex) {
            log.warn("Could not create the default Manager and Accountant roles: {}", ex.getMessage());
        }
    }

    private void seed(String name, String description, Set<String> grants) {
        if (repository.existsByNameIgnoreCase(name)) return;
        CustomRole role = new CustomRole();
        role.setName(name);
        role.setDescription(description);
        role.getGrants().addAll(grants);
        repository.save(role);
    }
}
