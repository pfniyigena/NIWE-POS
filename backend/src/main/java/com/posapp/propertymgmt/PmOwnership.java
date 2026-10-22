package com.posapp.propertymgmt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Who owns a property and how much each one has. A property's ownership is a list of {@link Split}s, oldest first; the split that applies on a day is the newest one that started on or
 * before it (the oldest one covers every day before it, too). Money is shared by {@link #part}: every co-owner but the lead gets their rounded percentage and the lead gets the rest, so
 * the parts always add up to the whole.
 */
public final class PmOwnership {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private PmOwnership() {
    }

    public record Share(Long ownerId, BigDecimal percent, boolean lead) {
    }

    /** {@code from} empty = from the beginning. */
    public record Split(LocalDate from, List<Share> shares) {
        public Share lead() {
            return shares.stream().filter(Share::lead).findFirst().orElse(shares.get(0));
        }

        public Share of(Long ownerId) {
            return shares.stream().filter(x -> x.ownerId().equals(ownerId)).findFirst().orElse(null);
        }
    }

    /** The splits of one property, oldest first. */
    public static List<Split> build(List<PmPropertyOwner> rows) {
        Map<LocalDate, List<PmPropertyOwner>> by = new TreeMap<>(Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()));
        for (PmPropertyOwner r : rows) by.computeIfAbsent(r.getValidFrom(), k -> new ArrayList<>()).add(r);
        List<Split> out = new ArrayList<>();
        for (Map.Entry<LocalDate, List<PmPropertyOwner>> e : by.entrySet()) {
            List<Share> shares = e.getValue().stream().sorted(Comparator.comparing(PmPropertyOwner::getId))
                    .map(r -> new Share(r.getOwnerId(), r.getSharePercent(), r.isLeadOwner())).toList();
            out.add(new Split(e.getKey(), shares));
        }
        return out;
    }

    /** The split in force on a day (the oldest one for days before it). */
    public static Split at(List<Split> splits, LocalDate day) {
        if (splits == null || splits.isEmpty()) return null;
        Split found = splits.get(0);
        for (Split sp : splits) {
            if (sp.from() == null || !sp.from().isAfter(day)) found = sp;
        }
        return found;
    }

    public static Split latest(List<Split> splits) {
        return splits == null || splits.isEmpty() ? null : splits.get(splits.size() - 1);
    }

    /** The part of {@code amount} that belongs to one owner under a split; zero if the owner is not in it. */
    public static BigDecimal part(Split sp, Long ownerId, BigDecimal amount) {
        if (sp == null || amount == null) return BigDecimal.ZERO;
        Share mine = sp.of(ownerId);
        if (mine == null) return BigDecimal.ZERO;
        if (sp.shares().size() == 1) return amount;
        Share lead = sp.lead();
        if (!mine.ownerId().equals(lead.ownerId())) return rounded(amount, mine.percent());
        BigDecimal others = BigDecimal.ZERO;
        for (Share x : sp.shares()) if (!x.ownerId().equals(lead.ownerId())) others = others.add(rounded(amount, x.percent()));
        return amount.subtract(others);
    }

    private static BigDecimal rounded(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
