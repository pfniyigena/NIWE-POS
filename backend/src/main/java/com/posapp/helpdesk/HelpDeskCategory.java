package com.posapp.helpdesk;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/**
 * Fourier: asked (via AskUserQuestion) for "Configurable categories/departments ... that you can
 * manage" to route Help Desk tickets — same simple, admin-managed lookup-list shape as {@code
 * com.posapp.catalog.Category}/{@code Brand} (name + active flag, no color/hierarchy), minus the
 * terminal-tile color since tickets aren't shown as tiles anywhere. Toggled inactive rather than
 * deleted once in use — see {@code HelpDeskCategoryService.delete}'s own in-use guard, same
 * "soft-retire, don't orphan history" convention as every other reference lookup in this app.
 */
@Getter
@Setter
@Entity
public class HelpDeskCategory extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private boolean active = true;
}
