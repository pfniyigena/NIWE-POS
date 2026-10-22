package com.posapp.propertymgmt;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.user.AppUser;
import org.springframework.stereotype.Component;

/** Switches the module on for a request and says who may change things. Operations viewers read; Operations managers and admins write. */
@Component
public class PropertyManagementAccess {

    public record Actor(AppUser user, boolean admin, boolean viewer, boolean manager) {
    }

    private final ModuleService moduleService;

    public PropertyManagementAccess(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    public void on() {
        moduleService.requireEnabled(ModuleKey.PROPERTY_MANAGEMENT);
    }

    public static String name(AppUser u) {
        return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
    }
}
