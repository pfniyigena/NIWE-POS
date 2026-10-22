package com.posapp.restaurant;

import com.posapp.common.ListPaging;
import com.posapp.restaurant.dto.DayCloseRequest;
import com.posapp.restaurant.dto.DayCloseRowDto;
import com.posapp.restaurant.dto.EndOfDayDto;
import com.posapp.restaurant.dto.ReopenRequest;
import com.posapp.restaurant.dto.RestaurantAnalysisDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Restaurant > Sales analysis and Restaurant > End of day (cash-up / Z-report). For supervisors and managers; closing the day needs a manager. */
@RestController
@RequestMapping("/api/restaurant/reports")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')")
public class RestaurantReportController {

    private final RestaurantReportService service;

    public RestaurantReportController(RestaurantReportService service) {
        this.service = service;
    }

    @GetMapping("/analysis")
    public RestaurantAnalysisDto analysis(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.analysis(from, to);
    }

    @GetMapping("/end-of-day")
    public EndOfDayDto endOfDay(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                @RequestParam(required = false) BigDecimal openingFloat) {
        return service.endOfDay(date, openingFloat);
    }

    @PostMapping("/end-of-day/close")
    public EndOfDayDto close(@RequestBody DayCloseRequest request) {
        return service.close(request);
    }

    @PostMapping("/end-of-day/{date}/reopen")
    public EndOfDayDto reopen(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @RequestBody ReopenRequest request) {
        return service.reopen(date, request.getReason());
    }

    @GetMapping("/end-of-day/history")
    public ResponseEntity<List<DayCloseRowDto>> history(@RequestParam(required = false) String status,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(service.history(status, from, to), page, size);
    }
}
