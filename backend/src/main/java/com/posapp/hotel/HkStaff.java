package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A housekeeper. Not necessarily a system user. */
@Getter
@Setter
@Entity
public class HkStaff extends BaseEntity {
    private String name;
    private boolean active = true;
}
