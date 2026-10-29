package com.posapp.logging;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** v1.14.0: one row per sign-in attempt, successful or not (who, when, from which address and device). */
@Getter
@Setter
@Entity
@Table(name = "login_event")
public class LoginEvent extends BaseEntity {
    @Column(length = 100)
    private String username;
    private Long userId;
    @Column(nullable = false)
    private boolean success;
    @Column(length = 80)
    private String reason;
    @Column(length = 64)
    private String ip;
    @Column(length = 64)
    private String deviceId;
    @Column(length = 255)
    private String userAgent;
}
