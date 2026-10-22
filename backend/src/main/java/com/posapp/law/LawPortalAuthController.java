package com.posapp.law;

import com.posapp.law.dto.LawPortalDtos.InviteInfoDto;
import com.posapp.law.dto.LawPortalDtos.SessionDto;
import com.posapp.law.dto.LawPortalRequests.AcceptRequest;
import com.posapp.law.dto.LawPortalRequests.LoginRequest;
import com.posapp.law.dto.LawPortalRequests.PasswordRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Client portal sign-in. The first three are open to everyone (SecurityConfig); the password change needs a portal token. */
@RestController
@RequestMapping("/api/portal")
public class LawPortalAuthController {

    private final LawPortalAuthService service;

    public LawPortalAuthController(LawPortalAuthService service) {
        this.service = service;
    }

    @PostMapping("/auth/login")
    public SessionDto login(@RequestBody LoginRequest r) {
        return service.login(r);
    }

    @GetMapping("/auth/invite")
    public InviteInfoDto invite(@RequestParam String token) {
        return service.inviteInfo(token);
    }

    @PostMapping("/auth/accept")
    public SessionDto accept(@RequestBody AcceptRequest r) {
        return service.accept(r);
    }

    @PostMapping("/password")
    public void password(@AuthenticationPrincipal LawPortalPrincipal me, @RequestBody PasswordRequest r) {
        service.changePassword(me, r);
    }
}
