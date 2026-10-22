package com.posapp.hr;

import com.posapp.hr.dto.HrDtos.*;
import com.posapp.hr.dto.HrRequests.*;
import com.posapp.hr.dto.MyHrDtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * "My HR": employee self-service. Open to every signed-in user (no HR or Payroll access needed); it only ever shows the
 * employee record linked to the caller's own login. Lives under /api/hr/me so the Employees license gate applies.
 */
@RestController
@RequestMapping("/api/hr/me")
@PreAuthorize("isAuthenticated()")
public class MyHrController {

    private final MyHrService service;
    private final AttendanceService attendanceService;

    public MyHrController(MyHrService service, AttendanceService attendanceService) {
        this.service = service;
        this.attendanceService = attendanceService;
    }

    private static ResponseEntity<byte[]> pdf(PayrollPaperService.Pdf f) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.filename() + "\"").body(f.bytes());
    }

    @GetMapping
    public MyOverviewDto overview() {
        return service.overview();
    }

    @PutMapping("/contact")
    public MyProfileDto contact(@RequestBody MyContactRequest request) {
        return service.updateContact(request);
    }

    @GetMapping("/payslips")
    public List<MyPayslipRowDto> payslips(@RequestParam(required = false) Integer year) {
        return service.payslips(year);
    }

    @GetMapping("/payslips/{runId}")
    public MyPayslipDto payslip(@PathVariable Long runId) {
        return service.payslip(runId);
    }

    @GetMapping("/payslips/{runId}/pdf")
    public ResponseEntity<byte[]> payslipPdf(@PathVariable Long runId) {
        return pdf(service.payslipPdf(runId));
    }

    @GetMapping("/certificate")
    public ResponseEntity<byte[]> certificate(@RequestParam int year) {
        return pdf(service.certificate(year));
    }

    @GetMapping("/leave-types")
    public List<LeaveTypeDto> leaveTypes() {
        return service.leaveTypes();
    }

    @GetMapping("/leave-balances")
    public List<LeaveBalanceDto> balances(@RequestParam(required = false) Integer year) {
        return service.balances(year != null ? year : LocalDate.now().getYear());
    }

    /** Working days of a period for the leave form (weekends and public holidays are not counted). */
    @GetMapping("/working-days")
    public java.math.BigDecimal workingDays(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(defaultValue = "false") boolean startHalf, @RequestParam(defaultValue = "false") boolean endHalf) {
        return service.workingDays(from, to, startHalf, endHalf);
    }

    @GetMapping("/leaves")
    public List<LeaveRequestDto> leaves() {
        return service.leaves();
    }

    @PostMapping("/leaves")
    public LeaveRequestDto requestLeave(@Valid @RequestBody LeaveRequestRequest request) {
        return service.requestLeave(request);
    }

    @PostMapping("/leaves/{id}/cancel")
    public LeaveRequestDto cancel(@PathVariable Long id, @RequestBody(required = false) DecisionRequest request) {
        return service.cancelLeave(id, request);
    }

    @GetMapping("/documents")
    public List<com.posapp.hr.dto.HrDocDtos.EmployeeDocumentDto> documents() {
        return service.documents();
    }

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<byte[]> documentFile(@PathVariable Long id) {
        return EmployeeDocumentController.download(service.documentFile(id));
    }

    @GetMapping("/attendance")
    public List<AttendanceDto> attendance(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.attendance(from, to);
    }

    @GetMapping("/attendance/today")
    public TodayDto today() {
        return attendanceService.today();
    }

    @PostMapping("/attendance/clock-in")
    public AttendanceDto clockIn() {
        return attendanceService.clockIn();
    }

    @PostMapping("/attendance/clock-out")
    public AttendanceDto clockOut() {
        return attendanceService.clockOut();
    }
}
