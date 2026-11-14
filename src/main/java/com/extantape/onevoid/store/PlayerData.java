package com.extantape.onevoid.store;

import com.extantape.onevoid.meter.Meter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything OneVoid keeps about one player. Saved to plugins/OneVoid/players/<uuid>.yml. */
public final class PlayerData {
    public final UUID id;
    public final Meter meter = new Meter();

    /** Where the player last rang their way bell from, and the island it was on. Null before the first trip. */
    public Spot lastSpot;
    public int lastIsland = -1;

    /** Reward ids offered by a full meter and not yet chosen. Rolled once, so reopening can't reroll. */
    public final List<String> offer = new ArrayList<>();

    /** Weapons and armour given to the void, serialised, for "the void gives back". */
    public final List<String> pool = new ArrayList<>();

    /** Items owed to the player, by delivery id. Each item carries its delivery id, so a crash can't pay twice. */
    public final Map<String, List<String>> deliveries = new HashMap<>();

    /** Running powers: reward id to the wall-clock time it ends, in epoch milliseconds. */
    public final Map<String, Long> powers = new HashMap<>();

    /** Whether this island's owner lets circle friends with island travel visit. Off until they turn it on. */
    public boolean visitsOpen;

    /** While visiting another island: where to go back to. */
    public Spot travelReturn;

    public PlayerData(UUID id) {
        this.id = id;
    }

    public long powerLeft(String power, long now) {
        Long end = powers.get(power);
        return end == null ? 0 : Math.max(0, end - now);
    }

    /** A saved position, by world name, so it survives the world being unloaded. */
    public record Spot(String world, double x, double y, double z, float yaw, float pitch) {}
}
