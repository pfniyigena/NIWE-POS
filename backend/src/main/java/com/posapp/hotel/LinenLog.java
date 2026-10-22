package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A movement of linen with the laundry: how many pieces of an item went out, came back, or were found damaged. */
@Getter
@Setter
@Entity
public class LinenLog extends BaseEntity {
    private LocalDate logDate;
    private String item;
    private int sent;
    private int returned;
    private int damaged;
    private String notes;
}
