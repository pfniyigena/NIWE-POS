package com.posapp.apps.efris.dto;

/**
 * Just the two letterhead logos for one terminal's EFRIS setting — Task: "add two logo (URA LOGO,
 * EFRIS LOGO) ... added left and right on the printout." Deliberately a separate, much narrower
 * DTO than {@link EfrisTerminalDto}: the receipt printout (any logged-in cashier, not just an
 * admin) needs these two images to render a completed sale's receipt, but has no business seeing
 * that terminal's EFRIS URL/db/uid/password — see EfrisSettingController's own endpoint split for
 * how the permissions differ between the two.
 */
public record EfrisLogosDto(String rraLogoDataUrl, String efrisLogoDataUrl) {
    /** No {@link com.posapp.apps.efris.EfrisSetting} row yet for this terminal — both logos are simply unset, not an error. */
    public static final EfrisLogosDto EMPTY = new EfrisLogosDto(null, null);
}
