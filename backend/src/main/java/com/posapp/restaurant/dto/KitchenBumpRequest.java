package com.posapp.restaurant.dto;

public class KitchenBumpRequest {
    /** The station to clear; blank clears every station on the order. "Unassigned" means dishes with no station. */
    private String station;

    public String getStation() { return station; }
    public void setStation(String station) { this.station = station; }
}
