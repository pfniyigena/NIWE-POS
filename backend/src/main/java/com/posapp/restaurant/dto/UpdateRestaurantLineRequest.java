package com.posapp.restaurant.dto;


import java.math.BigDecimal;public class UpdateRestaurantLineRequest {
    private BigDecimal quantity;
    private String notes;
    /** A discount on this line, in percent (0 removes it). Anything above the restaurant's limit needs a manager. */
    private java.math.BigDecimal discountPercent;
    private String discountReason;
    /** Change the options (only before the line is sent); null leaves them as they are, an empty list clears them. */
    private java.util.List<Long> optionIds;
    /** Move the line to another course (only before it is sent). */
    private Integer course;
    /** Seat number (0 clears it). */
    private Integer seat;

    public Integer getSeat() { return seat; }
    public void setSeat(Integer seat) { this.seat = seat; }

    public java.util.List<Long> getOptionIds() { return optionIds; }
    public void setOptionIds(java.util.List<Long> optionIds) { this.optionIds = optionIds; }
    public Integer getCourse() { return course; }
    public void setCourse(Integer course) { this.course = course; }

    public java.math.BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(java.math.BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    public String getDiscountReason() { return discountReason; }
    public void setDiscountReason(String discountReason) { this.discountReason = discountReason; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
