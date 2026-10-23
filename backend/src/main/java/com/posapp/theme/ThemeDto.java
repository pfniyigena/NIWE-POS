package com.posapp.theme;

/** A theme choice: the theme id ("navy", "white", "custom", ...) and, for "custom", its two colours. */
public record ThemeDto(String theme, String customBg, String customAccent) {
}
