package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "employee_checklist_item")
public class EmployeeChecklistItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private EmployeeChecklist checklist;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 60)
    private String responsible;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean done;

    @Column(name = "done_at")
    private Instant doneAt;

    @Column(name = "done_by_name", length = 120)
    private String doneByName;

    @Column(length = 300)
    private String note;
}
