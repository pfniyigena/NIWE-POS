package com.posapp.restaurant.dto;

public class ReservationReminderRequest {
    /** True = e-mail it now; false = only produce the text. */
    private boolean send;

    public boolean isSend() { return send; }
    public void setSend(boolean send) { this.send = send; }
}
