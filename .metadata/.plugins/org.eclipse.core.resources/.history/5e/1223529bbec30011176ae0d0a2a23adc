package com.posapp.branch;

import com.posapp.common.ApiException;
import com.posapp.security.AppUserPrincipal;
import com.posapp.user.AppUser;
import com.posapp.user.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Fourier: staff restricted to their own branch's data, with admin override. The rule, in one
 * place: an ADMIN is never restricted; a non-admin with <b>no</b> branches assigned is not
 * restricted either (so existing accounts and single-branch shops are unaffected after upgrade);
 * a non-admin with one or more branches assigned can only work with — and approve — records of
 * those branches. A record with no branch (older than branches) belongs to head office.
 *
 * <p>Depends only on {@link BranchService}, so any module may use it without a bean cycle.
 */
@Service
public class BranchAccessService {

    private final BranchService branchService;

    public BranchAccessService(BranchService branchService) {
        this.branchService = branchService;
    }

    /** Branch ids the signed-in user is limited to, or null when they are unrestricted (admin, nobody signed in, or no branches assigned). */
    public Set<Long> allowedBranchIds() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            return null;
        }
        return allowedBranchIds(principal.getUser());
    }

    /** Same rule for an arbitrary user (e.g. the cashier who is joining a shift by PIN). */
    public Set<Long> allowedBranchIds(AppUser user) {
        if (user == null || user.getRole() == Role.ADMIN || user.getBranchIds() == null || user.getBranchIds().isEmpty()) {
            return null;
        }
        return user.getBranchIds();
    }

    public boolean isRestricted() {
        return allowedBranchIds() != null;
    }

    /** True if the signed-in user may work with a record of this branch (null = head office). */
    @Transactional(readOnly = true)
    public boolean canAccess(Long branchId) {
        return canAccess(allowedBranchIds(), branchId);
    }

    /** True if {@code user} may work with a record of this branch (null = head office). */
    @Transactional(readOnly = true)
    public boolean canAccess(AppUser user, Long branchId) {
        return canAccess(allowedBranchIds(user), branchId);
    }

    private boolean canAccess(Set<Long> allowed, Long branchId) {
        if (allowed == null) {
            return true;
        }
        Long effective = branchId != null ? branchId : branchService.headquartersId();
        return allowed.contains(effective);
    }

    /** Throws 403 when the signed-in user may not work with this branch's records. */
    @Transactional(readOnly = true)
    public void assertCanAccess(Long branchId) {
        if (!canAccess(branchId)) {
            throw ApiException.forbidden("You don't have access to this branch's records");
        }
    }
}
