package com.posapp.propertymgmt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the property "Owners" panel reads and sends: who owns a property and what share each one has, from a date. */
public final class PropertyMgmtOwnershipDtos {

    private PropertyMgmtOwnershipDtos() {
    }

    public record ShareDto(Long ownerId, String ownerCode, String ownerName, BigDecimal percent, boolean lead) {
    }

    /**
     * validFrom empty = from the beginning. current = the latest split (the one in force now). lockedReason is set when a statement already agreed covers the dates of the split, so it
     * can no longer be changed or removed.
     */
    public record SplitDto(LocalDate validFrom, String key, boolean current, List<ShareDto> shares, boolean locked, String lockedReason, boolean canRemove) {
    }

    /** rebuild: codes of draft statements that have lines dated after the change and should be rebuilt. */
    public record OwnershipView(Long propertyId, String propertyCode, String propertyName, Long leadOwnerId, String leadOwnerName, List<SplitDto> splits, LocalDate lockedThrough,
                                List<String> rebuild, boolean canManage) {
    }

    public record ShareRequest(Long ownerId, BigDecimal percent) {
    }

    /** validFrom empty = from the beginning (replaces the first split). leadOwnerId empty = the owner with the biggest share. */
    public record OwnershipRequest(LocalDate validFrom, List<ShareRequest> shares, Long leadOwnerId) {
    }
}
