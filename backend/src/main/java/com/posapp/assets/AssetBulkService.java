package com.posapp.assets;

import com.posapp.assets.dto.AssetDtos.BulkResult;
import com.posapp.assets.dto.AssetDtos.BulkSkipped;
import com.posapp.assets.dto.AssetRequests.BulkRequest;
import com.posapp.assets.dto.AssetRequests.CheckInRequest;
import com.posapp.assets.dto.AssetRequests.CheckOutRequest;
import com.posapp.assets.dto.AssetRequests.TransferRequest;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Moves, hands out or takes back several assets at once. Each asset is its own transaction, so one that cannot be done does not stop the rest. */
@Service
public class AssetBulkService {

    static final int MAX = 200;

    private final FixedAssetService assetService;
    private final AssetActionService actionService;
    private final FixedAssetRepository repository;

    public AssetBulkService(FixedAssetService assetService, AssetActionService actionService, FixedAssetRepository repository) {
        this.assetService = assetService;
        this.actionService = actionService;
        this.repository = repository;
    }

    public BulkResult run(BulkRequest r) {
        if (r == null || r.ids() == null || r.ids().isEmpty()) throw ApiException.badRequest("Choose at least one asset.");
        LinkedHashSet<Long> ids = new LinkedHashSet<>(r.ids());
        if (ids.size() > MAX) throw ApiException.badRequest("At most " + MAX + " assets at a time.");
        String action = r.action() == null ? "" : r.action().trim().toUpperCase(Locale.ROOT);
        if (!List.of("TRANSFER", "CHECK_OUT", "CHECK_IN").contains(action)) throw ApiException.badRequest("Action must be TRANSFER, CHECK_OUT or CHECK_IN.");
        LocalDate date = r.date() != null ? r.date() : LocalDate.now();
        int done = 0;
        List<BulkSkipped> skipped = new ArrayList<>();
        for (Long id : ids) {
            try {
                switch (action) {
                    case "TRANSFER" -> assetService.transfer(id, new TransferRequest(r.toBranchId(), date, r.toLocation(), null, r.reason()));
                    case "CHECK_OUT" -> actionService.checkOut(id, new CheckOutRequest(r.custodianUserId(), r.custodianName(), date, null, r.reason()));
                    default -> actionService.checkIn(id, new CheckInRequest(date, null, r.reason()));
                }
                done++;
            } catch (RuntimeException ex) {
                String code = repository.findById(id).map(FixedAsset::getCode).orElse(null);
                String msg = ex instanceof ApiException || ex instanceof org.springframework.security.access.AccessDeniedException ? ex.getMessage() : "Could not be processed.";
                skipped.add(new BulkSkipped(id, code, msg));
            }
        }
        return new BulkResult(done, skipped);
    }
}
