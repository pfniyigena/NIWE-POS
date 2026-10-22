package com.posapp.restaurant.dto;

import java.util.List;

/** Which items to take from an order: some lines (optionally only part of the quantity) and/or everything ordered for certain seats. */
public class CheckLineSelection {
    private List<Item> lines;
    private List<Integer> seats;
    /** Transfer only: the open order that receives the items. */
    private Long targetOrderId;

    public static class Item {
        private Long lineId;
        private Integer quantity;

        public Long getLineId() { return lineId; }
        public void setLineId(Long lineId) { this.lineId = lineId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    public List<Item> getLines() { return lines; }
    public void setLines(List<Item> lines) { this.lines = lines; }
    public List<Integer> getSeats() { return seats; }
    public void setSeats(List<Integer> seats) { this.seats = seats; }
    public Long getTargetOrderId() { return targetOrderId; }
    public void setTargetOrderId(Long targetOrderId) { this.targetOrderId = targetOrderId; }
}
