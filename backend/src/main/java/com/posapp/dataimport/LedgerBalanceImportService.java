package com.posapp.dataimport;

import com.posapp.accounting.Account;
import com.posapp.accounting.AccountRepository;
import com.posapp.common.ApiException;
import com.posapp.controls.AccountingLockService;
import com.posapp.controls.ControlsDtos.OpeningLine;
import com.posapp.controls.ControlsDtos.OpeningRequest;
import com.posapp.controls.LedgerOpeningService;
import com.posapp.dataimport.dto.ImportResultDto;
import com.posapp.dataimport.dto.SkippedRowDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.posapp.dataimport.ImportSupport.requireDate;
import static com.posapp.dataimport.ImportSupport.requireText;
import static com.posapp.dataimport.ImportSupport.text;

/**
 * The ledger's opening balances from a CSV (accountCode, debit, credit, asOf). The opening entry is posted as one balanced piece, so this importer is all-or-nothing: if any
 * row is wrong, or the file does not balance, nothing is imported and every problem is listed.
 */
@Service
public class LedgerBalanceImportService {

    private final AccountRepository accountRepository;
    private final LedgerOpeningService opening;
    private final AccountingLockService lockService;

    public LedgerBalanceImportService(AccountRepository accountRepository, LedgerOpeningService opening, AccountingLockService lockService) {
        this.accountRepository = accountRepository;
        this.opening = opening;
        this.lockService = lockService;
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    @Transactional
    public ImportResultDto importBalances(MultipartFile file, boolean dryRun, boolean balanceToEquity) {
        List<Map<String, String>> rows = ImportSupport.readRows(file);
        ImportSupport.requireColumns(rows, "accountCode", "asOf");
        List<SkippedRowDto> problems = new ArrayList<>();
        List<SkippedRowDto> accepted = new ArrayList<>();
        List<OpeningLine> lines = new ArrayList<>();
        Map<Long, Integer> seen = new HashMap<>();
        LocalDate asOf = null;
        int n = 0;
        for (Map<String, String> row : rows) {
            n++;
            try {
                String code = requireText(row, "accountCode");
                LocalDate date = requireDate(row, "asOf");
                if (asOf == null) asOf = date;
                else if (!asOf.equals(date)) throw ApiException.badRequest("asOf must be the same date on every row (this one is " + date + ", the first row says " + asOf + ")");
                Account a = accountRepository.findByCode(code).orElseThrow(() -> ApiException.notFound("No account has the code '" + code + "'"));
                if (seen.containsKey(a.getId())) throw ApiException.badRequest(a.getCode() + " " + a.getName() + " is already on row " + seen.get(a.getId()));
                BigDecimal debit = text(row, "debit") == null ? BigDecimal.ZERO : ImportSupport.requireDecimal(row, "debit");
                BigDecimal credit = text(row, "credit") == null ? BigDecimal.ZERO : ImportSupport.requireDecimal(row, "credit");
                if (debit.signum() < 0 || credit.signum() < 0) throw ApiException.badRequest("Amounts cannot be negative");
                if (debit.signum() > 0 && credit.signum() > 0) throw ApiException.badRequest("Use either debit or credit on a row, not both");
                if (debit.signum() == 0 && credit.signum() == 0) throw ApiException.badRequest("No debit or credit amount");
                seen.put(a.getId(), n);
                lines.add(new OpeningLine(a.getId(), debit, credit));
                accepted.add(new SkippedRowDto(n, a.getCode() + " " + a.getName() + ": " + (debit.signum() > 0 ? "debit " + money(debit) : "credit " + money(credit))));
            } catch (ApiException ex) {
                problems.add(new SkippedRowDto(n, ex.getMessage()));
            }
        }
        if (problems.isEmpty()) {
            try {
                OpeningRequest request = new OpeningRequest(asOf, balanceToEquity, "Opening balances (imported)", lines);
                opening.validate(request);
                lockService.assertOpen(asOf);
                opening.record(request);
            } catch (ApiException ex) {
                problems.add(new SkippedRowDto(0, "Whole file: " + ex.getMessage()));
            }
        }
        if (!problems.isEmpty()) {
            List<String> notes = new ArrayList<>();
            notes.add("Nothing will be imported until every problem is fixed: the opening balances are posted together as one balanced entry.");
            return ImportSupport.result(dryRun, n, 0, problems, List.of(), notes);
        }
        BigDecimal d = BigDecimal.ZERO, c = BigDecimal.ZERO;
        for (OpeningLine l : lines) {
            d = d.add(l.debit() == null ? BigDecimal.ZERO : l.debit());
            c = c.add(l.credit() == null ? BigDecimal.ZERO : l.credit());
        }
        List<String> notes = new ArrayList<>();
        notes.add("Total debits " + money(d) + ", total credits " + money(c) + (d.compareTo(c) != 0 ? ". The difference of " + money(d.subtract(c).abs()) + " goes to Owner's Equity." : ". The file balances."));
        return ImportSupport.result(dryRun, n, n, List.of(), accepted, notes);
    }

    public byte[] templateCsv() {
        return ("accountCode,debit,credit,asOf\n"
                + "1000,2500000,,2026-01-31\n"
                + "1500,8000000,,2026-01-31\n"
                + "3000,,10500000,2026-01-31\n").getBytes(StandardCharsets.UTF_8);
    }
}
