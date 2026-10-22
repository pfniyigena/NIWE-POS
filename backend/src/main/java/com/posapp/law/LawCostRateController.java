package com.posapp.law;

import com.posapp.law.LawCostRateService.RateDto;
import com.posapp.law.LawCostRateService.RatesRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** {@code /api/law/cost-rates}: the cost of an hour of each lawyer's time. Admin only. */
@RestController
@RequestMapping("/api/law/cost-rates")
@PreAuthorize("hasRole('ADMIN')")
public class LawCostRateController {

    private final LawCostRateService service;

    public LawCostRateController(LawCostRateService service) {
        this.service = service;
    }

    @GetMapping
    public List<RateDto> list() {
        return service.list();
    }

    @PutMapping
    public List<RateDto> save(@RequestBody RatesRequest request) {
        return service.save(request);
    }
}
