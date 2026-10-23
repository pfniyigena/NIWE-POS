package com.posapp.inventory.dto;


import java.math.BigDecimal;import java.util.List;

/** Counted quantities entered on the sheet; a null countedQuantity clears a line back to "not counted". */
public class StockCountSaveRequest {

    private List<Entry> lines;

    public List<Entry> getLines() { return lines; }
    public void setLines(List<Entry> lines) { this.lines = lines; }

    public static class Entry {
        private Long lineId;
        private BigDecimal countedQuantity;

        public Long getLineId() { return lineId; }
        public void setLineId(Long lineId) { this.lineId = lineId; }
        public BigDecimal getCountedQuantity() { return countedQuantity; }
        public void setCountedQuantity(BigDecimal countedQuantity) { this.countedQuantity = countedQuantity; }
    }
}
