package com.posapp.apps.dto;

import com.posapp.apps.AppCatalog;
import com.posapp.apps.AppKey;
import com.posapp.apps.InstalledApp;

/** One row of the Apps catalog, merged with this store's install/enable state — see AppsService.list(). */
public record AppDto(
        String key,
        String name,
        String description,
        String category,
        boolean installed,
        boolean enabled
) {
    public static AppDto notInstalled(AppCatalog.Definition def) {
        return new AppDto(def.key().name(), def.name(), def.description(), def.category(), false, false);
    }

    public static AppDto from(AppCatalog.Definition def, InstalledApp installedApp) {
        return new AppDto(def.key().name(), def.name(), def.description(), def.category(),
                installedApp.isInstalled(), installedApp.isEnabled());
    }
}
