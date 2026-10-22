package com.posapp.collections;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** How the reminder run behaves. One row; the daily run stays off until an admin switches it on. */
@Entity
@Table(name = "collection_settings")
public class CollectionSettings extends BaseEntity {

    @Column(name = "auto_enabled", nullable = false)
    private boolean autoEnabled = false;

    /** Remind once the oldest unpaid item is this many days past due. */
    @Column(name = "first_days", nullable = false)
    private int firstDays = 1;

    /** Do not remind the same customer again within this many days. */
    @Column(name = "repeat_days", nullable = false)
    private int repeatDays = 7;

    /** Stop after this many reminders for the same debt. */
    @Column(name = "max_reminders", nullable = false)
    private int maxReminders = 3;

    /** Payment term (days) for an item with no due date whose customer has no term of their own. */
    @Column(name = "default_term_days", nullable = false)
    private int defaultTermDays = 30;

    public boolean isAutoEnabled() { return this.autoEnabled; }
    public void setAutoEnabled(boolean autoEnabled) { this.autoEnabled = autoEnabled; }
    public int getFirstDays() { return this.firstDays; }
    public void setFirstDays(int firstDays) { this.firstDays = firstDays; }
    public int getRepeatDays() { return this.repeatDays; }
    public void setRepeatDays(int repeatDays) { this.repeatDays = repeatDays; }
    public int getMaxReminders() { return this.maxReminders; }
    public void setMaxReminders(int maxReminders) { this.maxReminders = maxReminders; }
    public int getDefaultTermDays() { return this.defaultTermDays; }
    public void setDefaultTermDays(int defaultTermDays) { this.defaultTermDays = defaultTermDays; }
}
