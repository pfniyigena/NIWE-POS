package com.posapp.assets;

import com.posapp.assets.dto.AssetDtos.AssetDto;
import com.posapp.assets.dto.AssetRequests.CheckInRequest;
import com.posapp.assets.dto.AssetRequests.CheckOutRequest;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Check-out and check-in of one asset (also used one by one by the bulk action). */
@Service
public class AssetActionService {

    private final FixedAssetRepository assetRepository;
    private final AssetCustodyService custodyService;
    private final FixedAssetService assetService;
    private final BranchAccessService branchAccessService;

    public AssetActionService(FixedAssetRepository assetRepository, AssetCustodyService custodyService, FixedAssetService assetService,
                              BranchAccessService branchAccessService) {
        this.assetRepository = assetRepository;
        this.custodyService = custodyService;
        this.assetService = assetService;
        this.branchAccessService = branchAccessService;
    }

    private FixedAsset find(Long id) {
        FixedAsset asset = assetRepository.findById(id).orElseThrow(() -> ApiException.notFound("Asset not found: " + id));
        branchAccessService.assertCanAccess(asset.getBranchId());
        return asset;
    }

    @Transactional
    public AssetDto checkOut(Long id, CheckOutRequest r) {
        FixedAsset asset = find(id);
        custodyService.checkOut(asset, r.custodianUserId(), r.custodianName(), r.date(), r.condition(), r.note());
        return assetService.toDto(assetRepository.save(asset));
    }

    @Transactional
    public AssetDto checkIn(Long id, CheckInRequest r) {
        FixedAsset asset = find(id);
        custodyService.checkIn(asset, r.date(), r.condition(), r.note());
        return assetService.toDto(assetRepository.save(asset));
    }
}
