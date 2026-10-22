package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** What the client may see and do on one matter in the portal. A matter with no row, or with enabled off, is invisible to the client. */
@Getter
@Setter
@Entity
public class LawMatterPortal extends BaseEntity {

    @Column(name = "matter_id", nullable = false, unique = true)
    private Long matterId;

    @Column(nullable = false)
    private boolean enabled = false;

    /** Show the deadlines and court dates that staff marked as visible to the client. */
    @Column(nullable = false)
    private boolean showDeadlines = true;

    /** Show the documents marked "shared with the client" (never the confidential ones). */
    @Column(nullable = false)
    private boolean showDocuments = true;

    /** Show the matter's issued invoices and what is still owed. */
    @Column(nullable = false)
    private boolean showInvoices = false;

    /** Show the money held for the client in the trust account (posted entries only). */
    @Column(nullable = false)
    private boolean showTrust = false;

    /** Let the client send documents to the firm (they wait for a lawyer's review). */
    @Column(nullable = false)
    private boolean allowUpload = false;

    /** Let the client and the team exchange short messages. */
    @Column(nullable = false)
    private boolean allowMessages = false;

    /** A short plain-language progress note for the client. */
    @Column(length = 2000)
    private String clientUpdate;

    private Instant clientUpdateAt;
    private String clientUpdateBy;
}
