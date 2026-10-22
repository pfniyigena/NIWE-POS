package com.posapp.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Everything the QR menu, the ordering page, the order tracker and the staff queue exchange. */
public final class OnlineOrderingDtos {

    private OnlineOrderingDtos() {
    }

    // ---------------------------------------------------------------- what the guest sees

    public record PublicOption(Long id, String name, BigDecimal priceDelta) {
    }

    public record PublicOptionGroup(Long id, String name, boolean required, int maxSelect, List<PublicOption> options) {
    }

    public record PublicItem(Long id, String name, BigDecimal price, String allergens, boolean available, String unavailableReason, List<PublicOptionGroup> groups) {
    }

    public record PublicCategory(Long id, String name, List<PublicItem> items) {
    }

    public record PublicZone(Long id, String name, BigDecimal fee, BigDecimal minOrder, int extraMinutes) {
    }

    /**
     * The menu page's one request: the restaurant's name and hours, what can be ordered here (at the table, takeaway, delivery), the menu and the delivery areas.
     * {@code tableName} is set when the QR code belongs to a table.
     */
    public record PublicMenu(String restaurantName, String message, boolean paused, boolean openNow, String openTime, String closeTime, String mode,
                             String tableName, boolean tableOrdering, boolean takeaway, boolean delivery, BigDecimal minOrder, int prepMinutes,
                             List<PublicCategory> categories, List<PublicZone> zones) {
    }

    public static class PublicLine {
        private Long menuItemId;
        private int quantity = 1;
        @Size(max = 200, message = "A note can be at most 200 characters")
        private String notes;
        private List<Long> optionIds;

        public Long getMenuItemId() {
            return menuItemId;
        }

        public void setMenuItemId(Long menuItemId) {
            this.menuItemId = menuItemId;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        public List<Long> getOptionIds() {
            return optionIds;
        }

        public void setOptionIds(List<Long> optionIds) {
            this.optionIds = optionIds;
        }
    }

    public static class PublicOrderRequest {
        /** TABLE, TAKEAWAY or DELIVERY. */
        @NotBlank(message = "Choose how you want to order")
        private String mode;
        private String tableToken;
        @Size(max = 80, message = "The name is too long")
        private String customerName;
        @Size(max = 40, message = "The phone number is too long")
        private String customerPhone;
        @Size(max = 160, message = "The e-mail address is too long")
        private String customerEmail;
        @Size(max = 240, message = "The address is too long")
        private String customerAddress;
        private Long zoneId;
        @Size(max = 300, message = "The note is too long")
        private String notes;
        private List<PublicLine> lines;
        /** A field real people never fill in; bots do. */
        private String website;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getTableToken() {
            return tableToken;
        }

        public void setTableToken(String tableToken) {
            this.tableToken = tableToken;
        }

        public String getCustomerName() {
            return customerName;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }

        public String getCustomerPhone() {
            return customerPhone;
        }

        public void setCustomerPhone(String customerPhone) {
            this.customerPhone = customerPhone;
        }

        public String getCustomerEmail() {
            return customerEmail;
        }

        public void setCustomerEmail(String customerEmail) {
            this.customerEmail = customerEmail;
        }

        public String getCustomerAddress() {
            return customerAddress;
        }

        public void setCustomerAddress(String customerAddress) {
            this.customerAddress = customerAddress;
        }

        public Long getZoneId() {
            return zoneId;
        }

        public void setZoneId(Long zoneId) {
            this.zoneId = zoneId;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        public List<PublicLine> getLines() {
            return lines;
        }

        public void setLines(List<PublicLine> lines) {
            this.lines = lines;
        }

        public String getWebsite() {
            return website;
        }

        public void setWebsite(String website) {
            this.website = website;
        }
    }

    public record TrackLine(String name, int quantity, String options, String notes, BigDecimal lineTotal) {
    }

    /**
     * What the guest sees while waiting. {@code stage} is WAITING (not answered yet), ACCEPTED, PREPARING, READY, ON_THE_WAY, SERVED, COMPLETED, REJECTED, CANCELLED or EXPIRED;
     * {@code stageText} says it in words. The total is an estimate until the bill is paid.
     */
    public record Track(String code, String mode, String status, String stage, String stageText, Integer etaMinutes, String firstName, String tableName,
                        String zoneName, BigDecimal deliveryFee, BigDecimal subtotal, BigDecimal total, String rejectReason, Instant placedAt,
                        List<TrackLine> lines, String restaurantName) {
    }

    // ---------------------------------------------------------------- what staff see

    public record OnlineOrderDto(Long id, String code, String mode, String status, String source, Long tableId, String tableName, String customerName,
                                 String customerPhone, String customerEmail, String customerAddress, String zoneName, BigDecimal deliveryFee, String notes,
                                 BigDecimal estimatedTotal, Integer etaMinutes, Instant createdAt, Instant decidedAt, String decidedBy, String rejectReason,
                                 Long restaurantOrderId, int waitingMinutes, List<TrackLine> lines, String problem) {
    }

    public static class AcceptRequest {
        private Integer etaMinutes;
        private Long serverId;
        /** Leave out dishes that have sold out since the guest ordered instead of refusing the whole order. */
        private boolean skipUnavailable;

        public Integer getEtaMinutes() {
            return etaMinutes;
        }

        public void setEtaMinutes(Integer etaMinutes) {
            this.etaMinutes = etaMinutes;
        }

        public Long getServerId() {
            return serverId;
        }

        public void setServerId(Long serverId) {
            this.serverId = serverId;
        }

        public boolean isSkipUnavailable() {
            return skipUnavailable;
        }

        public void setSkipUnavailable(boolean skipUnavailable) {
            this.skipUnavailable = skipUnavailable;
        }
    }

    public static class RejectRequest {
        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public record TableQr(Long tableId, String name, String section, boolean active, String token, String path) {
    }

    public record ZoneDto(Long id, String name, BigDecimal fee, BigDecimal minOrder, int extraMinutes, boolean active, int sortOrder) {
    }

    public static class ZoneRequest {
        private String name;
        private BigDecimal fee;
        private BigDecimal minOrder;
        private Integer extraMinutes;
        private Boolean active;
        private Integer sortOrder;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public BigDecimal getFee() {
            return fee;
        }

        public void setFee(BigDecimal fee) {
            this.fee = fee;
        }

        public BigDecimal getMinOrder() {
            return minOrder;
        }

        public void setMinOrder(BigDecimal minOrder) {
            this.minOrder = minOrder;
        }

        public Integer getExtraMinutes() {
            return extraMinutes;
        }

        public void setExtraMinutes(Integer extraMinutes) {
            this.extraMinutes = extraMinutes;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
}
