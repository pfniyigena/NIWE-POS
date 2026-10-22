package com.posapp.propertymgmt;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.propertymgmt.dto.PropertyMgmtRentDtos.InvoiceRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every morning, when "invoice rent automatically" is on, invoices the rent that is ready. */
@Component
public class PmRentSweep {

    private static final Logger log = LoggerFactory.getLogger(PmRentSweep.class);

    private final ModuleService moduleService;
    private final PmSettingsService settings;
    private final PmRentService rentService;

    public PmRentSweep(ModuleService moduleService, PmSettingsService settings, PmRentService rentService) {
        this.moduleService = moduleService;
        this.settings = settings;
        this.rentService = rentService;
    }

    @Scheduled(cron = "0 20 6 * * *")
    public void invoiceReadyRent() {
        try {
            if (!moduleService.isEnabled(ModuleKey.PROPERTY_MANAGEMENT) || !settings.entity().isAutoInvoice()) return;
            InvoiceRun run = rentService.runInvoicing(null, null, "The morning sweep");
            if (run.invoiced() > 0 || run.failed() > 0) log.info("Property Management rent sweep: {} invoiced, {} failed", run.invoiced(), run.failed());
        } catch (Exception e) {
            log.warn("Property Management rent sweep failed: {}", e.getMessage());
        }
    }
}
