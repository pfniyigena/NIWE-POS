package com.posapp.theme;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A signed-in user's colour theme, kept on the server so it follows them to any device. */
@Getter
@Setter
@Entity
public class UserTheme extends BaseEntity {

    @Column(name = "app_user_id", nullable = false, unique = true)
    private Long appUserId;

    @Column(nullable = false)
    private String theme;

    private String customBg;
    private String customAccent;
}
