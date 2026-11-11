package com.posapp.propertymgmt;

import com.posapp.i18n.I18n;
import com.posapp.audit.AuditLogService;
import com.posapp.email.MailService;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.EmailAllStatementsRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.GenerateAllStatementsRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtStatementDtos.AutoRunResult;
import com.posapp.propertymgmt.dto.PropertyMgmtStatementDtos.EmailAllResult;
import com.posapp.propertymgmt.dto.PropertyMgmtStatementDtos.GenerateAllResult;
import com.posapp.propertymgmt.dto.PropertyMgmtStatementDtos.StatementDto;
import com.posapp.propertymgmt.dto.PropertyMgmtStatementDtos.StatementRow;
import com.posapp.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The monthly statement run. Every morning it checks whether last month's statements are due (switched on in Settings, the chosen day of the month reached, last month not made yet) and
 * if so makes a draft for every owner. With "agree and e-mail" also on, the drafts that need no attention are agreed and sent to their owners; the rest stay drafts for a person to
 * look at: a statement with a warning (no fee set, no bank account), or one where the owner would get nothing or owe money, is never agreed automatically.
 */
@Component
public class PmStatementAuto {

    private static final Logger log = LoggerFactory.getLogger(PmStatementAuto.class);

    private final ModuleService moduleService;
    private final PmSettingsService settings;
    private final PmStatementService statements;
    private final PmStatementMailService mail;
    private final MailService mailService;
    private final AuditLogService auditLogService;

    public PmStatementAuto(ModuleService moduleService, PmSettingsService settings, PmStatementService statements, PmStatementMailService mail, MailService mailService,
                           AuditLogService auditLogService) {
        this.moduleService = moduleService;
        this.settings = settings;
        this.statements = statements;
        this.mail = mail;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
    }

    @Scheduled(cron = "0 40 6 * * *")
    public void morning() {
        try {
            if (!moduleService.isEnabled(ModuleKey.PROPERTY_MANAGEMENT)) return;
            AutoRunResult r = run(LocalDate.now(), false);
            if (r.ran()) log.info("Property Management monthly statements: {} made, {} agreed, {} e-mailed", r.created(), r.agreed(), r.emailed());
        } catch (Exception e) {
            log.warn("Property Management monthly statements failed: {}", e.getMessage());
        }
    }

    /** {@code force}: make last month's statements now whatever the switch, the day or an earlier run says (the "run now" button). */
    public AutoRunResult run(LocalDate today, boolean force) {
        PmSettings s = settings.entity();
        LocalDate to = today.withDayOfMonth(1).minusDays(1);
        LocalDate from = to.withDayOfMonth(1);
        if (!force) {
            if (!s.isAutoStatements()) return none(I18n.t("Automatic statements are off."), from, to);
            if (today.getDayOfMonth() < s.getAutoStatementsDay()) return none(I18n.t("Not due until day ") + s.getAutoStatementsDay() + I18n.t(" of the month."), from, to);
            if (s.getAutoStatementsThrough() != null && !s.getAutoStatementsThrough().isBefore(to)) return none(I18n.t("Last month's statements were already made."), from, to);
        }
        Actor system = system();
        List<String> notes = new ArrayList<>();
        GenerateAllResult made = statements.generateAll(system, new GenerateAllStatementsRequest(from, to));
        notes.addAll(made.notes());
        int agreed = 0, emailed = 0;
        boolean send = s.isAutoSendStatements();
        if (send) {
            List<Long> ids = new ArrayList<>();
            for (StatementRow row : made.statements()) {
                StatementDto d = statements.get(system, row.id());
                String why = d.statement().netPayable().signum() <= 0 ? (d.statement().netPayable().signum() == 0 ? I18n.t("the owner would get nothing") : I18n.t("the owner would owe money")) : !d.warnings().isEmpty() ? d.warnings().get(0) : null;
                if (why != null) { notes.add(row.code() + " (" + row.ownerName() + I18n.t(") was left as a draft: ") + why); continue; }
                statements.finalizeStatement(system, row.id());
                ids.add(row.id());
                agreed++;
            }
            if (!ids.isEmpty()) {
                if (!mailService.isConfigured()) {
                    notes.add(I18n.t("E-mail is not set up, so the ") + ids.size() + I18n.t(" agreed statement(s) were not sent."));
                } else {
                    EmailAllResult r = mail.emailAll(system, new EmailAllStatementsRequest(false, false, ids));
                    emailed = r.sent();
                    notes.addAll(r.notes());
                }
            }
        }
        s = settings.entity();
        s.setAutoStatementsThrough(to);
        settings.save(s);
        String msg = made.created() == 0 ? I18n.t("No statements were needed for ") + from + I18n.t(" to ") + to + "." : I18n.t("Made ") + made.created() + I18n.t(" statement(s) for ") + from + I18n.t(" to ") + to
                + (send ? I18n.t(", agreed ") + agreed + I18n.t(" and e-mailed ") + emailed : I18n.t(", as drafts")) + ".";
        auditLogService.record("PM_STATEMENTS_AUTO", msg);
        return new AutoRunResult(true, msg, from, to, made.created(), agreed, emailed, notes);
    }

    private static AutoRunResult none(String why, LocalDate from, LocalDate to) {
        return new AutoRunResult(false, why, from, to, 0, 0, 0, List.of());
    }

    private static Actor system() {
        AppUser u = new AppUser();
        u.setFullName("The monthly run");
        u.setUsername("system");
        return new Actor(u, true, true, true);
    }
}
