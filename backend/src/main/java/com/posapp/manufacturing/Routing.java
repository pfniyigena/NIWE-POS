package com.posapp.manufacturing;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Manufacturing Module — "Settings (Production routings etc)" (Fourier's own naming). An ordered
 * list of {@link RoutingOperation}s a production run passes through — e.g. "Cut", "Assemble",
 * "Paint", "Pack", each possibly at a different {@link Workstation}. A {@link Bom} optionally
 * points at one Routing; a {@link ManufacturingOrder} snapshots that reference at confirm time
 * (same "lock it in once committed" convention {@code SalesOrderReturnService}/{@code
 * PurchaseOrderService} already use for their own referenced records).
 *
 * <p>Routing is entirely optional — a simple shop that doesn't track operation-by-operation
 * progress can leave a Bom's routing unset and a Manufacturing Order still works end to end
 * (materials in, finished goods out); Routing only adds the labor/time estimate on top for a
 * store that wants it.
 */
@Getter
@Setter
@Entity
public class Routing extends BaseEntity {

    private String name;

    private String description;

    private boolean active = true;

    @OneToMany(mappedBy = "routing", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoutingOperation> operations = new ArrayList<>();
}
