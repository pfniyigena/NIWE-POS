package com.posapp.apps.efrischad.dto;

/**
 * Just the two letterhead logos for one terminal's EFRIS Chad setting — Task 3: "Add also Chad
 * Revenue Authority logo and EFRIS logo on EFRIS Chad setting." Mirrors {@code
 * com.posapp.apps.efris.dto.EfrisLogosDto} (Rwanda's equivalent) field-for-field, just named for
 * Chad's own revenue authority: the receipt printout (any logged-in cashier, not just an admin)
 * needs these two images to render a completed sale's receipt for a terminal mapped to EFRIS Chad,
 * but has no business seeing that terminal's URL/basic-auth/SDC-number/branch-code/MRC/
 * integration-key — see EfrisChadSettingController's own endpoint split for how the permissions
 * differ between the two.
 */
public record EfrisChadLogosDto(String craLogoDataUrl, String efrisLogoDataUrl) {
    /** No {@link com.posapp.apps.efrischad.EfrisChadSetting} row yet for this terminal — both logos are simply unset, not an error. */
    public static final EfrisChadLogosDto EMPTY = new EfrisChadLogosDto(null, null);
}
