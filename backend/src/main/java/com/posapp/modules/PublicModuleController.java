package com.posapp.modules;

import com.posapp.booking.BookingSetupService;
import com.posapp.helpdesk.HelpDeskSettingsService;
import com.posapp.hotel.HotelOnlineService;
import com.posapp.realestate.RePublicService;
import com.posapp.restaurant.RestaurantSettingsService;
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
    private final HelpDeskSettingsService helpDesk;
    private final RestaurantSettingsService restaurant;
    private final BookingSetupService booking;
    private final com.posapp.onlinestore.OnlineStoreService onlineStore;

    public PublicModuleController(ModuleService service, RePublicService realEstate, HotelOnlineService hotel,
                                  HelpDeskSettingsService helpDesk, RestaurantSettingsService restaurant, BookingSetupService booking,
                                  com.posapp.onlinestore.OnlineStoreService onlineStore) {
        this.onlineStore = onlineStore;
        this.helpDesk = helpDesk;
        this.restaurant = restaurant;
        this.booking = booking;
        this.hotel = hotel;
        this.service = service;
        this.realEstate = realEstate;
    }

    @GetMapping
    public Map<String, Boolean> publicModules() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        // Each public page needs its module on and its own "Allow public website" switch in that module's settings.
        result.put(ModuleKey.RESTAURANT.name(), service.isEnabled(ModuleKey.RESTAURANT) && restaurant.isPublicOpen());
        result.put(ModuleKey.HELPDESK.name(), service.isEnabled(ModuleKey.HELPDESK) && helpDesk.isPublicOpen());
        result.put(ModuleKey.BOOKING.name(), service.isEnabled(ModuleKey.BOOKING) && booking.settingsEntity().isPublicEnabled());
        // The listings website needs the module and its own switch in the Real Estate settings.
        result.put(ModuleKey.REAL_ESTATE.name(), realEstate.isOpen());
        // The hotel's booking page needs the module and its own switch in Hotel > Online booking.
        result.put(ModuleKey.HOTEL.name(), hotel.isOpen());
        // The online shop needs the module and its own switch in Online Store > Settings.
        result.put(ModuleKey.ONLINE_STORE.name(), service.isEnabled(ModuleKey.ONLINE_STORE) && onlineStore.isOpen());
        return result;
    }
}
