package com.posapp.user;

/**
 * The areas of the system a {@link CustomRole} can be given access to. Each area is granted at one of two
 * levels ({@link AccessLevel}): VIEW (read only) or MANAGE (create, edit, delete — which includes VIEW).
 * Everything not listed here (staff and roles, settings, backup, branches, data import, audit log, payment
 * methods, tax rates, apps) stays ADMIN-only.
 */
public enum AccessArea {
    SALES("Sales and customers", "Customers, customer credit, sales orders and returns, quotations, registers, sessions and shifts."),
    BILLING("Billing and payments", "Invoices, bills, customer and supplier payments, recurring invoices and recurring expenses."),
    INVENTORY("Inventory and purchasing", "Products, categories, suppliers, purchase orders and requisitions, stock adjustments, counts and transfers, warehouses, packaging, batch and serial tracking."),
    OPERATIONS("Operations", "Manufacturing, restaurant, subscriptions and help desk."),
    ACCOUNTING("Accounting", "Chart of accounts, journal entries, expenses, incomes and cost centers."),
    HOTEL("Hotel", "Room types and rooms, guests, reservations, front desk (check-in, check-out, guest bills), housekeeping, maintenance and hotel reports."),
    HR("Human resources", "Employees, departments and positions, attendance, leave requests and approvals, public holidays and HR reports (without salaries)."),
    PAYROLL("Payroll and contracts", "Employment contracts and salaries, payroll rules, payroll runs, payslips and the salary reports. Sensitive: salary data."),
    REPORTS("Reports and dashboard", "All reports (sales, profit, inventory, credit, payables, accounting, help desk) and the dashboard. View only.");

    private final String label;
    private final String description;

    AccessArea(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
