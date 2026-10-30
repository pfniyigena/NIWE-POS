package com.posapp.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class HelpDeskCannedRequest {
    @NotBlank(message = "A title is required")
    @Size(max = 120, message = "The title can be at most 120 characters")
    private String title;
    @NotBlank(message = "The reply text is required")
    @Size(max = 4000, message = "The reply can be at most 4000 characters")
    private String body;
    private Long categoryId;
    private Boolean active;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
