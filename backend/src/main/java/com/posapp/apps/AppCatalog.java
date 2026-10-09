package com.posapp.apps;

import java.util.List;

/**
 * The fixed, code-defined list of apps this build knows how to install (Task: "plugin (or apps)
 * to be installed and configured"). Not database-backed — which apps *exist* is a code decision,
 * shipped with a release; which ones are *installed/enabled* is store-specific data (see
 * {@link InstalledApp}). Mirrors how `report-defs.ts` is a fixed frontend catalog of report
 * definitions rather than a database table.
 */
public final class AppCatalog {

    public record Definition(AppKey key, String name, String description, String category) {
    }

    private static final List<Definition> APPS = List.of(
            new Definition(
                    AppKey.EFRIS_RW,
                    "EFRIS Rwanda e-Invoicing",
                    "Rwanda Revenue Authority (RRA) Electronic Fiscal Receipting and Invoicing "
                            + "System integration. Install and enable this once you're ready to configure "
                            + "it — the specific fields it needs, and any new tables required to store what "
                            + "EFRIS returns per sale, are added in a follow-up pass once RRA's exact "
                            + "requirements are provided.",
                    "Tax & Compliance"
            ),
            new Definition(
                    AppKey.EFRIS_TD,
                    "EFRIS Chad e-Invoicing",
                    "Chad electronic invoicing (EFRIS Chad) integration over HTTP(S) with basic "
                            + "authentication, using a per-terminal SDC number, branch code, MRC, and "
                            + "integration key. Install and enable this once you're ready to configure it — "
                            + "the actual submission call and response parsing are added in a follow-up pass "
                            + "once that's provided.",
                    "Tax & Compliance"
            )
    );

    private AppCatalog() {
    }

    public static List<Definition> all() {
        return APPS;
    }

    public static Definition getOrThrow(AppKey key) {
        return APPS.stream()
                .filter(d -> d.key() == key)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown app: " + key));
    }
}
