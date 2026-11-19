package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A person who delivers the shop's orders. Not an ERP user or employee: a simple list kept in Shop settings. */
@Getter
@Setter
@Entity
public class OnlineDriver extends BaseEntity {
    private String name;
    private String phone;
    private String notes;
    private boolean active = true;
}
