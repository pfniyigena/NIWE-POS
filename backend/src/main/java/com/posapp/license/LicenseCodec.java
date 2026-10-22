package com.posapp.license;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posapp.common.ApiException;
import com.posapp.modules.ModuleKey;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads a license file. A file is a small JSON envelope: {@code {"format":"NIWE-LICENSE-1","payload":"<base64>","signature":"<base64>"}}.
 * The payload is the JSON text of the license and the signature is an Ed25519 signature over exactly those payload bytes, so nothing
 * is re-serialised before it is checked. Nothing in the payload is believed until the signature has been verified.
 */
final class LicenseCodec {

    static final String FORMAT = "NIWE-LICENSE-1";
    static final int MAX_LENGTH = 8000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private LicenseCodec() {
    }

    record Payload(String licenseId, long serial, String customer, String installationId, LocalDate issuedAt, LocalDate expiresAt, List<ModuleKey> modules) {
    }

    /** Checks the signature and reads the payload. Every failure is a plain-language {@link ApiException#badRequest}. */
    static Payload open(String text, PublicKey key) {
        if (text == null || text.isBlank()) {
            throw ApiException.badRequest("The license file is empty.");
        }
        if (text.length() > MAX_LENGTH) {
            throw ApiException.badRequest("This does not look like a license file (too large).");
        }
        if (key == null) {
            throw ApiException.badRequest("This build of the application has no license key, so licenses cannot be checked. Ask NIWE for a build that includes it.");
        }
        JsonNode envelope;
        try {
            envelope = JSON.readTree(text);
        } catch (Exception e) {
            throw ApiException.badRequest("This does not look like a license file.");
        }
        if (envelope == null || !envelope.isObject() || !FORMAT.equals(text(envelope, "format"))) {
            throw ApiException.badRequest("This does not look like a NIWE license file.");
        }
        byte[] payloadBytes;
        byte[] signature;
        try {
            payloadBytes = Base64.getDecoder().decode(text(envelope, "payload"));
            signature = Base64.getDecoder().decode(text(envelope, "signature"));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("The license file is damaged.");
        }
        boolean ok;
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(payloadBytes);
            ok = verifier.verify(signature);
        } catch (Exception e) {
            ok = false;
        }
        if (!ok) {
            throw ApiException.badRequest("The license signature is not valid: the file was changed or was not issued by NIWE.");
        }
        return parse(new String(payloadBytes, StandardCharsets.UTF_8));
    }

    private static Payload parse(String json) {
        JsonNode n;
        try {
            n = JSON.readTree(json);
        } catch (Exception e) {
            throw ApiException.badRequest("The license content is damaged.");
        }
        String licenseId = text(n, "licenseId");
        String customer = text(n, "customer");
        String installationId = text(n, "installationId");
        if (licenseId.isBlank() || customer.isBlank() || installationId.isBlank() || !n.path("serial").canConvertToLong() || n.path("serial").asLong() < 1) {
            throw ApiException.badRequest("The license is incomplete.");
        }
        LocalDate issued = date(n, "issuedAt", true);
        LocalDate expires = date(n, "expiresAt", false);
        if (!n.path("modules").isArray()) {
            throw ApiException.badRequest("The license lists no modules.");
        }
        Set<ModuleKey> modules = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (JsonNode m : n.path("modules")) {
            try {
                modules.add(ModuleKey.valueOf(m.asText("").trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                unknown.add(m.asText(""));
            }
        }
        if (!unknown.isEmpty()) {
            throw ApiException.badRequest("This license has modules this version of the application does not know (" + String.join(", ", unknown) + "). Update the application first.");
        }
        return new Payload(licenseId, n.path("serial").asLong(), customer, installationId, issued, expires, List.copyOf(modules));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    private static LocalDate date(JsonNode n, String field, boolean required) {
        String v = text(n, field);
        if (v.isBlank()) {
            if (required) throw ApiException.badRequest("The license is incomplete.");
            return null;
        }
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("The license has a date that cannot be read.");
        }
    }
}
