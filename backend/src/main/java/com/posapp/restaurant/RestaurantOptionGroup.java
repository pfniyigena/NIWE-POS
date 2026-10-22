package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** A choice the guest makes for a menu item ("Size", "Cooking", "Extras") with the options they can pick from. */
@Getter
@Setter
@Entity
public class RestaurantOptionGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private MenuItem menuItem;

    private String name;
    /** The guest must pick at least one option from this group. */
    private boolean required = false;
    /** The most options that may be picked (1 = choose one; 0 = no limit). */
    private int maxSelect = 1;
    private int sortOrder = 0;
    private boolean active = true;

    @OrderBy("sortOrder ASC, id ASC")
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    private List<RestaurantOption> options = new ArrayList<>();
}
