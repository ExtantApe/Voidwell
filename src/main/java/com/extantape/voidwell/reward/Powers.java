package com.extantape.voidwell.reward;

import com.extantape.voidwell.Text;
import com.extantape.voidwell.VoidwellPlugin;
import com.extantape.voidwell.store.PlayerData;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.potion.PotionEffectType;

/**
 * Running powers. They run on the clock, in real time, so a restart or logging out doesn't stop or stretch
 * them, and each ends with a message saying so.
 */
public final class Powers implements Listener {
    private final VoidwellPlugin plugin;

    public Powers(VoidwellPlugin plugin) {
        this.plugin = plugin;
    }

    /** Every second: end powers that are over, and show what's running. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.store.get(p.getUniqueId());
            if (d.powers.isEmpty()) continue;
            List<String> ended = new ArrayList<>();
            List<String> running = new ArrayList<>();
            for (var e : d.powers.entrySet()) {
                if (e.getValue() <= now) ended.add(e.getKey());
                else {
                    Reward r = Reward.byId(e.getKey());
                    if (r != null) running.add(Rewards.title(r) + " " + Text.minutes(e.getValue() - now));
                }
            }
            for (String id : ended) {
                d.powers.remove(id);
                Reward r = Reward.byId(id);
                if (r == Reward.FEATHERFALL) p.removePotionEffect(PotionEffectType.SLOW_FALLING);
                if (r == Reward.ISLAND_TRAVEL) plugin.social.endTravel(p, "Island travel has ended, so you're back home.");
                if (r != null) Text.tell(p, Rewards.title(r) + " has worn off.");
            }
            if (!ended.isEmpty()) plugin.store.save(d);
            if (!running.isEmpty() && plugin.bridge.inWorld(p.getLocation()))
                p.sendActionBar(Text.mm("<gray>Running:</gray> <aqua>" + String.join("  ·  ", running) + "</aqua>"));
            if (d.powerLeft(Reward.VOID_SAFE.id, now) > 0) catchFalling(p);
        }
    }

    private boolean active(Player p, Reward r) {
        return plugin.store.get(p.getUniqueId()).powerLeft(r.id, System.currentTimeMillis()) > 0;
    }

    /** Void-safe: well below the islands, lift the player back to their island. */
    private void catchFalling(Player p) {
        if (!plugin.bridge.inWorld(p.getLocation())) return;
        int island = plugin.bridge.islandOf(p.getUniqueId());
        if (island < 0) return;
        Location top = plugin.bridge.oneBlock(island);
        if (p.getLocation().getY() > top.getY() - 24) return;
        rescue(p, island);
    }

    private void rescue(Player p, int island) {
        Location to = plugin.bridge.oneBlock(island).add(0.5, 1.2, 0.5);
        p.setFallDistance(0);
        p.teleportAsync(to, TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!ok) return;
            p.setFallDistance(0);
            p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, to, 60, 0.4, 0.8, 0.4, 0.05);
            p.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
            long left = plugin.store.get(p.getUniqueId()).powerLeft(Reward.VOID_SAFE.id, System.currentTimeMillis());
            Text.tell(p, "The void caught you and set you back on your island. Void-safe: " + Text.minutes(left) + " left.");
        });
    }

    /** Void-safe also stops void damage outright, in case the fall was quicker than the check. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVoid(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.VOID || !(e.getEntity() instanceof Player p)) return;
        if (!plugin.bridge.inWorld(p.getLocation()) || !active(p, Reward.VOID_SAFE)) return;
        int island = plugin.bridge.islandOf(p.getUniqueId());
        if (island < 0) return;
        e.setCancelled(true);
        rescue(p, island);
    }

    /** Bountiful block: the player's own one block drops everything twice. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if (!plugin.bridge.inWorld(b.getLocation()) || !active(p, Reward.BOUNTIFUL_BLOCK)) return;
        int island = plugin.bridge.islandOf(p.getUniqueId());
        if (island < 0) return;
        Location one = plugin.bridge.oneBlock(island);
        if (b.getX() != one.getBlockX() || b.getY() != one.getBlockY() || b.getZ() != one.getBlockZ()) return;
        for (Item it : e.getItems())
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), it.getItemStack().clone());
    }
}
