package com.posapp.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);

    /** Used by UserService.setPin to enforce PIN uniqueness among active staff. */
    List<AppUser> findByActiveTrue();

    /** Used by CashierSessionService to resolve a PIN to a cashier — see AppUser.pinHash. */
    List<AppUser> findByActiveTrueAndPinHashIsNotNull();

    /** Forgot-password lookup (Fourier: "user will input the email and he/she can reset the password") — see AppUser.email's javadoc. */
    Optional<AppUser> findByEmailIgnoreCase(String email);

    /** Email-uniqueness check in UserService, same shape as existsByUsernameIgnoreCase. */
    boolean existsByEmailIgnoreCase(String email);

    /** How many users hold a given custom role — blocks deleting a role that is still in use. */
    long countByCustomRoleId(Long customRoleId);
}
