package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/** A delivery window of the day (e.g. 08:00-11:00) that customers can choose, with how many orders it takes and on which weekdays it exists. */
@Getter
@Setter
@Entity
public class OnlineDeliverySlot extends BaseEntity {
    private LocalTime startTime;
    private LocalTime endTime;
    /** Orders one window takes on one day; 0 = no limit. */
    private int maxOrders = 0;
    /** Seven characters, Monday first: 1 = the window exists that day. */
    private String days = "1111111";
    private boolean active = true;
    private int sortOrder = 0;

    public boolean on(java.time.DayOfWeek d) {
        String s = days == null ? "1111111" : days;
        int i = d.getValue() - 1;
        return i < s.length() && s.charAt(i) == '1';
    }
}
