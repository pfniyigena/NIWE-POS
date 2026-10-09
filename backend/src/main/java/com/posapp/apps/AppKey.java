package com.posapp.apps;

/**
 * Every built-in "app" (Task: "This will work as a plugin (or apps) to be installed and
 * configured") this codebase knows about — a fixed, code-defined set, not a real
 * download-a-jar-from-a-marketplace plugin system. "Installed" and "enabled" per store live in
 * {@link InstalledApp}; the catalog metadata (name/description/category) lives in
 * {@link AppCatalog}. Adding a second app later means adding one more enum value here plus one
 * more {@link AppCatalog} entry — nothing structural changes.
 */
public enum AppKey {
    /**
     * Rwanda Revenue Authority e-invoicing (EFRIS) — Task: "I want to enable EFRIS this means
     * every terminal should be connected to the external EFRIS." Deliberately just a catalog
     * entry for now: install/enable/generic-settings only. The actual submission call (what gets
     * sent to EFRIS at checkout, what comes back, what has to print on the receipt) is a
     * deliberate next step once Fourier shares EFRIS's exact field/table requirements — see the
     * README's "Apps / plugins framework" section.
     *
     * <p>Task 1: "In Apps Change EFRIS Uganda e-Invoicing Uganda Revenue Authority (URA) to EFRIS
     * Rwanda e-Invoicing Rwanda Revenue Authority (RRA)" / "yes do it for all" — this constant was
     * renamed from {@code EFRIS_UG} to {@code EFRIS_RW} as part of that pass. Since {@link
     * com.posapp.apps.InstalledApp#getAppKey()} persists this name as a plain string ({@code
     * @Enumerated(EnumType.STRING)}), any database that already had an {@code EFRIS_UG} row
     * installed/enabled before this rename won't resolve to this constant anymore — see the
     * README's dated entry for this change for the one-line SQL fix.</p>
     */
    EFRIS_RW,

    /**
     * Chad e-invoicing (EFRIS Chad) — Task: "Now I want to add new app (EFRIS Chad) with will need
     * the following settings: url, basic_auth_username, basic_password, sdc_number, branch_code,
     * mrc, integration_key. The integration with EFRIS Chad is via HTTP(S) with basic
     * authentication." Same deliberate scope as {@link #EFRIS_RW} started with: install/enable
     * plus its own structured per-terminal settings ({@code com.posapp.apps.efrischad
     * .EfrisChadSetting}) only. The actual submission call and response parsing are a later pass —
     * Fourier's own words: "I will share the resulting parsing later."
     */
    EFRIS_TD
}
