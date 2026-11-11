package com.posapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.util.Map;

/**
 * {@code @EnableAsync}/{@code @EnableScheduling} — Fourier: "Wiring a handful of business events
 * to that same mailer" (see {@code com.posapp.alerts.AlertMailService}'s class javadoc for the
 * async side) and the quotation-expiry check (see {@code QuotationService}'s own {@code
 * @Scheduled} method). Neither annotation existed anywhere in this codebase before that feature.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class PosApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PosApplication.class);
        // v1.22.4: started as a jar (for example as a service, whose working directory is often not the jar's folder), the log files
        // go next to the jar. This is only a default with the lowest priority: app.logging.dir in application.properties, an
        // environment variable or a command-line argument still wins. Started from an IDE the default stays "logs".
        File jarFolder = jarFolder();
        if (jarFolder != null) {
            app.setDefaultProperties(Map.<String, Object>of(
                    "app.logging.dir", new File(jarFolder, "logs").getAbsolutePath(),
                    "app.backup.directory", new File(jarFolder, "backups").getAbsolutePath()));
        }
        app.run(args);
    }

    /** The folder holding the running jar, or null when the application is not started from a jar. */
    static File jarFolder() {
        File source = new ApplicationHome(PosApplication.class).getSource();
        if (source != null && source.isFile() && source.getName().toLowerCase().endsWith(".jar")) {
            return source.getAbsoluteFile().getParentFile();
        }
        return null;
    }
}
