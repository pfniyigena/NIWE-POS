package com.posapp.helpdesk;

/**
 * Fourier: "Add Help Desk module ... should manage customer and internal support requests" —
 * one ticket model covers both (see {@code HelpDeskTicket}'s own javadoc for why), distinguished
 * only by this field so the admin list can filter by it rather than needing two separate screens.
 */
public enum HelpDeskTicketType {
    /** Raised by or on behalf of a customer (walk-in/guest or an existing {@code Customer} record). */
    CUSTOMER,
    /** Staff-to-staff — an internal request (e.g. "printer in Store 2 is down"), not tied to any customer. */
    INTERNAL
}
