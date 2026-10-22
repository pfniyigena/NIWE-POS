package com.posapp.law;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.timetracking.ProjectStatus;
import com.posapp.timetracking.TimeProject;
import com.posapp.timetracking.TimeProjectGate;
import com.posapp.timetracking.TimeTrackingService;
import com.posapp.user.AppUser;
import com.posapp.user.Role;
import org.springframework.stereotype.Component;

/**
 * The tie between a matter and its Time Tracking project (the same approach the work orders use): lawyers log hours in My time on the
 * project, and the matter bills them. A restricted matter's project carries no title or client, and only the matter's team (and admins)
 * can pick it, log on it or see its entries.
 */
@Component
public class LawTimeLink implements TimeProjectGate {

    public static final String SOURCE = "MATTER";

    private final TimeTrackingService timeService;
    private final LawMatterRepository matterRepository;
    private final LawMatterTeamRepository teamRepository;
    private final ModuleService moduleService;

    public LawTimeLink(TimeTrackingService timeService, LawMatterRepository matterRepository, LawMatterTeamRepository teamRepository, ModuleService moduleService) {
        this.moduleService = moduleService;
        this.timeService = timeService;
        this.matterRepository = matterRepository;
        this.teamRepository = teamRepository;
    }

    private static String projectName(LawMatter m) {
        return m.isRestricted() ? m.getNumber() + " — (restricted)" : m.getNumber() + " — " + m.getTitle() + " (" + m.getClientName() + ")";
    }

    /** Only hourly and retainer matters bill their time; on the other kinds hours are tracked but not billable. */
    static boolean timeBillable(LawMatter m) {
        return m.getBillingType() == BillingType.HOURLY || m.getBillingType() == BillingType.RETAINER;
    }

    /** Matters opened before billing existed get their time project when the application starts. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void backfill() {
        try {
            if (!moduleService.isEnabled(ModuleKey.LAW_FIRM)) return;
            matterRepository.findAll().stream().filter(m -> m.getTimeProjectId() == null).forEach(this::sync);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(LawTimeLink.class).warn("Could not create the time projects of existing matters: {}", e.getMessage());
        }
    }

    /** Creates the project on first use, then keeps its name, client, rate, billable flag and open/closed state in step with the matter. */
    public TimeProject sync(LawMatter m) {
        TimeProject p = timeService.ensureProjectForSource(SOURCE, m.getId(), projectName(m), m.isRestricted() ? null : m.getClientCustomerId(), m.getBranchId());
        timeService.configureSourceProject(SOURCE, m.getId(), projectName(m), m.isRestricted() ? null : m.getClientCustomerId(), timeBillable(m), m.getHourlyRate(),
                m.getStatus() == MatterStatus.CLOSED ? ProjectStatus.COMPLETED : ProjectStatus.ACTIVE);
        if (!p.getId().equals(m.getTimeProjectId())) {
            m.setTimeProjectId(p.getId());
            matterRepository.save(m);
        }
        return p;
    }

    /** The matter is gone: so is its (empty) project. */
    public void remove(LawMatter m) {
        if (m.getTimeProjectId() != null) timeService.deleteProject(m.getTimeProjectId());
    }

    @Override
    public boolean allows(TimeProject project, AppUser user) {
        if (!SOURCE.equals(project.getSourceType()) || project.getSourceId() == null) return true;
        LawMatter m = matterRepository.findById(project.getSourceId()).orElse(null);
        if (m == null || !m.isRestricted()) return true;
        return user.getRole() == Role.ADMIN || teamRepository.existsByMatterIdAndUserId(m.getId(), user.getId());
    }
}
