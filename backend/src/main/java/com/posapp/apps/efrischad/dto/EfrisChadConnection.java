package com.posapp.apps.efrischad.dto;

/**
 * The connection details one terminal needs to submit an invoice to EFRIS Chad — deliberately a
 * plain record of primitives with no reference back to {@code Register}/{@code EfrisChadSetting},
 * mirroring {@code com.posapp.apps.efris.dto.EfrisConnection} (Rwanda's equivalent) so {@code
 * order} (the only caller, via {@code EfrisChadSettingService.getConnectionForRegister}) never
 * needs to depend on those types, only this one.
 */
public record EfrisChadConnection(
        String url,
        String basicAuthUsername,
        String basicPassword,
        String sdcNumber,
        String branchCode,
        String mrc,
        String integrationKey
) {
}
