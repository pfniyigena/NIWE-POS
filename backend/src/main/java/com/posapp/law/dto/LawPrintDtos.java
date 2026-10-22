package com.posapp.law.dto;

import com.posapp.law.TrustType;
import com.posapp.law.dto.LawBillingDtos.BillingDto;
import com.posapp.law.dto.LawDtos.MatterDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the Law Firm printed papers are built from. */
public final class LawPrintDtos {

    private LawPrintDtos() {
    }

    /** A paper and the name it is saved under. */
    public record Printed(byte[] content, String fileName) {
    }

    /** E-mailed to the client: who it went to and what to tell the person. */
    public record MailedClient(String sentTo, String message) {
    }

    /** One posted client-money movement, for its receipt (money in) or voucher (money out). */
    public record TrustReceiptData(String number, TrustType type, LocalDate txDate, String matterNumber, String matterTitle, String clientName, String partyName, BigDecimal amount,
                                   String method, String reference, String purpose, String invoiceNumber, String requestedBy, String approvedBy, BigDecimal balanceAfter,
                                   String bankName, String accountNumber) {
    }

    public record StatementLine(LocalDate date, String number, String what, String party, String reference, BigDecimal moneyIn, BigDecimal moneyOut, BigDecimal balance) {
    }

    public record WaitingLine(LocalDate date, String number, String what, String party, BigDecimal amount) {
    }

    /** A client's trust ledger for one matter over a period. */
    public record TrustStatementData(String matterNumber, String matterTitle, String clientName, String bankName, String accountNumber, LocalDate from, LocalDate to,
                                     BigDecimal opening, List<StatementLine> lines, BigDecimal totalIn, BigDecimal totalOut, BigDecimal closing, List<WaitingLine> waiting) {
    }

    /** What the person writes in the letter: the scope of work (engagement letter), extra terms or notes, and the date on the letter (today when empty). */
    public record PaperRequest(String scope, String terms, String notes, LocalDate letterDate) {
    }

    /** A paper kept in the matter's documents. */
    public record SavedPaper(Long documentId, String name, String message) {
    }

    /** The matter file cover sheet: the matter with its team, parties and open deadlines, and who has printed it. */
    public record CoverData(MatterDto matter, String printedBy, LocalDate printedOn, boolean trustEnabled, java.math.BigDecimal trustHeld) {
    }

    /** The engagement and fee letter: the matter, its fee terms, rates and stages, and what the lawyer typed. */
    public record EngagementData(MatterDto matter, BillingDto billing, String scope, String terms, LocalDate letterDate, boolean trustEnabled, String signedBy) {
    }

    /** The closing letter: the matter, the final figures and any client money still held. */
    public record ClosingData(MatterDto matter, BillingDto billing, java.math.BigDecimal trustHeld, boolean trustEnabled, String notes, LocalDate letterDate, String signedBy) {
    }

    /** What the Papers tab shows before the person prints: things to look at first, and whether e-mailing is possible. */
    public record PaperChecks(boolean closed, String status, int openDeadlines, int openTasks, java.math.BigDecimal unbilledTime, java.math.BigDecimal unbilledCosts,
                              java.math.BigDecimal outstanding, java.math.BigDecimal trustHeld, boolean trustEnabled, String clientEmail, boolean mailReady, boolean feeTermsSet,
                              boolean canWork) {
    }
}
