package com.posapp.theme;

/** applyToWebsite: an administrator can also make this the theme of the public website and sign-in page. */
public record ThemeRequest(String theme, String customBg, String customAccent, Boolean applyToWebsite) {
}
