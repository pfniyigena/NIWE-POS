package com.posapp.modules;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/**
 * An explicit on/off choice for one module. No row means "never touched" and the module's default applies. The Hotel
 * module is the exception: its switch lives in {@code HotelSettings.enabled} so Hotel > Settings and Settings > Modules
 * can never disagree (see ModuleService).
 */
@Getter
@Setter
@Entity
public class ModuleSetting extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(unique = true, nullable = false)
    private ModuleKey moduleKey;

    private boolean enabled = true;
}
