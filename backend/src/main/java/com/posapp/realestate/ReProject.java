package com.posapp.realestate;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A development the company sells units in (an estate of plots, an apartment block...). */
@Getter
@Setter
@Entity
@Table(name = "re_project")
public class ReProject extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 120)
    private String area;

    @Column(length = 255)
    private String address;

    @Column(length = 200)
    private String developerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.PLANNING;

    private LocalDate launchDate;
    private LocalDate handoverDate;

    @Column(length = 2000)
    private String description;

    private String createdByName;
}
