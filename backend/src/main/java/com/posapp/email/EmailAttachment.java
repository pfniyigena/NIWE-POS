package com.posapp.email;

/** One file attached to an outgoing email — see MailService.send. */
public record EmailAttachment(String filename, String contentType, byte[] bytes) {
}
