package com.posapp.register;

import com.posapp.common.ListPaging;
import com.posapp.register.dto.PosExceptionDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** The voids-and-returns log (Sales area). */
@RestController
@RequestMapping("/api/pos")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_VIEW') or hasAuthority('AREA_SALES_MANAGE')")
public class PosExceptionController {

    private final PosExceptionService service;

    public PosExceptionController(PosExceptionService service) {
        this.service = service;
    }

    @GetMapping("/exceptions")
    public ResponseEntity<List<PosExceptionDto>> list(@RequestParam(required = false) String q, @RequestParam(required = false) String type, @RequestParam(required = false) Long registerId,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(service.filtered(q, type, registerId, from, to), page, size);
    }
}
