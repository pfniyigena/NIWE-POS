package com.posapp.security;

import com.posapp.user.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Wraps our AppUser as a Spring Security principal so controllers can read the logged-in user via Authentication. */
public class AppUserPrincipal implements UserDetails {

    private final AppUser user;

    public AppUserPrincipal(AppUser user) {
        this.user = user;
    }

    public AppUser getUser() {
        return user;
    }

    public Long getId() {
        return user.getId();
    }

    /**
     * Fourier: "Build a full permissions system" — the usual {@code ROLE_<role>} authority, plus
     * one {@code PERM_<name>} authority per permission {@link com.posapp.user.AppUser#getPermissions()}
     * grants. Purely additive: nothing here ever removes what the role authority already allows.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Area authorities (AREA_<AREA>_VIEW / AREA_<AREA>_MANAGE / AREA_ANY) come from the user's custom role — see CustomRole.
        Stream<SimpleGrantedAuthority> areas = user.getRole() == com.posapp.user.Role.ADMIN
                ? Stream.<SimpleGrantedAuthority>empty()
                : com.posapp.user.AccessGrants.authorities(user.effectiveGrants()).stream().map(SimpleGrantedAuthority::new);
        return Stream.concat(
                Stream.concat(
                        Stream.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                        user.getPermissions().stream().map(p -> new SimpleGrantedAuthority("PERM_" + p.name()))),
                areas
        ).collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
