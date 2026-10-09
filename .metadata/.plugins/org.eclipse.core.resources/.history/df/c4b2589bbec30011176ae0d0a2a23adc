package com.posapp.inventory.dto;

import java.util.List;

/** Counted quantities entered on the sheet; a null countedQuantity clears a line back to "not counted". */
public class StockCountSaveRequest {

    private List<Entry> lines;

    public List<Entry> getLines() { return lines; }
    public void setLines(List<Entry> lines) { this.lines = lines; }

    public static class Entry {
        private Long lineId;
        private Integer countedQuantity;

        public Long getLineId() { return lineId; }
        public void setLineId(Long lineId) { this.lineId = lineId; }
        public Integer getCountedQuantity() { return countedQuantity; }
        public void setCountedQuantity(Integer countedQuantity) { this.countedQuantity = countedQuantity; }
    }
}
