package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskPriority;

import java.util.List;

public final class HelpDeskWorkflowRequests {

    private HelpDeskWorkflowRequests() {
    }

    public static class TagsRequest {
        private List<String> tags;

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }

    /** Blank or null clears that link. */
    public static class LinksRequest {
        private String invoiceNumber;
        private String workOrderNumber;

        public String getInvoiceNumber() { return invoiceNumber; }
        public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
        public String getWorkOrderNumber() { return workOrderNumber; }
        public void setWorkOrderNumber(String workOrderNumber) { this.workOrderNumber = workOrderNumber; }
    }

    public static class MergeRequest {
        private Long targetId;

        public Long getTargetId() { return targetId; }
        public void setTargetId(Long targetId) { this.targetId = targetId; }
    }

    /** action: ASSIGN (assignedToUserId, null unassigns), CLOSE, PRIORITY (priority), TAG (tag), WAIT, START. */
    public static class BulkRequest {
        private List<Long> ids;
        private String action;
        private Long assignedToUserId;
        private HelpDeskPriority priority;
        private String tag;

        public List<Long> getIds() { return ids; }
        public void setIds(List<Long> ids) { this.ids = ids; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public Long getAssignedToUserId() { return assignedToUserId; }
        public void setAssignedToUserId(Long assignedToUserId) { this.assignedToUserId = assignedToUserId; }
        public HelpDeskPriority getPriority() { return priority; }
        public void setPriority(HelpDeskPriority priority) { this.priority = priority; }
        public String getTag() { return tag; }
        public void setTag(String tag) { this.tag = tag; }
    }

    public record BulkResult(int done, List<Skipped> skipped) {
    }

    public record Skipped(Long id, String ticketNumber, String reason) {
    }
}
