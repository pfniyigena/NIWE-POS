package com.posapp.law;

import com.posapp.common.ApiException;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.user.AppUser;
import org.springframework.stereotype.Component;

/**
 * Who may do what on a matter. Admins can do everything. A matter's team can work it; its lead can also change it. Operations
 * viewers and managers cover every matter that is not restricted. A restricted matter is invisible to everyone outside its team
 * (except admins), and a matter someone may not see answers "not found".
 */
@Component
public class LawAccess {

    public record Actor(AppUser user, boolean admin, boolean viewer, boolean manager) {
    }

    private final LawMatterRepository matterRepository;
    private final LawMatterTeamRepository teamRepository;
    private final ModuleService moduleService;

    public LawAccess(LawMatterRepository matterRepository, LawMatterTeamRepository teamRepository, ModuleService moduleService) {
        this.matterRepository = matterRepository;
        this.teamRepository = teamRepository;
        this.moduleService = moduleService;
    }

    public void on() {
        moduleService.requireEnabled(ModuleKey.LAW_FIRM);
    }

    public static String name(AppUser u) {
        return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
    }

    public boolean isTeam(LawMatter m, Actor a) {
        return teamRepository.existsByMatterIdAndUserId(m.getId(), a.user().getId());
    }

    /** True when the person is on the team of at least one matter. */
    public boolean isLawyer(Actor a) {
        return teamRepository.existsByUserId(a.user().getId());
    }

    public boolean canView(LawMatter m, Actor a) {
        return a.admin() || isTeam(m, a) || (a.viewer() && !m.isRestricted());
    }

    public boolean canWork(LawMatter m, Actor a) {
        return a.admin() || isTeam(m, a) || (a.manager() && !m.isRestricted());
    }

    public boolean canLead(LawMatter m, Actor a) {
        return a.admin() || teamRepository.existsByMatterIdAndUserIdAndRole(m.getId(), a.user().getId(), TeamRole.LEAD) || (a.manager() && !m.isRestricted());
    }

    public LawMatter find(Long id) {
        return matterRepository.findById(id).orElseThrow(() -> ApiException.notFound("Matter not found."));
    }

    public LawMatter viewable(Actor a, Long id) {
        on();
        LawMatter m = find(id);
        if (!canView(m, a)) throw ApiException.notFound("Matter not found.");
        return m;
    }

    public LawMatter workable(Actor a, Long id) {
        LawMatter m = viewable(a, id);
        if (!canWork(m, a)) throw ApiException.forbidden("You are not on this matter's team.");
        return m;
    }

    public LawMatter leadable(Actor a, Long id) {
        LawMatter m = viewable(a, id);
        if (!canLead(m, a)) throw ApiException.forbidden("Only the lead lawyer, a manager or an admin can do that.");
        return m;
    }
}
