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

import java.util.ArrayList;
import java.util.List;

/** A reusable list of tasks for a new starter or a leaver. Starting it for an employee copies its tasks onto that employee. */
@Getter
@Setter
@Entity
@Table(name = "checklist_template")
public class ChecklistTemplate extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ChecklistKind kind = ChecklistKind.ONBOARDING;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, id asc")
    private List<ChecklistTemplateItem> items = new ArrayList<>();
}
