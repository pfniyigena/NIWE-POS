package com.posapp.register.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/** Closing a shift: the cash counted (typed in, or worked out from the notes and coins counted), and a note when the count is off. */
public class CloseSessionRequest {
    @PositiveOrZero(message = "Counted cash cannot be negative")
    private BigDecimal countedCash;
    /** Notes and coins counted; when given, the total of these is the counted cash. */
    private List<Denomination> denominations;
    private String varianceNote;

    public BigDecimal getCountedCash() { return countedCash; }
    public void setCountedCash(BigDecimal countedCash) { this.countedCash = countedCash; }
    public List<Denomination> getDenominations() { return denominations; }
    public void setDenominations(List<Denomination> denominations) { this.denominations = denominations; }
    public String getVarianceNote() { return varianceNote; }
    public void setVarianceNote(String varianceNote) { this.varianceNote = varianceNote; }

    public static class Denomination {
        private BigDecimal value;
        private Integer count;

        public BigDecimal getValue() { return value; }
        public void setValue(BigDecimal value) { this.value = value; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
    }
}
