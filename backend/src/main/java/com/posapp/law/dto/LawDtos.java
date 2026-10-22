package com.posapp.law.dto;

import com.posapp.law.BillingType;
import com.posapp.law.ContactType;
import com.posapp.law.DeadlineStatus;
import com.posapp.law.DeadlineType;
import com.posapp.law.MatterStatus;
import com.posapp.law.NoteKind;
import com.posapp.law.PartyRole;
import com.posapp.law.TaskPriority;
import com.posapp.law.TaskStatus;
import com.posapp.law.TeamRole;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class LawDtos {

    private LawDtos() {
    }

    public record Option(Long id, String name) {
    }

    public record SettingsDto(List<String> practiceAreas, List<String> jurisdictions, int reminderDays, boolean digestEnabled, boolean restrictedByDefault,
                              BillingType defaultBillingType, boolean trustEnabled, String trustBankName, String trustAccountNumber, boolean portalEnabled,
                              boolean escalationEnabled, int escalationDays, boolean collectionsEnabled, int collectionsFirstDays, int collectionsRepeatDays, int collectionsMax,
                              int defaultTermDays) {
    }

    public record ContactDto(Long id, String name, ContactType type, String phone, String email, String idNumber, String address, String notes, long matters) {
    }

    public record ClientRow(Long id, String name, String phone, String email, long matters, long openMatters) {
    }

    public record TeamDto(Long id, Long userId, String userName, TeamRole role) {
    }

    public record PartyDto(Long id, Long contactId, String name, ContactType contactType, String phone, String email, String idNumber, PartyRole role, String note) {
    }

    public record DeadlineDto(Long id, Long matterId, String matterNumber, String matterTitle, String title, DeadlineType type, LocalDate dueDate, LocalTime dueTime,
                              DeadlineStatus status, Long assignedToUserId, String assignedToName, String notes, boolean overdue, long daysLeft, String completedByName) {
    }

    public record TaskDto(Long id, Long matterId, String matterNumber, String matterTitle, String title, String description, LocalDate dueDate, Long assignedToUserId,
                          String assignedToName, TaskStatus status, TaskPriority priority, boolean overdue, Instant completedAt) {
    }

    public record NoteDto(Long id, NoteKind kind, String text, String createdByName, Instant createdAt, boolean canDelete) {
    }

    public record MatterRow(Long id, String number, String title, MatterStatus status, String practiceArea, String jurisdiction, Long clientId, String clientName,
                            Long leadUserId, String leadName, LocalDate openedDate, LocalDate closedDate, BillingType billingType, boolean restricted,
                            boolean conflictCleared, int openTasks, int overdueTasks, LocalDate nextDeadline, int overdueDeadlines) {
    }

    public record MatterDto(Long id, String number, String title, String description, MatterStatus status, String practiceArea, String jurisdiction, String courtOrForum,
                            String courtCaseNumber, Long branchId, Long clientId, String clientName, String clientPhone, String clientEmail, BillingType billingType,
                            String billingNote, LocalDate openedDate, LocalDate closedDate, String outcome, boolean restricted, boolean conflictCleared,
                            String conflictNote, String conflictClearedBy, Instant conflictClearedAt, Long leadUserId, String leadName, String createdByName,
                            List<TeamDto> team, List<PartyDto> parties, List<DeadlineDto> deadlines, List<TaskDto> tasks, List<NoteDto> notes,
                            boolean canWork, boolean canLead) {
    }

    public record SummaryDto(int openMatters, int onHold, int deadlinesThisWeek, int overdueDeadlines, int overdueTasks, int conflictsNotCleared) {
    }

    public record MineDto(List<MatterRow> matters, List<TaskDto> tasks, List<DeadlineDto> deadlines) {
    }

    public record MatterRef(Long id, String number, String title, MatterStatus status, String role, boolean hidden) {
    }

    public record ConflictHit(String kind, Long id, String name, String phone, String email, String idNumber, List<String> reasons, String severity,
                              List<MatterRef> matters) {
    }

    public record ConflictSubject(String label, String role, List<ConflictHit> hits) {
    }

    public record ConflictResult(Long logId, int hits, List<ConflictSubject> subjects) {
    }

    public record ConflictLogDto(Long id, String query, Long matterId, String matterNumber, int hits, String checkedBy, Instant at) {
    }
}
