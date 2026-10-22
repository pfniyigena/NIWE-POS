package com.posapp.restaurant;

import com.posapp.common.ListPaging;
import com.posapp.restaurant.dto.RestaurantVoidLogDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Admin > Restaurant > Voids and comps: who took what off a bill, and why. For supervisors and managers. */
@RestController
@RequestMapping("/api/restaurant/voids")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')")
public class RestaurantVoidController {

    private final RestaurantVoidService service;

    public RestaurantVoidController(RestaurantVoidService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<RestaurantVoidLogDto>> list(@RequestParam(required = false) String q, @RequestParam(required = false) String type, @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String user,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                           @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        List<RestaurantVoidLogDto> rows = service.filtered(q, type != null && !type.isBlank() ? type : status, user, from, to);
        return ListPaging.of(rows, page, size);
    }
}
