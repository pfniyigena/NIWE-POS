package com.posapp.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class HkRequests {

    private HkRequests() {
    }

    public record StaffRequest(@NotBlank(message = "Name is required") String name, Boolean active) {
    }

    public record ChecklistRequest(List<ChecklistLine> items) {
    }

    public record ChecklistLine(@NotBlank(message = "Each checklist line needs a label") String label, Boolean active) {
    }

    public record AssignRequest(Long staffId) {
    }

    /** Spreads the day's unassigned tasks over these housekeepers (or every active one when none are given). */
    public record AutoAssignRequest(LocalDate date, List<Long> staffIds) {
    }

    public record StartRequest(Long staffId) {
    }

    public record SkipRequest(String reason) {
    }

    public record InspectCheck(String label, Boolean passed) {
    }

    public record InspectRequest(Boolean passed, List<InspectCheck> checks, String note) {
    }

    public record DndRequest(@NotNull(message = "Say whether the room is on do-not-disturb") Boolean dnd) {
    }

    public record LostFoundRequest(@NotBlank(message = "Describe the item") String description, LocalDate foundOn, Long roomId, String location,
                                   String foundBy, Long guestId, String notes) {
    }

    public record ClaimRequest(String claimedBy, Long guestId, LocalDate claimedOn, String notes) {
    }

    public record DisposeRequest(String notes) {
    }

    public record MinibarItemRequest(@NotBlank(message = "Name is required") String name, @NotNull(message = "Price is required") BigDecimal price,
                                     Boolean taxable, Boolean active) {
    }

    public record MinibarLine(@NotNull(message = "Choose the item") Long itemId, @NotNull(message = "Quantity is required") Integer qty) {
    }

    public record MinibarUseRequest(@NotNull(message = "Choose the room") Long roomId, List<MinibarLine> lines) {
    }

    public record LinenRequest(@NotBlank(message = "Item is required") String item, LocalDate logDate, Integer sent, Integer returned, Integer damaged, String notes) {
    }
}
