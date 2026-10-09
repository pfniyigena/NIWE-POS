package com.posapp.apps;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import lombok.Getter;
import lombok.Setter;

/**
 * One store's install/enable state for one built-in app (see {@link AppKey}/{@link AppCatalog}) —
 * Task: "plugin (or apps) to be installed and configured". A row only exists once an app has been
 * installed at least once; there's no row at all for an app nobody has touched, which is how
 * {@code AppsService.list()} tells "never installed" apart from "installed, currently disabled".
 *
 * <p>{@code installed} and {@code enabled} are deliberately separate: installing an app (a
 * one-time "start using this" action) defaults it to disabled, since a half-configured
 * integration (no settings filled in yet) should never silently start acting on real sales —
 * enabling it is a distinct, explicit second step, taken once its settings actually make sense.
 *
 * <p>Used to also carry a generic {@code List<InstalledAppSetting>} of free-form key/value rows,
 * for whatever a future app needed before its real fields were known — Task: "Then can we remove
 * this? ... Frontend + backend, fully." Removed: EFRIS (the only app in the catalog) got its own
 * structured, per-terminal settings ({@code EfrisSetting}) once its actual requirements were known,
 * nothing ever read the generic rows, and the admin UI's own warning said as much ("nothing here is
 * validated or used by any live integration yet"). A future app that genuinely needs an
 * unstructured settings bag can reintroduce a version of it scoped to what it actually needs.
 */
@Getter
@Setter
@Entity
public class InstalledApp extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(unique = true, nullable = false)
    private AppKey appKey;

    private boolean installed = true;
    private boolean enabled = false;
}
