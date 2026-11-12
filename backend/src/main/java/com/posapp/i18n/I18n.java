package com.posapp.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * v1.26.0: texts that the server writes itself (PDF papers, e-mails, messages) in the business language set in Settings > System settings
 * (English or French). The English text is the key, as on the screens: <code>I18n.t("Statement of account")</code>. French lives in
 * <code>resources/i18n/fr.json</code>; a text with no entry shows in English, so nothing is ever blank. A value is written as {0}, {1}.
 *
 * The language is read from the store settings and kept for 10 seconds, so a change in Settings reaches the papers almost at once.
 */
@Component
public class I18n {

    private static final Logger log = LoggerFactory.getLogger(I18n.class);
    private static volatile I18n instance;

    private final StoreSettingsRepository repository;
    private volatile Map<String, String> fr = Map.of();
    private volatile String language = "en";
    private volatile long readAt;

    public I18n(StoreSettingsRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void init() {
        try (InputStream in = I18n.class.getResourceAsStream("/i18n/fr.json")) {
            if (in != null) fr = new ObjectMapper().readValue(in, new TypeReference<HashMap<String, String>>() {});
        } catch (Exception e) {
            log.warn("The French texts could not be read: {}", e.getMessage());
        }
        instance = this;
    }

    private String language() {
        long now = System.currentTimeMillis();
        if (now - readAt > 10_000) {
            try {
                StoreSettings s = repository.findAll().stream().findFirst().orElse(null);
                language = s != null && "fr".equalsIgnoreCase(s.getLanguage()) ? "fr" : "en";
            } catch (Exception e) {
                // keep the last known language
            }
            readAt = now;
        }
        return language;
    }

    // ---- static entry points, so that static helpers of the PDF builders can use them too

    public static boolean french() {
        I18n i = instance;
        return i != null && "fr".equals(i.language());
    }

    /** The text in the business language; the English text when there is no French for it. */
    public static String t(String english) {
        if (english == null) return null;
        I18n i = instance;
        if (i == null || !"fr".equals(i.language())) return english;
        String f = i.fr.get(english);
        return f != null ? f : english;
    }

    /** Same, with values: <code>t("Due on {0}", date)</code>. */
    public static String t(String english, Object... values) {
        String s = t(english);
        if (s == null || values == null) return s;
        for (int k = 0; k < values.length; k++) s = s.replace("{" + k + "}", String.valueOf(values[k]));
        return s;
    }

    /** Same as {@link #t(String, Object...)} but in a language given by the caller ("fr" or anything else = English), e.g. the language a customer chose. */
    public static String tIn(String lang, String english, Object... values) {
        if (english == null) return null;
        I18n i = instance;
        String s = english;
        if (i != null && "fr".equalsIgnoreCase(lang)) {
            String f = i.fr.get(english);
            if (f != null) s = f;
        }
        if (values != null) {
            for (int k = 0; k < values.length; k++) s = s.replace("{" + k + "}", String.valueOf(values[k]));
        }
        return s;
    }

    public static Locale locale() {
        return french() ? Locale.FRENCH : Locale.ENGLISH;
    }

    /** Number symbols for papers: the PDF fonts have no narrow no-break space, so French uses the ordinary no-break space. */
    public static DecimalFormatSymbols symbols() {
        DecimalFormatSymbols s = DecimalFormatSymbols.getInstance(locale());
        if (french()) {
            s.setGroupingSeparator(' ');
            s.setDecimalSeparator(',');
        }
        return s;
    }

    public static DateTimeFormatter longDay() {
        return DateTimeFormatter.ofPattern("d MMMM yyyy", locale());
    }

    public static DateTimeFormatter shortDay() {
        return DateTimeFormatter.ofPattern("d MMM yyyy", locale());
    }

    /** A date pattern written for English ("EEEE d MMMM 'at' HH:mm") in the business language: month and day names follow it, and 'at' becomes 'à'. */
    public static DateTimeFormatter pattern(String english) {
        return DateTimeFormatter.ofPattern(french() ? english.replace("'at'", "'à'") : english, locale());
    }
}
