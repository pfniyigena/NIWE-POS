package com.posapp.restaurant;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Who is signed in, and whether they may approve the things a waiter may not do alone (big discounts, waiving the service charge, voids). */
public final class RestaurantAccess {

    private RestaurantAccess() {
    }

    /** An administrator, anyone with Operations manage rights, or anyone holding the discount-override permission. */
    public static boolean isApprover() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return false;
        for (GrantedAuthority g : a.getAuthorities()) {
            String n = g.getAuthority();
            if (n.equals("ROLE_ADMIN") || n.equals("AREA_OPERATIONS_MANAGE") || n.equals("PERM_OVERRIDE_DISCOUNT_CAP")) return true;
        }
        return false;
    }

    public static String username() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null || a.getName() == null ? "system" : a.getName();
    }
}
