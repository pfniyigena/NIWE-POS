package com.posapp.user;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * An admin-defined role such as "Manager" or "Accountant": a named set of {@link AccessArea} grants. A user keeps
 * their base {@link Role} (ADMIN or CASHIER) and may additionally be given one custom role, whose grants are
 * added on top — nothing here ever removes what the base role allows. An ADMIN ignores it (already unrestricted).
 */
@Getter
@Setter
@Entity
@Table(name = "custom_role")
public class CustomRole extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** An inactive role grants nothing to the users assigned to it. */
    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean active = true;

    /** Grant codes like {@code INVENTORY_MANAGE} — see {@link AccessGrants}. Eager: read on every request that builds a principal. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "custom_role_grants", joinColumns = @JoinColumn(name = "custom_role_id"))
    @Column(name = "grant_code")
    private Set<String> grants = new HashSet<>();
}
