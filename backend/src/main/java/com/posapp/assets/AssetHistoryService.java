package com.posapp.assets;

import com.posapp.security.AppUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Writes and reads the history lines of an asset, and knows who is signed in. */
@Service
public class AssetHistoryService {

    /** Who is acting: user id, display name, whether an administrator, whether allowed to approve large disposals. */
    public record Actor(Long id, String name, boolean admin, boolean disposalApprover) {
    }

    private final AssetEventRepository repository;

    public AssetHistoryService(AssetEventRepository repository) {
        this.repository = repository;
    }

    public static Actor actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p && p.getUser() != null) {
            boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            boolean approver = admin || auth.getAuthorities().stream().anyMatch(a -> "PERM_APPROVE_ASSET_DISPOSAL".equals(a.getAuthority()));
            return new Actor(p.getUser().getId(), p.getUser().getFullName(), admin, approver);
        }
        return new Actor(null, "System", false, false);
    }

    @Transactional
    public void event(Long assetId, String kind, String detail) {
        AssetEvent ev = new AssetEvent();
        ev.setAssetId(assetId);
        ev.setKind(kind);
        ev.setDetail(detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail);
        ev.setCreatedByName(actor().name());
        repository.save(ev);
    }

    @Transactional
    public void deleteAllOf(Long assetId) {
        repository.deleteAll(repository.findByAssetIdOrderByIdDesc(assetId));
    }

    @Transactional(readOnly = true)
    public List<AssetEvent> events(Long assetId) {
        return repository.findByAssetIdOrderByIdDesc(assetId);
    }
}
