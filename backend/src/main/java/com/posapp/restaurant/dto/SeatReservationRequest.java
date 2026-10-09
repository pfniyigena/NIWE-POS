package com.posapp.restaurant.dto;

public class SeatReservationRequest {
    /** Only needed when the reservation wasn't already assigned a table. */
    private Long tableId;
    private Long serverId;

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }
}
