package com.posapp.apps.efris;

import com.posapp.common.BaseEntity;
import com.posapp.register.Register;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-terminal EFRIS configuration — Fourier's own follow-up spec: "Table EFRIS_setting, column
 * EFRIS_url... EFRIS_setting for one terminal." Deliberately one row per {@link Register}, not a
 * store-wide setting the way every other admin-configurable value in this app is (business
 * details, system settings, tax rates, payment methods). Real EFRIS deployments fiscalize through a
 * local device on each till's own network (a "Sales Data Controller"/SDC) — so each terminal talks
 * to its own EFRIS URL, not one shared endpoint for the whole store.
 */
@Getter
@Setter
@Entity
@Table(name = "efris_setting")
public class EfrisSetting extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "register_id", nullable = false, unique = true)
    private Register register;

    @Column(name = "efris_url", columnDefinition = "TEXT")
    private String efrisUrl;

    /**
     * The JSON-RPC "db" argument EFRIS's own middleware expects (Fourier's own working sample:
     * {@code args[0]}, e.g. "RRA_VSDC") — a connection credential, not a POS concept, so it's kept
     * alongside the URL on the same per-terminal row rather than a new global settings table.
     */
    @Column(name = "efris_db", columnDefinition = "TEXT")
    private String efrisDb;

    /** The JSON-RPC "uid" argument (args[1]) — also sent as the invoice payload's own user_id field. */
    @Column(name = "efris_uid")
    private Integer efrisUid;

    /**
     * The JSON-RPC "password" argument (args[2]) — stored in plaintext by necessity: EFRIS's own
     * protocol requires resending the raw value on every call, the same way a third-party API key
     * would be, so there's nothing to hash it against.
     */
    @Column(name = "efris_password", columnDefinition = "TEXT")
    private String efrisPassword;

    /**
     * Task: "On EFRIS setting add two logo (URA LOGO, EFRIS LOGO) ... added left and right on the
     * printout." A data: URL, same convention as {@code StoreSettings.logoDataUrl} — the admin
     * screen reads the chosen image file with FileReader.readAsDataURL and sends the result as-is,
     * so no separate file-storage/CDN setup is needed. Per-terminal (like the rest of this entity),
     * not store-wide: a chain with tills configured for different EFRIS deployments could
     * conceivably want different letterhead logos, though in practice every terminal usually
     * shares the same two. Printed on the left of the SDC INFORMATION block (see
     * ReceiptComponent) — the Rwanda Revenue Authority's own logo, not this app's.
     *
     * <p>Task 1: "... yes do it for all" — renamed from {@code uraLogoDataUrl}/{@code
     * ura_logo_data_url} to match the app's Rwanda/RRA branding. See the README's dated entry for
     * this change for the one-line SQL to carry over any logo already saved in the old column.</p>
     */
    @Column(name = "rra_logo_data_url", columnDefinition = "TEXT")
    private String rraLogoDataUrl;

    /** Same as {@link #rraLogoDataUrl} but for the EFRIS logo, printed on the right. */
    @Column(name = "efris_logo_data_url", columnDefinition = "TEXT")
    private String efrisLogoDataUrl;
}
