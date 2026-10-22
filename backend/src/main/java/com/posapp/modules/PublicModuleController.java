package com.posapp.modules;

import com.posapp.hotel.HotelOnlineService;
import com.posapp.realestate.RePublicService;
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
    private final RePublicService realEstate;
    private final HotelOnlineService hotel;

    public PublicModuleController(ModuleService service, RePublicService realEstate, HotelOnlineService hotel) {
        this.hotel = hotel;
        this.service = service;
        this.realEstate = realEstate;
    }

    @GetMapping
    public Map<String, Boolean> publicModules() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put(ModuleKey.RESTAURANT.name(), service.isEnabled(ModuleKey.RESTAURANT));
        result.put(ModuleKey.HELPDESK.name(), service.isEnabled(ModuleKey.HELPDESK));
        result.put(ModuleKey.BOOKING.name(), service.isEnabled(ModuleKey.BOOKING));
        // The listings website needs the module and its own switch in the Real Estate settings.
        result.put(ModuleKey.REAL_ESTATE.name(), realEstate.isOpen());
        // The hotel's booking page needs the module and its own switch in Hotel > Online booking.
        result.put(ModuleKey.HOTEL.name(), hotel.isOpen());
        return result;
    }
}
