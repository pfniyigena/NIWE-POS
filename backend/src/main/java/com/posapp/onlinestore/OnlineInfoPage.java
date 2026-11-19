package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A page of information about the shop (terms, privacy, returns, delivery). A null body means the starter text is shown. */
@Getter
@Setter
@Entity
public class OnlineInfoPage extends BaseEntity {
    private String slug;
    private String title;
    @Column(columnDefinition = "text")
    private String body;
    private boolean published = true;
    private String updatedBy;
}
