package com.posapp.apps.efris.dto;

import com.posapp.apps.efris.EfrisSetting;
import com.posapp.register.Register;

/** One terminal's (Register's) EFRIS connection settings, merged with its EfrisSetting row if one exists yet. */
public record EfrisTerminalDto(
        Long registerId,
        String registerName,
        String location,
        String efrisUrl,
        String efrisDb,
        Integer efrisUid,
        String efrisPassword,
        /** Task: "add two logo (URA LOGO, EFRIS LOGO)" — see EfrisSetting.rraLogoDataUrl's javadoc. */
        String rraLogoDataUrl,
        String efrisLogoDataUrl
) {
    public static EfrisTerminalDto from(Register register, EfrisSetting setting) {
        return new EfrisTerminalDto(
                register.getId(),
                register.getName(),
                register.getLocation(),
                setting != null ? setting.getEfrisUrl() : null,
                setting != null ? setting.getEfrisDb() : null,
                setting != null ? setting.getEfrisUid() : null,
                setting != null ? setting.getEfrisPassword() : null,
                setting != null ? setting.getRraLogoDataUrl() : null,
                setting != null ? setting.getEfrisLogoDataUrl() : null
        );
    }
}
