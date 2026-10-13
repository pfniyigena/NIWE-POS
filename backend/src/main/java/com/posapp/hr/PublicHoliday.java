package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
public class PublicHoliday extends BaseEntity {

    @Column(nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(nullable = false)
    private String name;
}
