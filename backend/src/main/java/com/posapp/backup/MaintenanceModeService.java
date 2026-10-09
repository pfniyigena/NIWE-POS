package com.posapp.backup;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fourier: "true maintenance-mode lockout on top of this" — a single shared on/off flag for the
 * one moment this app needs to stop everyone else cold: a restore in progress, which drops and
 * recreates tables that every other request (reads included — see MaintenanceModeFilter) could
 * otherwise hit mid-rebuild. {@link BackupService} is the only thing that flips this; nothing else
 * in the app should call {@link #activate()}/{@link #deactivate()} directly.
 * <p>
 * Deliberately NOT reused for plain "Backup now" — pg_dump only reads the database, so a backup
 * running in the background is safe for everyone else to keep working through (Fourier's own
 * answer when asked: "No — only restore locks the app").
 */
@Service
public class MaintenanceModeService {

    private final AtomicBoolean active = new AtomicBoolean(false);
    private volatile Instant since;

    /**
     * Turns maintenance mode on, unless it's already on.
     *
     * @return true if this call was the one that turned it on; false if a restore was already in
     *         progress (the caller should treat that as "can't start a second one").
     */
    public boolean activate() {
        boolean turnedOn = active.compareAndSet(false, true);
        if (turnedOn) {
            since = Instant.now();
        }
        return turnedOn;
    }

    /** Always safe to call, including after a failed restore — leaves the flag off either way. */
    public void deactivate() {
        active.set(false);
        since = null;
    }

    public boolean isActive() {
        return active.get();
    }

    /** When the current maintenance window started, or null if it isn't active. */
    public Instant since() {
        return since;
    }
}
