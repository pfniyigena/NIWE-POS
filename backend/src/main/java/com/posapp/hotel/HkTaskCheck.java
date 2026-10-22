package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/** The result of one checklist line at an inspection. Replaced at each inspection. */
@Getter
@Setter
@Entity
public class HkTaskCheck extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private HkTask task;

    private String label;
    private boolean passed;
}
