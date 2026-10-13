package com.posapp.hotel;

/** What a line on a guest's bill is for. RESTAURANT / POS / ROOM_SERVICE lines are posted by those modules when "Charge to room" is used. */
public enum ChargeType {
    ROOM, RESTAURANT, POS, ROOM_SERVICE, EXTRA
}
