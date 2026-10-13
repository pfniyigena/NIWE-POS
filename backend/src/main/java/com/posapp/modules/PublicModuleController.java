package com.posapp.modules;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * No login (everything under /api/public/** is open — see SecurityConfig). Tells the public home page which of the public
 * services are available: only the two modules that have a public page, and only on/off, nothing else.
 */
@RestController
@RequestMapping("/api/public/modules")
public class PublicModuleController {

    private final ModuleService service;

    public PublicModuleController(ModuleService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Boolean> publicModules() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put(ModuleKey.RESTAURANT.name(), service.isEnabled(ModuleKey.RESTAURANT));
        result.put(ModuleKey.HELPDESK.name(), service.isEnabled(ModuleKey.HELPDESK));
        return result;
    }
}
