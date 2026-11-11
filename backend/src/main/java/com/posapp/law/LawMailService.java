package com.posapp.law;

import com.posapp.i18n.I18n;
import com.posapp.email.MailService;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/** The daily e-mail to each lawyer: deadlines and tasks that are overdue or due soon. Failures are logged and never stop the run. */
@Service
public class LawMailService {

    private static final Logger log = LoggerFactory.getLogger(LawMailService.class);

    private final MailService mailService;
    private final ModuleService moduleService;
    private final LawService lawService;
    private final LawWorkService workService;
    private final UserRepository userRepository;

    public LawMailService(MailService mailService, ModuleService moduleService, LawService lawService, LawWorkService workService, UserRepository userRepository) {
        this.mailService = mailService;
        this.moduleService = moduleService;
        this.lawService = lawService;
        this.workService = workService;
        this.userRepository = userRepository;
    }

    @Scheduled(cron = "0 15 6 * * *")
    public void dailyDigest() {
        try {
            if (!moduleService.isEnabled(ModuleKey.LAW_FIRM)) return;
            LawSettings s = lawService.settingsEntity();
            if (!s.isDigestEnabled()) return;
            for (AppUser u : userRepository.findByActiveTrue()) {
                if (u.getEmail() == null || u.getEmail().isBlank()) continue;
                try {
                    String html = workService.digestHtml(u, s.getReminderDays());
                    if (html != null) mailService.send(u.getEmail(), I18n.t("Your deadlines and tasks"), html, List.of());
                } catch (Exception e) {
                    log.warn("Could not send the law digest to {}: {}", u.getUsername(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Law digest skipped: {}", e.getMessage());
        }
    }
}
