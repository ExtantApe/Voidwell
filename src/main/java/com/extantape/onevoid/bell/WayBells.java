package com.extantape.onevoid.bell;

import com.extantape.onevoid.Text;
import com.extantape.onevoid.OneVoidPlugin;
import com.extantape.onevoid.store.IslandStore.BellPos;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bell;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

/**
 * Places each island's way bell, on a post, and keeps a label floating over it. The bell never replaces a
 * block: if the configured spot is taken, the nearest free one is used, and failing that, a spot higher up.
 * Labels are display entities that are never saved, so they can't be left behind; a pass every two seconds
 * redraws any that are missing near a player.
 */
public final class WayBells {
    private final OneVoidPlugin plugin;
    private final Map<Integer, TextDisplay> labels = new HashMap<>();

    public WayBells(OneVoidPlugin plugin) {
        this.plugin = plugin;
    }

    /** Makes sure the island has a bell, placing one if needed. Returns where it is, or null if it can't. */
    public BellPos ensure(int island) {
        World w = plugin.bridge.world();
        if (w == null || island < 0) return null;
        BellPos pos = plugin.islands.bell(island);
        if (pos != null && w.getBlockAt(pos.x(), pos.y(), pos.z()).getType() == Material.BELL) return pos;
        Location center = plugin.bridge.oneBlock(island);
        if (!center.isChunkLoaded()) return pos;
        Block spot = findSpot(center);
        if (spot == null) {
            plugin.getLogger().warning("No room for a way bell on island " + island + ".");
            return null;
        }
        spot.getRelative(BlockFace.DOWN).setType(plugin.settings.post, false);
        spot.setType(Material.BELL, false);
        if (spot.getBlockData() instanceof Bell bell) {
            bell.setAttachment(Bell.Attachment.FLOOR);
            bell.setFacing(facingToward(spot, center));
            spot.setBlockData(bell, false);
        }
        BellPos placed = new BellPos(spot.getX(), spot.getY(), spot.getZ());
        plugin.islands.put(island, placed);
        removeLabel(island);
        return placed;
    }

    private Block findSpot(Location center) {
        World w = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        var s = plugin.settings;
        // The configured spot first, then rings around the one block, from near to far, at the island's level
        // and a little above. The bell sits one block over its post.
        int[][] first = {{s.offX, s.offY, s.offZ}};
        for (int[] o : first) {
            Block b = w.getBlockAt(cx + o[0], cy + o[1] + 1, cz + o[2]);
            if (free(b, cx, cz)) return b;
        }
        for (int dy = 0; dy <= 3; dy++)
            for (int r = 1; r <= 4; r++)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        Block b = w.getBlockAt(cx + dx, cy + dy + 1, cz + dz);
                        if (free(b, cx, cz)) return b;
                    }
        for (int y = cy + 5; y < w.getMaxHeight() - 2; y++) {
            Block b = w.getBlockAt(cx + s.offX, y, cz + s.offZ);
            if (free(b, cx, cz)) return b;
        }
        return null;
    }

    private static boolean free(Block bell, int cx, int cz) {
        if (bell.getX() == cx && bell.getZ() == cz) return false; // never over the one block
        Block post = bell.getRelative(BlockFace.DOWN);
        return post.getType().isAir() && bell.getType().isAir() && bell.getRelative(BlockFace.UP).getType().isAir();
    }

    private static BlockFace facingToward(Block from, Location to) {
        int dx = to.getBlockX() - from.getX(), dz = to.getBlockZ() - from.getZ();
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? BlockFace.EAST : BlockFace.WEST;
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /** Redraws labels over the bells near players in the OneBlock world. */
    public void tickLabels() {
        World w = plugin.bridge.world();
        if (w == null) return;
        java.util.Set<Integer> near = new java.util.HashSet<>();
        for (Player p : w.getPlayers()) {
            int island = plugin.bridge.islandAt(p.getLocation());
            BellPos pos = plugin.islands.bell(island);
            if (pos == null) continue;
            if (p.getLocation().distanceSquared(new Location(w, pos.x(), pos.y(), pos.z())) > 48 * 48) continue;
            near.add(island);
            TextDisplay label = labels.get(island);
            if (label == null || !label.isValid()) labels.put(island, spawnLabel(w, pos));
        }
        labels.entrySet().removeIf(e -> {
            if (near.contains(e.getKey())) return false;
            e.getValue().remove();
            return true;
        });
    }

    private TextDisplay spawnLabel(World w, BellPos pos) {
        Location at = new Location(w, pos.x() + 0.5, pos.y() + 1.35, pos.z() + 0.5);
        int rings = plugin.settings.rings;
        return w.spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(Text.mm("<aqua><bold>Way bell</bold></aqua>\n<white>Ring " + rings
                    + " times to go to the Waystation</white>\n<gray>Sneak and ring for island options</gray>"));
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setViewRange(plugin.settings.labelRange / 64f);
            d.setBackgroundColor(Color.fromARGB(plugin.settings.labelBacking, 0, 0, 0));
            d.setDefaultBackground(plugin.settings.labelBacking == 0);
        });
    }

    private void removeLabel(int island) {
        TextDisplay old = labels.remove(island);
        if (old != null) old.remove();
    }

    public void removeAllLabels() {
        labels.values().forEach(TextDisplay::remove);
        labels.clear();
    }
}
