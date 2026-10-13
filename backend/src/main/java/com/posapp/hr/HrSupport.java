package com.posapp.hr;

import com.posapp.branch.BranchService;
import com.posapp.branch.dto.BranchDto;
import com.posapp.security.AppUserPrincipal;
import com.posapp.user.AppUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/** Small helpers shared by the HR services. */
@Component
public class HrSupport {

    private final BranchService branchService;

    public HrSupport(BranchService branchService) {
        this.branchService = branchService;
    }

    /** The signed-in user, or null. */
    public static AppUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            return null;
        }
        return principal.getUser();
    }

    public static String currentUserName() {
        AppUser user = currentUser();
        if (user == null) {
            return "system";
        }
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
    }

    public Map<Long, String> branchNames() {
        Map<Long, String> names = new HashMap<>();
        for (BranchDto b : branchService.listAll()) {
            names.put(b.id(), b.name());
        }
        return names;
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
