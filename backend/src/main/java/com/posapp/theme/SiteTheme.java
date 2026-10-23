package com.posapp.theme;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** The theme of the public website and the sign-in page (one row). It is what visitors who are not signed in see. */
@Getter
@Setter
@Entity
public class SiteTheme extends BaseEntity {

    @Column(nullable = false)
    private String theme;

    private String customBg;
    private String customAccent;
}
