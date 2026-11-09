package com.extantape.voidwell;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/** config.yml, read once per load. Bad values fall back to defaults and are listed in {@link #problems}. */
public final class Settings {
    public final int rings, withinTicks, minGapTicks;
    public final int offX, offY, offZ;
    public final Material post;
    public final int labelRange, labelBacking;
    public final int itemLifeSteps;
    public final double full, dailyCap, repeatFactor, minDurability;
    public final boolean featherfall, voidSafe, bountiful, givesBack, travel;
    public final int featherfallMinutes, voidSafeMinutes, bountifulMinutes, travelMinutes;
    public final int givesBackOneIn, consolationPercent;
    public final int inviteSeconds, maxTeam;
    public final boolean saveWorld;
    public final List<String> problems = new ArrayList<>();

    public Settings(FileConfiguration c) {
        rings = clamp(c.getInt("way-bell.rings", 3), 2, 10, "way-bell.rings");
        withinTicks = 20 * clamp(c.getInt("way-bell.within-seconds", 6), 2, 30, "way-bell.within-seconds");
        minGapTicks = clamp(c.getInt("way-bell.min-gap-ticks", 8), 0, 40, "way-bell.min-gap-ticks");
        offX = c.getInt("way-bell.offset.x", 0);
        offY = c.getInt("way-bell.offset.y", 0);
        offZ = c.getInt("way-bell.offset.z", -2);
        Material m = Material.matchMaterial(c.getString("way-bell.post", "CRYING_OBSIDIAN"));
        if (m == null || !m.isBlock() || !m.isSolid()) {
            problems.add("way-bell.post must be a solid block; using CRYING_OBSIDIAN.");
            m = Material.CRYING_OBSIDIAN;
        }
        post = m;
        labelRange = clamp(c.getInt("way-bell.label-range", 12), 2, 64, "way-bell.label-range");
        labelBacking = clamp(c.getInt("way-bell.label-backing", 96), 0, 255, "way-bell.label-backing");
        itemLifeSteps = Math.max(1, Math.round(clamp(c.getInt("item-life-minutes", 30), 5, 240, "item-life-minutes") / 5f));
        full = clampD(c.getDouble("meter.full", 100), 10, 10000, "meter.full");
        dailyCap = clampD(c.getDouble("meter.daily-cap", 200), 0, 100000, "meter.daily-cap");
        repeatFactor = clampD(c.getDouble("meter.repeat-factor", 0.9), 0, 1, "meter.repeat-factor");
        minDurability = clampD(c.getDouble("meter.min-durability-factor", 0.25), 0, 1, "meter.min-durability-factor");
        featherfall = c.getBoolean("rewards.featherfall.enabled", true);
        featherfallMinutes = clamp(c.getInt("rewards.featherfall.minutes", 5), 1, 60, "rewards.featherfall.minutes");
        voidSafe = c.getBoolean("rewards.void-safe.enabled", true);
        voidSafeMinutes = clamp(c.getInt("rewards.void-safe.minutes", 2), 1, 60, "rewards.void-safe.minutes");
        bountiful = c.getBoolean("rewards.bountiful-block.enabled", true);
        bountifulMinutes = clamp(c.getInt("rewards.bountiful-block.minutes", 5), 1, 60, "rewards.bountiful-block.minutes");
        givesBack = c.getBoolean("rewards.void-gives-back.enabled", true);
        givesBackOneIn = clamp(c.getInt("rewards.void-gives-back.one-in", 3), 1, 100, "rewards.void-gives-back.one-in");
        consolationPercent = clamp(c.getInt("rewards.void-gives-back.consolation-percent", 25), 0, 99, "rewards.void-gives-back.consolation-percent");
        travel = c.getBoolean("rewards.island-travel.enabled", true);
        travelMinutes = clamp(c.getInt("rewards.island-travel.minutes", 5), 1, 60, "rewards.island-travel.minutes");
        inviteSeconds = clamp(c.getInt("social.invite-seconds", 60), 10, 600, "social.invite-seconds");
        maxTeam = clamp(c.getInt("social.max-team-size", 4), 1, 20, "social.max-team-size");
        saveWorld = c.getBoolean("crash-safety.save-world", true);
    }

    private int clamp(int v, int lo, int hi, String key) {
        if (v < lo || v > hi) {
            problems.add(key + " must be between " + lo + " and " + hi + "; using " + Math.max(lo, Math.min(hi, v)) + ".");
            return Math.max(lo, Math.min(hi, v));
        }
        return v;
    }

    private double clampD(double v, double lo, double hi, String key) {
        if (v < lo || v > hi) {
            problems.add(key + " must be between " + lo + " and " + hi + ".");
            return Math.max(lo, Math.min(hi, v));
        }
        return v;
    }
}
