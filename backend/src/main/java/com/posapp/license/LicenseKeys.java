package com.posapp.license;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * The public key that licenses are checked against. It is read from the classpath resource {@code license/public-key.b64} inside the
 * application, deliberately not from a setting an administrator could change: the file holds NIWE's Ed25519 public key (X.509 / SubjectPublicKeyInfo,
 * base64); lines starting with {@code #} are comments. Without a key no license can be installed and only the core modules work.
 */
@Component
public class LicenseKeys {

    private static final Logger log = LoggerFactory.getLogger(LicenseKeys.class);
    static final String RESOURCE = "license/public-key.b64";

    private final PublicKey key;

    public LicenseKeys() {
        this.key = load();
    }

    PublicKey publicKey() {
        return key;
    }

    boolean configured() {
        return key != null;
    }

    private static PublicKey load() {
        try {
            ClassPathResource res = new ClassPathResource(RESOURCE);
            if (!res.exists()) {
                log.warn("No license key ({}) in this build: only the core modules will work.", RESOURCE);
                return null;
            }
            String b64;
            try (InputStream in = res.getInputStream()) {
                String all = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                StringBuilder sb = new StringBuilder();
                for (String line : all.split("\\R")) {
                    String t = line.trim();
                    if (!t.isEmpty() && !t.startsWith("#")) sb.append(t);
                }
                b64 = sb.toString();
            }
            if (b64.isEmpty()) {
                log.warn("The license key file {} is empty: only the core modules will work.", RESOURCE);
                return null;
            }
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
        } catch (Exception e) {
            log.error("The license key could not be read: {}", e.getMessage());
            return null;
        }
    }
}
