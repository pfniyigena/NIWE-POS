package com.posapp.propertymgmt;

import com.posapp.common.ApiException;

import java.math.BigDecimal;

/** Small checks shared by the Property Management services. */
final class PmUtil {

    private PmUtil() {
    }

    /** Trims a text; empty becomes null; too long is refused. */
    static String clean(String s, int max, String what) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest(what + " can be at most " + max + " characters.");
        return t.isEmpty() ? null : t;
    }

    /** A text that must be there. */
    static String required(String s, int max, String what) {
        String t = clean(s, max, what);
        if (t == null) throw ApiException.badRequest(what + " is needed.");
        return t;
    }

    /** The last nine digits of a phone number, so 0772 123 456 and +256 772 123 456 count as the same. */
    static String digits(String phone) {
        if (phone == null) return null;
        String d = phone.replaceAll("\\D", "");
        if (d.isEmpty()) return null;
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    static void percent(BigDecimal v, String what) {
        if (v.signum() < 0 || v.compareTo(BigDecimal.valueOf(100)) > 0) throw ApiException.badRequest(what + " is between 0 and 100 percent.");
    }
}
