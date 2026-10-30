package com.posapp.helpdesk;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/** One line of a ticket's history. Never edited or deleted. */
@Getter
@Setter
@Entity
public class HelpDeskEvent extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private HelpDeskTicket ticket;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HelpDeskEventKind kind;
    @Column(columnDefinition = "TEXT")
    private String detail;
    private String createdByName;
}
