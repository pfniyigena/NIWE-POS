package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A date the shop does not deliver (a holiday, stock-taking): no window can be chosen that day. */
@Getter
@Setter
@Entity
public class OnlineClosedDay extends BaseEntity {
    @Column(name = "day")
    private LocalDate day;
    private String reason;
}
