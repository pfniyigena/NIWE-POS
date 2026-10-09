package com.posapp.apps.efrischad;

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
 * Per-terminal EFRIS Chad configuration — Task: "Now I want to add new app (EFRIS Chad) with will
 * need the following settings: url, basic_auth_username, basic_password, sdc_number, branch_code,
 * mrc, integration_key. The integration with EFRIS Chad is via HTTP(S) with basic authentication."
 * One row per {@link Register}, same reasoning as {@link com.posapp.apps.efris.EfrisSetting}
 * (Rwanda's EFRIS integration): real fiscal devices/gateways are configured per till, not
 * store-wide, so each terminal gets its own connection settings row.
 *
 * <p>Deliberately its own entity/table rather than reusing {@code EfrisSetting} (Rwanda's): the
 * two integrations authenticate completely differently — Rwanda is JSON-RPC with a
 * {@code db}/{@code uid}/{@code password} triple, Chad is plain HTTP(S) with basic auth plus four
 * fiscal identifiers ({@code sdc_number}/{@code branch_code}/{@code mrc}/
 * {@code integration_key}) that have no Rwanda equivalent — forcing them into one shared shape
 * would mean a pile of fields that are null for whichever country a given terminal isn't
 * configured for. Same "structured, one-row-per-Register data doesn't belong in the generic Apps
 * key/value settings store" reasoning as Rwanda's own settings (see
 * {@code EfrisSettingService}'s javadoc) — that generic store was removed entirely for having no
 * real user in this codebase; this is what a real structured settings need looks like instead.
 *
 * <p>Started out settings-storage only ("I will share the resulting parsing later"), mirroring
 * exactly how {@code EfrisSetting}/{@code AppKey.EFRIS_RW} began before their own submission logic
 * existed — the actual HTTP(S) submission call, request-building, and {@code sdc_data} response
 * parsing were added in later passes (see {@code EfrisChadSubmissionService}).
 */
@Getter
@Setter
@Entity
@Table(name = "efris_chad_setting")
public class EfrisChadSetting extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "register_id", nullable = false, unique = true)
    private Register register;

    @Column(name = "url", columnDefinition = "TEXT")
    private String url;

    @Column(name = "basic_auth_username", columnDefinition = "TEXT")
    private String basicAuthUsername;

    /**
     * The HTTP Basic Authentication password — stored in plaintext by necessity, same reasoning as
     * {@code EfrisSetting.efrisPassword}: the raw value has to be resent (base64-encoded, not
     * hashed) on every call, the same way any third-party API credential would be.
     */
    @Column(name = "basic_password", columnDefinition = "TEXT")
    private String basicPassword;

    /** The Sales Data Controller number EFRIS Chad assigns this terminal — sent on every request per Fourier's sample payload. */
    @Column(name = "sdc_number", columnDefinition = "TEXT")
    private String sdcNumber;

    @Column(name = "branch_code", columnDefinition = "TEXT")
    private String branchCode;

    /** "mrc" per Fourier's own field list/sample payload — not expanded further here since he didn't spell it out; carried through as-is. */
    @Column(name = "mrc", columnDefinition = "TEXT")
    private String mrc;

    /**
     * A per-terminal integration credential (see Fourier's sample payload's own
     * {@code integration_key}) — stored in plaintext for the same reason as
     * {@link #basicPassword}.
     */
    @Column(name = "integration_key", columnDefinition = "TEXT")
    private String integrationKey;

    /**
     * Task 3: "Add also Chad Revenue Authority logo and EFRIS logo on EFRIS Chad setting" — Chad's
     * own equivalent of {@code EfrisSetting.rraLogoDataUrl} (Rwanda's RRA logo): a data: URL, same
     * FileReader.readAsDataURL convention, printed on the left of the receipt letterhead in place
     * of the RRA logo for a terminal mapped to EFRIS Chad instead of EFRIS Rwanda (a terminal is
     * only ever mapped to one, so the two never both render — see {@code
     * EfrisSettingService}/{@code EfrisChadSettingService}'s mutual-exclusivity javadoc). Named for
     * Chad's own revenue authority rather than reusing {@code rraLogoDataUrl}, since this app's
     * "dedicated field per distinct concept" convention treats "which country's revenue authority"
     * as part of the field's identity, not an interchangeable label.
     */
    @Column(name = "cra_logo_data_url", columnDefinition = "TEXT")
    private String craLogoDataUrl;

    /** Same as {@link #craLogoDataUrl} but for the EFRIS logo, printed on the right — Chad's own copy of {@code EfrisSetting.efrisLogoDataUrl}. */
    @Column(name = "efris_logo_data_url", columnDefinition = "TEXT")
    private String efrisLogoDataUrl;
}
