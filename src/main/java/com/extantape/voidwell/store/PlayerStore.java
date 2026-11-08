package com.extantape.voidwell.store;

import com.extantape.voidwell.store.PlayerData.Spot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Player data, one plain YAML file per player. Every save writes a temporary file and renames it over the old
 * one, so a crash leaves either the old file or the new one, never half of each.
 */
public final class PlayerStore {
    private final Path dir;
    private final Logger log;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerStore(Path dir, Logger log) {
        this.dir = dir;
        this.log = log;
    }

    public PlayerData get(UUID id) {
        return cache.computeIfAbsent(id, this::load);
    }

    public void forget(UUID id) {
        cache.remove(id);
    }

    public Iterable<PlayerData> loaded() {
        return cache.values();
    }

    private Path file(UUID id) {
        return dir.resolve(id + ".yml");
    }

    private PlayerData load(UUID id) {
        PlayerData d = new PlayerData(id);
        Path f = file(id);
        if (!Files.exists(f)) return d;
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(Files.readString(f, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            // Keep the unreadable file for an admin to look at, and don't overwrite it.
            log.severe("Couldn't read " + f + ": " + e.getMessage() + ". Starting this player fresh; the file is kept as .bad.");
            try { Files.copy(f, f.resolveSibling(id + ".yml.bad"), StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) {}
            return d;
        }
        d.meter.points = y.getDouble("meter.points");
        String day = y.getString("meter.day");
        if (day != null) d.meter.day = LocalDate.parse(day);
        d.meter.chargedToday = y.getDouble("meter.charged-today");
        readInts(y.getConfigurationSection("meter.types-today"), d.meter.typesToday);
        d.lastSpot = readSpot(y.getConfigurationSection("last-spot"));
        d.lastIsland = y.getInt("last-island", -1);
        d.offer.addAll(y.getStringList("offer"));
        d.pool.addAll(y.getStringList("pool"));
        ConfigurationSection del = y.getConfigurationSection("deliveries");
        if (del != null) for (String k : del.getKeys(false)) d.deliveries.put(k, new java.util.ArrayList<>(del.getStringList(k)));
        ConfigurationSection pw = y.getConfigurationSection("powers");
        if (pw != null) for (String k : pw.getKeys(false)) d.powers.put(k, pw.getLong(k));
        d.visitsOpen = y.getBoolean("visits-open");
        d.travelReturn = readSpot(y.getConfigurationSection("travel-return"));
        return d;
    }

    /** Writes the player's file now. Called at every moment a crash would matter. */
    public void save(PlayerData d) {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Voidwell player data. Safe to read; edit only with the server stopped."));
        y.set("meter.points", round(d.meter.points));
        if (!d.meter.day.equals(LocalDate.MIN)) y.set("meter.day", d.meter.day.toString());
        y.set("meter.charged-today", round(d.meter.chargedToday));
        for (var e : d.meter.typesToday.entrySet()) y.set("meter.types-today." + e.getKey(), e.getValue());
        writeSpot(y, "last-spot", d.lastSpot);
        y.set("last-island", d.lastIsland);
        y.set("offer", d.offer);
        y.set("pool", d.pool);
        for (var e : d.deliveries.entrySet()) y.set("deliveries." + e.getKey(), e.getValue());
        for (var e : d.powers.entrySet()) y.set("powers." + e.getKey(), e.getValue());
        y.set("visits-open", d.visitsOpen);
        writeSpot(y, "travel-return", d.travelReturn);
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(d.id + ".yml.tmp");
            Files.writeString(tmp, y.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file(d.id), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file(d.id), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.severe("Couldn't save Voidwell data for " + d.id + ": " + e.getMessage());
        }
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static Spot readSpot(ConfigurationSection s) {
        if (s == null || s.getString("world") == null) return null;
        return new Spot(s.getString("world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    private static void writeSpot(YamlConfiguration y, String path, Spot s) {
        if (s == null) return;
        y.set(path + ".world", s.world());
        y.set(path + ".x", s.x());
        y.set(path + ".y", s.y());
        y.set(path + ".z", s.z());
        y.set(path + ".yaw", (double) s.yaw());
        y.set(path + ".pitch", (double) s.pitch());
    }

    private static void readInts(ConfigurationSection s, Map<String, Integer> into) {
        if (s == null) return;
        for (String k : s.getKeys(false)) into.put(k, s.getInt(k));
    }
}
