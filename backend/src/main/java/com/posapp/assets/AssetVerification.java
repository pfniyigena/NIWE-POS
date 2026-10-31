package com.posapp.assets;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A physical check of the assets in one place: every asset in scope gets a line to mark found, missing, damaged. */
@Getter
@Setter
@Entity
public class AssetVerification extends BaseEntity {
    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    @Column(nullable = false)
    private String name;
    @Column(nullable = false, length = 20)
    private String status = OPEN;
    @Column(name = "branch_id")
    private Long branchId;
    @Column(name = "category_id")
    private Long categoryId;
    private String location;
    @Column(nullable = false)
    private LocalDate startedOn;
    private LocalDate closedOn;
    @Column(length = 1000)
    private String note;
    private String createdByName;
    private String closedByName;
}
