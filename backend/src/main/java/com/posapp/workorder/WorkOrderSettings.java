package com.posapp.workorder;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** Work Orders settings (one row). */
@Getter
@Setter
@Entity
public class WorkOrderSettings extends BaseEntity {
    /** New customer jobs start with "needs the customer's approval of the estimate" switched on. */
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean requireApprovalDefault = false;
    /** E-mail the customer the completion note (PDF) when a customer job is completed, if the customer has an e-mail address and e-mail is set up. */
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean emailCustomerOnComplete = false;
}
