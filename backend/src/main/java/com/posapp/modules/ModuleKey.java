package com.posapp.modules;

import java.util.List;

/**
 * The modules listed under Settings > Modules. Sales, Purchases and Accounting are core: always on and cannot be switched
 * off. Every other module starts switched off (menu hidden) until an admin switches it on. Once an
 * optional module is activated it stays active — it cannot be deactivated. Foundations
 * that every module shares (inventory/products, customers, suppliers, staff, settings) are not modules and are always there.
 */
public enum ModuleKey {
    // ---- core: always on ----
    SALES("Sales", "🧾",
            "Quotations, sale orders, invoices, credit notes, customers and customer payments.",
            true),
    PURCHASES("Purchases", "📥",
            "Purchase requisitions and orders, bills, purchase returns, suppliers and supplier payments.",
            true),
    ACCOUNTING("Accounting", "📒",
            "Chart of accounts, journal entries, cost centers, incomes and the accounting reports. Sales, purchases and stock post here automatically.",
            true),
    // ---- optional: off until an admin switches them on ----
    POS("POS", "🛒",
            "Cash registers and terminals, cashier sessions and shifts, returns, receipts and POS reports.",
            false),
    HOTEL("Hotel", "🏨",
            "Rooms, reservations, front desk, guest folios, housekeeping and maintenance. Adds the \"Charge to room\" payment method to the POS and the Restaurant.",
            false),
    RESTAURANT("Restaurant", "🍽️",
            "Tables, menu, orders, kitchen display, deliveries, reservations and wastage.",
            false),
    MANUFACTURING("Manufacturing", "🏭",
            "Bills of materials, production plans and orders, workstations, routings and overhead costs.",
            false),
    SUBSCRIPTIONS("Subscriptions", "🔁",
            "Subscription plans and recurring billing of customers.",
            false),
    HELPDESK("Help Desk", "🎫",
            "Customer and internal support tickets, categories and reports.",
            false),
    EXPENSES("Expenses", "💸",
            "Recording business expenses and recurring expense templates (expense reports stay under Accounting).",
            false),
    ASSETS("Assets Management", "🏗️",
            "Fixed-asset register, asset categories, monthly depreciation, disposals and asset reports. Posts to Accounting automatically.",
            false),
    INSTALLMENTS("Installments", "🗓️",
            "Let customers pay an invoice in dated instalments: deposit, interest, late fees, reminders, a dashboard and reports. Payments go through the invoice, so receipts and Accounting stay the same.",
            false),
    EMPLOYEES("Employees", "👥",
            "Employee records (personal, job and bank details), departments and the employee directory. The base of the other HR modules.",
            false),
    ORG_STRUCTURE("Organizational Structure", "🧭",
            "Departments, positions, reporting lines and the organisation chart.",
            false),
    ATTENDANCE("Attendance & Leaves", "🕒",
            "Daily attendance and clocking, leave types, leave requests with approval, leave balances and public holidays.",
            false),
    CONTRACTS("Contracts", "📄",
            "Employment contracts: type, dates, salary and allowances, renewals and expiry alerts.",
            false),
    PAYROLL("Payroll", "💰",
            "Monthly payroll with income tax (PAYE) and statutory contributions, payslips and automatic accounting entries.",
            false);

    private final String label;
    private final String icon;
    private final String description;
    private final boolean core;

    ModuleKey(String label, String icon, String description, boolean core) {
        this.label = label;
        this.icon = icon;
        this.description = description;
        this.core = core;
    }

    public String label() {
        return label;
    }

    public String icon() {
        return icon;
    }

    public String description() {
        return description;
    }

    /** Core modules are always on. */
    public boolean core() {
        return core;
    }

    /** Every optional module starts switched off (menu hidden); core modules are always on. */
    public boolean defaultEnabled() {
        return core;
    }

    /**
     * Modules this one is integrated with and cannot work without. Activating this module activates those too. The HR modules build on Employees (Payroll also needs Contracts and Attendance & Leaves). Hotel needs the POS and the Restaurant ("Charge to room" at the
     * POS and the restaurant till, room service).
     */
    public List<ModuleKey> requires() {
        return switch (this) {
            case HOTEL -> List.of(POS, RESTAURANT);
            case ORG_STRUCTURE, ATTENDANCE, CONTRACTS -> List.of(EMPLOYEES);
            case PAYROLL -> List.of(EMPLOYEES, CONTRACTS, ATTENDANCE);
            default -> List.of();
        };
    }
}
