package com.posapp.accounting;

/**
 * Fourier: "I want to have possibility of selection on Chart while initiating the app at first
 * time" — which country-specific starter chart of accounts {@link AccountService} seeds on a
 * brand-new store. Originally chosen purely via the {@code app.accounting.chart-country} property
 * (still a valid starting point for a scripted/headless deployment — see
 * {@code application-niwe.properties}, the one per-client profile this app ships with); now, per
 * Fourier's follow-up ("The Config app.accounting.chart-country can be forgotten easily, what if
 * we add this on the screen then at first time a user select the country?"), the primary path is
 * {@link ChartSetupController}'s on-screen wizard — the first ADMIN to log in on a store with no
 * chart yet is redirected there before anything else, and picks RWANDA or CHAD explicitly.
 * Whichever path seeds the chart first locks its choice into {@code StoreSettings.chartCountry}
 * (see {@code AccountService#resolveChartCountry}), which is what every later read actually uses —
 * the property only matters in the window before that, so it can no longer be silently forgotten
 * or changed out from under an already-running store. There is deliberately no way to change the
 * locked-in choice afterward from the running app either: switching an already-seeded store from
 * one country's chart to another is a real re-chart migration this doesn't attempt (see
 * {@link AccountService#backfillMissingAccounts} javadoc).
 *
 * <p>{@link #RWANDA} is an IFRS country with no single mandated chart for private businesses, so
 * its chart is this app's own original 1000-5900 numbering, extended with the Rwandan statutory
 * accounts (VAT/RRA, PAYE, RSSB) a real business needs. {@link #CHAD} is an OHADA member state, so
 * it gets the real SYSCOHADA numbering (512 Banques, 701 Ventes, 4431 TVA facturée, etc.) — see
 * {@link AccountService}'s own javadoc for the full reasoning and a caveat on a couple of codes
 * that weren't independently verifiable from a primary OHADA source.
 */
public enum ChartCountry {
    RWANDA,
    CHAD;

    /** Parses {@code app.accounting.chart-country}'s raw string value — case-insensitive, trimmed — failing fast with every valid option listed rather than silently falling back, since getting this wrong on a brand-new store means reseeding the wrong country's chart. */
    public static ChartCountry fromConfigValue(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase();
        for (ChartCountry country : values()) {
            if (country.name().equals(normalized)) {
                return country;
            }
        }
        throw new IllegalStateException("Invalid app.accounting.chart-country value '" + raw
                + "' — must be one of " + java.util.Arrays.toString(values()));
    }
}
