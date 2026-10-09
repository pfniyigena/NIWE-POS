package com.posapp.register;

import com.posapp.common.BaseEntity;
import com.posapp.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One cashier's own working session inside a shift (a {@link RegisterSession}, i.e. one till
 * open/close). A shift can have several of these open at the same time — that's what lets more
 * than one person ring sales on the same physical terminal during the same till session, each
 * identifying themselves with their own PIN (see {@code CashierSessionService.joinShift}), while
 * the till's cash float/reconciliation stays a single shared thing at the {@link RegisterSession}
 * level.
 *
 * <p>This is bookkeeping only: it does not replace or widen the JWT that actually authorizes API
 * calls, and it never changes what a request is allowed to do. It exists purely to tag which
 * cashier's tabs/orders these are — for the terminal's tab list, for the receipt, and for
 * reporting who actually rang up a given sale during a shared shift.</p>
 */
@Getter
@Setter
@Entity
public class CashierSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private RegisterSession registerSession;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser cashier;

    private Instant startedAt = Instant.now();
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    private CashierSessionStatus status = CashierSessionStatus.OPEN;

    /**
     * Which of this cashier's open tabs (a {@code SaleOrder} id) is currently on screen at the
     * terminal — Task: "Add customer-facing double screen". Deliberately a plain nullable
     * {@code Long}, not a {@code @ManyToOne SaleOrder}: {@code order} already depends on
     * {@code register} (via {@code SaleOrder.cashierSession}), and a real relationship back the
     * other way would make the two packages depend on each other. This is purely a "what's on
     * screen right now" pointer for a second, customer-facing display to poll — it is never read
     * by checkout/reporting/reconciliation logic, the same "display concern only" carve-out
     * {@code PaymentMethodEntity.sortOrder} already has. Null for every cashier session that
     * predates this pass, and whenever no tab is currently selected (e.g. between the cashier
     * session opening and its first tab being created) — a customer display polling a null value
     * just shows its idle/welcome screen.
     */
    private Long activeOrderId;
}
