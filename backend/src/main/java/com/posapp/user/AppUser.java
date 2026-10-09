package com.posapp.user;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

    private String username;
    private String passwordHash;
    private String fullName;

    /**
     * Fourier: "Add forget password feature where user will input the email and he/she can reset
     * the password" — forgot-password is keyed by this, not the username, so a user can't reset
     * their password at all until this is set. Nullable: an existing deployment's staff accounts
     * predate this field, same "new field, no retrofit" convention as every other optional column
     * added to this app over time (e.g. Customer.taxId) — an admin or the user themselves (My
     * Profile) sets it whenever they're ready to use forgot-password. Enforced unique when
     * non-blank (see UserService) so PasswordResetService.findByEmail always resolves to exactly
     * one account.
     */
    private String email;

    /**
     * A short PIN (hashed with the same {@link org.springframework.security.crypto.password.PasswordEncoder}
     * as the password) that lets this cashier identify themselves at the terminal without a full
     * login — see {@code com.posapp.register.CashierSessionService}. Null until an admin sets one.
     * Enforced unique among active users at set-time (in UserService.setPin) so a PIN alone is
     * enough to look up which cashier it belongs to.
     */
    private String pinHash;

    @Enumerated(EnumType.STRING)
    private Role role = Role.CASHIER;

    private boolean active = true;

    /**
     * Fourier: "Build a full permissions system" — additive, ADMIN-tier powers granted to an
     * otherwise-{@code CASHIER} account; see {@link Permission}'s own javadoc for the full
     * reasoning. Eagerly fetched (same convention as every other small, always-needed collection
     * in this app) since this is read on every request that builds an {@code AppUserPrincipal}.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "app_user_id"))
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = new HashSet<>();

    /**
     * Fourier: staff restricted to their own branch's data, with admin override. The branches this
     * user may work with. Empty (the default) means unrestricted, as does the ADMIN role — see
     * {@code com.posapp.branch.BranchAccessService} for the one place the rule lives. Plain ids
     * rather than Branch references, same snapshot-id convention as the branch tag on documents.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_branches", joinColumns = @JoinColumn(name = "app_user_id"))
    @Column(name = "branch_id")
    private Set<Long> branchIds = new HashSet<>();
}
