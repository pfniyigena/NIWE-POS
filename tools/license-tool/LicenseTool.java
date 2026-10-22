import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * NIWE ERP license tool. For NIWE only: it holds the private key and must never be shipped to a customer.
 * Needs only Java 17 or later and runs straight from the source, nothing to build:
 *
 *   java LicenseTool.java keygen   [--dir <folder>]
 *   java LicenseTool.java issue    --key <private-key file> --customer "<name>" --installation <id> --modules POS,HOTEL,... [--expires yyyy-mm-dd] [--out <file>] [--register <csv>]
 *   java LicenseTool.java verify   --pub <public-key file> <license file>
 *   java LicenseTool.java modules
 *
 * The module list below must match ModuleKey.java in the application (the backend tests check that they do).
 */
public class LicenseTool {

    static final String FORMAT = "NIWE-LICENSE-1";

    /** key -> the modules it needs. Core modules (SALES, PURCHASES, ACCOUNTING) are always included by the application and are not listed in a license. */
    static final Map<String, List<String>> MODULES = new LinkedHashMap<>();
    static {
        MODULES.put("POS", List.of());
        MODULES.put("HOTEL", List.of("POS", "RESTAURANT"));
        MODULES.put("RESTAURANT", List.of());
        MODULES.put("MANUFACTURING", List.of());
        MODULES.put("SUBSCRIPTIONS", List.of());
        MODULES.put("HELPDESK", List.of());
        MODULES.put("EXPENSES", List.of());
        MODULES.put("ASSETS", List.of());
        MODULES.put("INSTALLMENTS", List.of());
        MODULES.put("TIME_TRACKING", List.of());
        MODULES.put("WORK_ORDERS", List.of("TIME_TRACKING"));
        MODULES.put("BOOKING", List.of());
        MODULES.put("LAW_FIRM", List.of("TIME_TRACKING"));
        MODULES.put("REAL_ESTATE", List.of());
        MODULES.put("PROPERTY_MANAGEMENT", List.of());
        MODULES.put("EMPLOYEES", List.of());
        MODULES.put("ORG_STRUCTURE", List.of("EMPLOYEES"));
        MODULES.put("ATTENDANCE", List.of("EMPLOYEES"));
        MODULES.put("CONTRACTS", List.of("EMPLOYEES"));
        MODULES.put("PAYROLL", List.of("EMPLOYEES", "CONTRACTS", "ATTENDANCE"));
    }
    static final Set<String> CORE = Set.of("SALES", "PURCHASES", "ACCOUNTING");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) usage(null);
        try {
            switch (args[0]) {
                case "keygen" -> keygen(opts(args, 1));
                case "issue" -> issue(opts(args, 1));
                case "verify" -> verify(opts(args, 1));
                case "modules" -> modules();
                default -> usage("Unknown command: " + args[0]);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    // ---- keygen ----

    static void keygen(Map<String, String> o) throws Exception {
        Path dir = Path.of(o.getOrDefault("dir", "."));
        Files.createDirectories(dir);
        Path priv = dir.resolve("niwe-license-private.key");
        Path pub = dir.resolve("niwe-license-public.b64");
        if (Files.exists(priv) || Files.exists(pub)) {
            throw new IllegalArgumentException("A key already exists in " + dir.toAbsolutePath() + ". Refusing to overwrite it: a new key would make every issued license invalid.");
        }
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String privB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        String pubB64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        Files.writeString(priv, privB64 + "\n", StandardOpenOption.CREATE_NEW);
        Files.writeString(pub, pubB64 + "\n", StandardOpenOption.CREATE_NEW);
        System.out.println("Private key (KEEP SECRET, back it up in two places, never put it in git or on a customer's machine):\n  " + priv.toAbsolutePath());
        System.out.println("Public key file:\n  " + pub.toAbsolutePath());
        System.out.println("\nPut this single line into backend/src/main/resources/license/public-key.b64 (below the comments) and rebuild the application:\n");
        System.out.println(pubB64);
    }

    // ---- issue ----

    static void issue(Map<String, String> o) throws Exception {
        String customer = need(o, "customer");
        String installation = need(o, "installation");
        PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(Files.readString(Path.of(need(o, "key"))).trim())));
        LocalDate issued = LocalDate.now();
        LocalDate expires = null;
        if (o.containsKey("expires")) {
            expires = LocalDate.parse(o.get("expires"));
            if (!expires.isAfter(issued)) throw new IllegalArgumentException("--expires must be a date in the future.");
        }
        Set<String> chosen = new LinkedHashSet<>();
        for (String m : need(o, "modules").split(",")) {
            String k = m.trim().toUpperCase();
            if (k.isEmpty() || CORE.contains(k)) continue;
            if (!MODULES.containsKey(k)) throw new IllegalArgumentException("Unknown module: " + k + ". Run 'modules' to see the list.");
            chosen.add(k);
        }
        Set<String> all = new LinkedHashSet<>(chosen);
        for (String k : chosen) {
            for (String need : MODULES.get(k)) {
                if (all.add(need)) System.out.println("Added " + need + " because " + k + " needs it.");
            }
        }
        if (all.isEmpty()) System.out.println("Note: no optional modules, so this license only confirms the core modules.");
        Path register = Path.of(o.getOrDefault("register", "licenses.csv"));
        long serial = 1;
        List<String> lines = Files.exists(register) ? Files.readAllLines(register) : new ArrayList<>();
        for (String line : lines) {
            String[] c = line.split(";", -1);
            if (c.length > 3 && c[3].equals(installation)) {
                try { serial = Math.max(serial, Long.parseLong(c[1]) + 1); } catch (NumberFormatException ignored) { }
            }
        }
        String licenseId = "LIC-" + issued.toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        StringBuilder p = new StringBuilder("{");
        p.append("\"licenseId\":").append(q(licenseId)).append(",\"serial\":").append(serial)
                .append(",\"customer\":").append(q(customer)).append(",\"installationId\":").append(q(installation))
                .append(",\"issuedAt\":").append(q(issued.toString()));
        if (expires != null) p.append(",\"expiresAt\":").append(q(expires.toString()));
        p.append(",\"modules\":[");
        int i = 0;
        for (String m : all) p.append(i++ > 0 ? "," : "").append(q(m));
        p.append("]}");
        byte[] payload = p.toString().getBytes(StandardCharsets.UTF_8);
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(key);
        s.update(payload);
        String envelope = "{\"format\":\"" + FORMAT + "\",\"payload\":\"" + Base64.getEncoder().encodeToString(payload) + "\",\"signature\":\"" + Base64.getEncoder().encodeToString(s.sign()) + "\"}";
        String slug = customer.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        Path out = Path.of(o.getOrDefault("out", slug + "-" + serial + ".niwelic"));
        Files.writeString(out, envelope + "\n", StandardOpenOption.CREATE_NEW);
        lines.add(String.join(";", licenseId, String.valueOf(serial), customer, installation, issued.toString(), expires == null ? "" : expires.toString(), String.join(",", all)));
        Files.write(register, lines);
        System.out.println("License " + licenseId + " number " + serial + " for " + customer + " written to " + out.toAbsolutePath());
        System.out.println("Modules: " + (all.isEmpty() ? "(core only)" : String.join(", ", all)) + (expires == null ? "; no expiry" : "; expires " + expires));
        System.out.println("Register updated: " + register.toAbsolutePath());
    }

    // ---- verify ----

    static void verify(Map<String, String> o) throws Exception {
        String file = o.get("_arg");
        if (file == null) throw new IllegalArgumentException("Give the license file to check.");
        PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(Files.readString(Path.of(need(o, "pub"))).trim())));
        String text = Files.readString(Path.of(file));
        String payloadB64 = field(text, "payload");
        String sigB64 = field(text, "signature");
        if (!FORMAT.equals(field(text, "format")) || payloadB64 == null || sigB64 == null) throw new IllegalArgumentException("Not a NIWE license file.");
        byte[] payload = Base64.getDecoder().decode(payloadB64);
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(key);
        s.update(payload);
        boolean ok = s.verify(Base64.getDecoder().decode(sigB64));
        System.out.println(new String(payload, StandardCharsets.UTF_8));
        System.out.println(ok ? "SIGNATURE VALID" : "SIGNATURE INVALID");
        if (!ok) System.exit(1);
    }

    static void modules() {
        System.out.println("Core (always included, not listed in a license): " + String.join(", ", CORE));
        MODULES.forEach((k, r) -> System.out.println("  " + k + (r.isEmpty() ? "" : "   (needs " + String.join(", ", r) + ")")));
    }

    // ---- helpers ----

    static Map<String, String> opts(String[] a, int from) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = from; i < a.length; i++) {
            if (a[i].startsWith("--")) {
                if (i + 1 >= a.length) throw new IllegalArgumentException("Missing value for " + a[i]);
                m.put(a[i].substring(2), a[++i]);
            } else {
                m.put("_arg", a[i]);
            }
        }
        return m;
    }

    static String need(Map<String, String> o, String k) {
        String v = o.get(k);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("--" + k + " is required.");
        return v;
    }

    static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static String field(String json, String name) {
        int i = json.indexOf("\"" + name + "\"");
        if (i < 0) return null;
        int c = json.indexOf(':', i);
        int a = json.indexOf('"', c + 1);
        int b = json.indexOf('"', a + 1);
        return a < 0 || b < 0 ? null : json.substring(a + 1, b);
    }

    static void usage(String problem) {
        if (problem != null) System.err.println(problem + "\n");
        System.err.println("""
                NIWE ERP license tool (NIWE only)
                  java LicenseTool.java keygen [--dir <folder>]
                  java LicenseTool.java issue --key <private-key file> --customer "<name>" --installation <id> --modules POS,HOTEL [--expires yyyy-mm-dd] [--out <file>] [--register <csv>]
                  java LicenseTool.java verify --pub <public-key file> <license file>
                  java LicenseTool.java modules""");
        System.exit(problem == null ? 0 : 2);
    }
}
