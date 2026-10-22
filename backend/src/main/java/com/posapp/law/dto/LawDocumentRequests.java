package com.posapp.law.dto;

import com.posapp.law.DocCategory;
import com.posapp.law.TemplateKind;

/** What the document screens send (files are sent as multipart form fields instead). */
public final class LawDocumentRequests {

    private LawDocumentRequests() {
    }

    public record DocUpdateRequest(String name, DocCategory category, String description) {
    }

    public record DocFlagsRequest(Boolean confidential, Boolean sharedWithClient) {
    }

    public record ReviewRequest(Boolean accept, String note) {
    }

    public record GenerateRequest(Long templateId, String name, Boolean confidential) {
    }

    public record TemplateRequest(String name, DocCategory category, String description, TemplateKind kind, String body, Boolean active) {
    }
}
