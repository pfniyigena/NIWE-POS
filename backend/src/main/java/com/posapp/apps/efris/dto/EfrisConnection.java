package com.posapp.apps.efris.dto;

/**
 * The connection details one terminal needs to submit an invoice to EFRIS — deliberately a plain
 * record of primitives with no reference back to {@code Register}/{@code EfrisSetting}, so
 * {@code order} (the only caller, via {@code EfrisSettingService.getConnectionForRegister}) never
 * needs to depend on those types, only this one.
 */
public record EfrisConnection(String url, String db, Integer uid, String password) {
}
