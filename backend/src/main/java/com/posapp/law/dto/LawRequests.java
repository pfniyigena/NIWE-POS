package com.posapp.law.dto;

import com.posapp.law.BillingType;
import com.posapp.law.ContactType;
import com.posapp.law.DeadlineType;
import com.posapp.law.MatterStatus;
import com.posapp.law.NoteKind;
import com.posapp.law.PartyRole;
import com.posapp.law.TaskPriority;
import com.posapp.law.TaskStatus;
import com.posapp.law.TeamRole;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class LawRequests {

    private LawRequests() {
    }

    public record SettingsRequest(String practiceAreas, String jurisdictions, Integer reminderDays, Boolean digestEnabled, Boolean restrictedByDefault,
                                  BillingType defaultBillingType, Boolean trustEnabled, String trustBankName, String trustAccountNumber, Boolean portalEnabled,
                                  Boolean escalationEnabled, Integer escalationDays, Boolean collectionsEnabled, Integer collectionsFirstDays, Integer collectionsRepeatDays, Integer collectionsMax,
                                  Integer defaultTermDays) {
    }

    public record ContactRequest(String name, ContactType type, String phone, String email, String idNumber, String address, String notes) {
    }

    public record MatterRequest(String title, String description, Long clientCustomerId, String practiceArea, String jurisdiction, String courtOrForum,
                                String courtCaseNumber, Long branchId, BillingType billingType, String billingNote, Boolean restricted, Long leadUserId,
                                LocalDate openedDate) {
    }

    public record StatusRequest(MatterStatus status, String outcome, Boolean force) {
    }

    public record TeamMemberRequest(Long userId, TeamRole role) {
    }

    public record TeamRequest(List<TeamMemberRequest> members) {
    }

    public record PartyRequest(Long contactId, PartyRole role, String note) {
    }

    public record NoteRequest(NoteKind kind, String text) {
    }

    public record TaskRequest(String title, String description, LocalDate dueDate, Long assignedToUserId, TaskPriority priority) {
    }

    public record TaskStatusRequest(TaskStatus status) {
    }

    public record DeadlineRequest(String title, DeadlineType type, LocalDate dueDate, LocalTime dueTime, Long assignedToUserId, String notes) {
    }

    public record ConflictSearchRequest(String name, String phone, String idNumber, String email, Long excludeMatterId) {
    }

    public record ClearRequest(String note) {
    }
}
