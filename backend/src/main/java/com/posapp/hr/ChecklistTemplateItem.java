package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "checklist_template_item")
public class ChecklistTemplateItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ChecklistTemplate template;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 200)
    private String title;

    /** Who normally does it: HR, Manager, IT, Payroll, Employee... free text. */
    @Column(length = 60)
    private String responsible;

    /** Days after (or before, when negative) the start date of the checklist. */
    @Column(name = "due_offset_days", nullable = false)
    private int dueOffsetDays;
}
