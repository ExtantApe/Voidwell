package com.extantape.onevoid.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Where each island's way bell is, in plugins/OneVoid/islands.yml. Island ids are OneBlock's. */
public final class IslandStore {
    public record BellPos(int x, int y, int z) {}

    private final Path file;
    private final Logger log;
    private final Map<Integer, BellPos> bells = new HashMap<>();
    private final Map<BellPos, Integer> byPos = new HashMap<>();

    public IslandStore(Path file, Logger log) {
        this.file = file;
        this.log = log;
        load();
    }

    public BellPos bell(int island) {
        return bells.get(island);
    }

    public Integer islandWithBellAt(int x, int y, int z) {
        return byPos.get(new BellPos(x, y, z));
    }

    public boolean isBell(int x, int y, int z) {
        return byPos.containsKey(new BellPos(x, y, z));
    }

    /** True for the bell or the post under it. */
    public boolean isBellOrPost(int x, int y, int z) {
        return isBell(x, y, z) || isBell(x, y + 1, z);
    }

    public Map<Integer, BellPos> all() {
        return bells;
    }

    public void put(int island, BellPos pos) {
        BellPos old = bells.put(island, pos);
        if (old != null) byPos.remove(old);
        byPos.put(pos, island);
        save();
    }

    public void remove(int island) {
        BellPos old = bells.remove(island);
        if (old != null) byPos.remove(old);
        save();
    }

    private void load() {
        if (!Files.exists(file)) return;
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            log.severe("Couldn't read " + file + ": " + e.getMessage() + ". Bells will be placed again where missing.");
            return;
        }
        ConfigurationSection s = y.getConfigurationSection("bells");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            ConfigurationSection b = s.getConfigurationSection(k);
            if (b == null) continue;
            BellPos p = new BellPos(b.getInt("x"), b.getInt("y"), b.getInt("z"));
            int id = Integer.parseInt(k);
            bells.put(id, p);
            byPos.put(p, id);
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(java.util.List.of("Where each OneBlock island's way bell is, by OneBlock island id."));
        for (var e : bells.entrySet()) {
            y.set("bells." + e.getKey() + ".x", e.getValue().x());
            y.set("bells." + e.getKey() + ".y", e.getValue().y());
            y.set("bells." + e.getKey() + ".z", e.getValue().z());
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("islands.yml.tmp");
            Files.writeString(tmp, y.saveToString(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.severe("Couldn't save " + file + ": " + e.getMessage());
        }
    }
}
