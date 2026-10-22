package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A checklist started for one employee: its own copy of the tasks, so changing the template later does not change it. */
@Getter
@Setter
@Entity
@Table(name = "employee_checklist")
public class EmployeeChecklist extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "template_id")
    private Long templateId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ChecklistKind kind = ChecklistKind.ONBOARDING;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    private String createdByName;

    @OneToMany(mappedBy = "checklist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, id asc")
    private List<EmployeeChecklistItem> items = new ArrayList<>();
}
