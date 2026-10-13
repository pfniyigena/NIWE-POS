package com.posapp.user.dto;

/** One entry of the area catalog the Roles screen builds its grid from. */
public record AccessAreaDto(String code, String label, String description, boolean viewOnly) {
}
