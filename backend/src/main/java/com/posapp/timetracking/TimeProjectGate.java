package com.posapp.timetracking;

import com.posapp.user.AppUser;

/**
 * Lets the module that owns a project (a law-firm matter, say) keep it away from people who must not see it. The time module asks every
 * gate before it lists a project, accepts time on it, or shows or decides its entries; a project nobody objects to is open to all.
 */
public interface TimeProjectGate {

    /** False when {@code user} must not see, log time on or approve time of this project. */
    boolean allows(TimeProject project, AppUser user);
}
