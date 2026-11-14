package com.extantape.onevoid.bell;

import com.extantape.onevoid.Text;
import com.extantape.onevoid.OneVoidPlugin;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * The way bell. Ringing it the configured number of times, each a separate right-click by one of the island's
 * own players, sends them to the Waystation. Nothing else counts: not projectiles, not redstone, not other
 * players, not hitting it. Nothing can break it, move it or blow it up.
 */
public final class BellListener implements Listener {
    private final OneVoidPlugin plugin;
    private final Map<UUID, Rings> rings = new HashMap<>();
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    private record Rings(int island, int count, long firstTick, long lastTick) {}

    public BellListener(OneVoidPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean protectedBlock(Block b) {
        return plugin.bridge.inWorld(b.getLocation()) && plugin.islands.isBellOrPost(b.getX(), b.getY(), b.getZ());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.BELL || !protectedBlock(b)) return;
        Integer island = plugin.islands.islandWithBellAt(b.getX(), b.getY(), b.getZ());
        if (island == null) return;
        Player p = e.getPlayer();
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) return; // hitting it never counts
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        boolean member = plugin.bridge.islandOf(p.getUniqueId()) == island;
        if (p.isSneaking()) {
            e.setCancelled(true);
            rings.remove(p.getUniqueId());
            if (member) plugin.menus.island(p, island);
            else plugin.menus.visitor(p, island);
            return;
        }
        if (!member) {
            e.setCancelled(true);
            if (noticeDue(p)) Text.tell(p, "This is another island's way bell. Only its own players can ring it. "
                    + "Sneak and ring it for your options.");
            return;
        }
        // Let the bell ring normally: the sound is part of the feedback.
        long now = p.getWorld().getGameTime();
        var s = plugin.settings;
        Rings r = rings.get(p.getUniqueId());
        if (r != null && r.island == island && now - r.lastTick < s.minGapTicks) return; // a held button
        if (r == null || r.island != island || now - r.firstTick > s.withinTicks) r = new Rings(island, 0, now, now);
        r = new Rings(island, r.count + 1, r.firstTick, now);
        float pitch = 0.6f + 0.4f * r.count;
        p.playSound(b.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, pitch);
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, b.getLocation().add(0.5, 0.6, 0.5), 15 * r.count, 0.3, 0.4, 0.3, 0.02);
        if (r.count < s.rings) {
            rings.put(p.getUniqueId(), r);
            int left = s.rings - r.count;
            p.sendActionBar(Text.mm("<aqua>Way bell: ring " + r.count + " of " + s.rings + ".</aqua> <white>Ring "
                    + (left == 1 ? "once more" : left + " more times") + " to go to the Waystation.</white>"));
            return;
        }
        rings.remove(p.getUniqueId());
        plugin.trips.leave(p, island);
    }

    private boolean noticeDue(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(p.getUniqueId());
        if (last != null && now - last < 3000) return false;
        lastNotice.put(p.getUniqueId(), now);
        return true;
    }

    public void forget(UUID id) {
        rings.remove(id);
        lastNotice.remove(id);
    }

    // Protection. Nobody breaks or moves a way bell, operators included. /onevoid bell puts it right.

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!protectedBlock(e.getBlock())) return;
        e.setCancelled(true);
        if (noticeDue(e.getPlayer())) Text.tell(e.getPlayer(), "Way bells can't be broken. Ring it instead.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        // Keeps the space over the bell clear, so it can always be reached and rung.
        Block b = e.getBlock();
        if (plugin.bridge.inWorld(b.getLocation()) && plugin.islands.isBell(b.getX(), b.getY() - 1, b.getZ()))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (anyProtected(e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (anyProtected(e.getBlocks())) e.setCancelled(true);
    }

    private boolean anyProtected(List<Block> blocks) {
        for (Block b : blocks) if (protectedBlock(b)) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (protectedBlock(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (protectedBlock(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (protectedBlock(e.getToBlock())) e.setCancelled(true);
    }
}
