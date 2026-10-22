package com.posapp.propertymgmt;

import com.posapp.propertymgmt.dto.PropertyMgmtStatementDtos.StatementDto;
import com.posapp.propertymgmt.dto.PropertyMgmtStatementDtos.StatementLineDto;
import com.posapp.propertymgmt.dto.PropertyMgmtStatementDtos.StatementRow;
import com.posapp.reports.ReportExportService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** The owner's copy of a statement as a PDF or an Excel file. Used for the download and for the e-mail, so both send the same thing. */
@Component
public class PmStatementFiles {

    private final ReportExportService exportService;

    public PmStatementFiles(ReportExportService exportService) {
        this.exportService = exportService;
    }

    public byte[] build(StatementDto d, boolean xlsx) {
        StatementRow st = d.statement();
        List<String> headers = List.of("Date", "Property", "Unit", "Description", "Reference", "Amount");
        List<List<String>> rows = new ArrayList<>();
        for (StatementLineDto l : d.lines()) {
            rows.add(List.of(String.valueOf(l.date()), nz(l.propertyName()), nz(l.unitNo()), l.description(), nz(l.reference()), l.amount().toPlainString()));
        }
        rows.add(List.of("", "", "", "Rent collected", "", st.rentCollected().toPlainString()));
        rows.add(List.of("", "", "", "Management fee", "", st.managementFee().negate().toPlainString()));
        if (st.vat().signum() != 0) rows.add(List.of("", "", "", "VAT on management fee", "", st.vat().negate().toPlainString()));
        rows.add(List.of("", "", "", "Repairs paid by owner", "", st.repairs().negate().toPlainString()));
        if (st.adjustments().signum() != 0) rows.add(List.of("", "", "", "Adjustments", "", st.adjustments().toPlainString()));
        rows.add(List.of("", "", "", "NET PAYABLE TO OWNER", "", st.netPayable().toPlainString()));
        String title = "Owner statement " + st.code() + " - " + st.ownerName() + " (" + st.periodFrom() + " to " + st.periodTo() + ")";
        return xlsx ? exportService.toXlsx(title, headers, rows) : exportService.toPdf(title, headers, rows);
    }

    public String filename(StatementRow st, boolean xlsx) {
        return st.code().toLowerCase() + (xlsx ? ".xlsx" : ".pdf");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
