package com.extantape.onevoid.meter;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * One player's void meter. Pure logic: the caller works out each item's unit worth, and this applies the
 * repeat factor, the daily cap and the meter's size. Saved and loaded by the player store.
 */
public final class Meter {
    public double points;
    public LocalDate day = LocalDate.MIN;
    public double chargedToday;
    public final Map<String, Integer> typesToday = new HashMap<>();

    /** What one charge did, for the action bar. */
    public record Charge(double added, double wasted, boolean capped, boolean becameFull) {}

    /**
     * Charges the meter with {@code count} items of {@code type}, each worth {@code unit} before repeats.
     * @param full   points for a full meter; the meter holds up to twice this
     * @param cap    the daily cap
     * @param factor the repeat factor
     */
    public Charge charge(String type, double unit, int count, LocalDate today, double full, double cap, double factor) {
        rollDay(today);
        int before = typesToday.getOrDefault(type, 0);
        typesToday.put(type, before + count);
        double worth = ValueTable.repeated(unit, count, before, factor);
        double room = Math.max(0, Math.min(cap - chargedToday, full * 2 - points));
        double added = Math.min(worth, room);
        boolean wasFull = points >= full;
        points += added;
        chargedToday += added;
        boolean capped = chargedToday >= cap - 1e-9 && worth > added;
        return new Charge(added, worth - added, capped, !wasFull && points >= full);
    }

    public boolean isFull(double full) {
        return points >= full;
    }

    /** Spends one full meter on a reward. Anything over full carries on. */
    public void spend(double full) {
        points = Math.max(0, points - full);
    }

    public void rollDay(LocalDate today) {
        if (!today.equals(day)) {
            day = today;
            chargedToday = 0;
            typesToday.clear();
        }
    }
}
