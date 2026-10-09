package com.posapp.apps.efrischad.dto;

import com.posapp.apps.efrischad.EfrisChadSetting;
import com.posapp.register.Register;

/** One terminal's (Register's) EFRIS Chad connection settings, merged with its EfrisChadSetting row if one exists yet. */
public record EfrisChadTerminalDto(
        Long registerId,
        String registerName,
        String location,
        String url,
        String basicAuthUsername,
        String basicPassword,
        String sdcNumber,
        String branchCode,
        String mrc,
        String integrationKey,
        /** Task 3: "Add also Chad Revenue Authority logo and EFRIS logo on EFRIS Chad setting" — see EfrisChadSetting.craLogoDataUrl's javadoc. */
        String craLogoDataUrl,
        String efrisLogoDataUrl
) {
    public static EfrisChadTerminalDto from(Register register, EfrisChadSetting setting) {
        return new EfrisChadTerminalDto(
                register.getId(),
                register.getName(),
                register.getLocation(),
                setting != null ? setting.getUrl() : null,
                setting != null ? setting.getBasicAuthUsername() : null,
                setting != null ? setting.getBasicPassword() : null,
                setting != null ? setting.getSdcNumber() : null,
                setting != null ? setting.getBranchCode() : null,
                setting != null ? setting.getMrc() : null,
                setting != null ? setting.getIntegrationKey() : null,
                setting != null ? setting.getCraLogoDataUrl() : null,
                setting != null ? setting.getEfrisLogoDataUrl() : null
        );
    }
}
