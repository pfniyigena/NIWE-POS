package com.posapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

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
        SpringApplication.run(PosApplication.class, args);
    }
}
