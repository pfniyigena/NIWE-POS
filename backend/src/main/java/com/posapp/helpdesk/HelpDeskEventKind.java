package com.posapp.helpdesk;

/** What happened on a ticket; stored as text in {@code help_desk_event.kind}. */
public enum HelpDeskEventKind {
    CREATED, EDITED, PRIORITY_CHANGED, ASSIGNED, STARTED, WAITING, RESUMED, RESOLVED, CLOSED, REOPENED, AUTO_CLOSED,
    REPLY_STAFF, REPLY_REQUESTER, NOTE, FILE, TAGS, LINKED, MERGED, RATED, SLA_WARNING, SLA_BREACH
}
