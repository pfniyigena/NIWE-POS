package com.posapp.register.dto;

import java.math.BigDecimal;
import java.util.List;

/** Cash paid in, paid out or dropped into the safe, or (kind COUNT) a count made in the middle of the shift. */
public class CashMovementRequest {
    private String kind;
    private String reasonCode;
    private BigDecimal amount;
    private String note;
    /** A manager's PIN, when a paid-out is above the limit and the cashier is not a manager. */
    private String approverPin;
    private List<CloseSessionRequest.Denomination> denominations;

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getApproverPin() { return approverPin; }
    public void setApproverPin(String approverPin) { this.approverPin = approverPin; }
    public List<CloseSessionRequest.Denomination> getDenominations() { return denominations; }
    public void setDenominations(List<CloseSessionRequest.Denomination> denominations) { this.denominations = denominations; }
}
