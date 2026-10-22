package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** One line of the inspection checklist. */
@Getter
@Setter
@Entity
public class HkChecklistItem extends BaseEntity {
    private String label;
    private int sortOrder;
    private boolean active = true;
}
