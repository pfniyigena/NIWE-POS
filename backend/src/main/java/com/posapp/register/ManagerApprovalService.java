package com.posapp.register;

import com.posapp.common.ApiException;
import com.posapp.user.AccessArea;
import com.posapp.user.AppUser;
import com.posapp.user.Permission;
import com.posapp.user.Role;
import com.posapp.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** A manager standing at the till and typing their PIN to approve something the cashier may not do alone. */
@Service
public class ManagerApprovalService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ManagerApprovalService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** An administrator, anyone who manages Sales, or anyone holding the discount-override permission. */
    public static boolean isManager(AppUser u) {
        return u != null && u.isActive() && (u.getRole() == Role.ADMIN || u.canManage(AccessArea.SALES) || u.getPermissions().contains(Permission.OVERRIDE_DISCOUNT_CAP));
    }

    /** What a person is being asked to approve at the till. */
    public enum Kind {
        DISCOUNT(null), VOID_RETURN(Permission.APPROVE_POS_VOID_RETURN), PRICE(Permission.APPROVE_POS_PRICE), CASH(Permission.APPROVE_POS_CASH);
        final Permission permission;
        Kind(Permission p) { this.permission = p; }
    }

    /** A manager (see {@link #isManager}) can approve anything; others only the one thing their own permission names. */
    public static boolean canApprove(AppUser u, Kind kind) {
        if (isManager(u)) return true;
        return u != null && u.isActive() && kind.permission != null && u.getPermissions().contains(kind.permission);
    }

    /** The manager who owns this PIN. */
    public AppUser approverByPin(String pin) {
        return approverByPin(pin, Kind.DISCOUNT);
    }

    /** The person who owns this PIN, if they may approve this kind of thing. */
    public AppUser approverByPin(String pin, Kind kind) {
        if (pin == null || pin.isBlank()) throw ApiException.badRequest("A manager's PIN is needed to approve this");
        AppUser found = userRepository.findByActiveTrueAndPinHashIsNotNull().stream()
                .filter(u -> passwordEncoder.matches(pin.trim(), u.getPinHash()))
                .findFirst().orElseThrow(() -> ApiException.badRequest("PIN not recognized"));
        if (!canApprove(found, kind)) throw ApiException.forbidden(found.getFullName() + " cannot approve this; ask a manager");
        return found;
    }
}
