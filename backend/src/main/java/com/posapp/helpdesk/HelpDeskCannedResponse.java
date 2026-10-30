package com.posapp.helpdesk;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A saved reply. {{requester}}, {{ticket}}, {{subject}} and {{agent}} are filled in when it is used. */
@Getter
@Setter
@Entity
public class HelpDeskCannedResponse extends BaseEntity {
    @Column(nullable = false, length = 120, unique = true)
    private String title;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;
    /** When set, the reply is only offered on tickets of this category (null = every ticket). */
    private Long categoryId;
    @Column(nullable = false)
    private boolean active = true;
}
