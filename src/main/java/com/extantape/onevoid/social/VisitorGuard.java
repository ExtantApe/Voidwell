package com.extantape.onevoid.social;

import com.extantape.onevoid.Text;
import com.extantape.onevoid.OneVoidPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * On someone else's island, a player can look but not touch: no breaking, placing, opening, hurting or
 * picking up what isn't theirs. Players can still pick up items they threw, and their own death drops.
 * Admins with onevoid.admin aren't held back.
 */
public final class VisitorGuard implements Listener {
    private final OneVoidPlugin plugin;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public VisitorGuard(OneVoidPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean guarded(Player p) {
        return !p.hasPermission("onevoid.admin") && plugin.social.isVisiting(p);
    }

    private void stop(Player p, Cancellable e) {
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(p.getUniqueId());
        if (last != null && now - last < 3000) return;
        lastNotice.put(p.getUniqueId(), now);
        Text.tell(p, "You're visiting this island. You can look around, but only its own players can change things here.");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (guarded(e.getPlayer())) stop(e.getPlayer(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (guarded(e.getPlayer())) stop(e.getPlayer(), e);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_AIR) return;
        if (e.getClickedBlock() != null && plugin.islands.isBell(e.getClickedBlock().getX(), e.getClickedBlock().getY(), e.getClickedBlock().getZ())) return;
        if (guarded(e.getPlayer())) {
            if (e.getAction() == Action.PHYSICAL) e.setCancelled(true);
            else stop(e.getPlayer(), e);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntity(PlayerInteractEntityEvent e) {
        if (guarded(e.getPlayer())) stop(e.getPlayer(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent e) {
        Player p = e.getDamager() instanceof Player d ? d
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player s ? s : null;
        if (p != null && guarded(p)) stop(p, e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHanging(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player p && guarded(p)) stop(p, e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (guarded(e.getPlayer())) stop(e.getPlayer(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (guarded(e.getPlayer())) stop(e.getPlayer(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || !guarded(p)) return;
        Item item = e.getItem();
        if (p.getUniqueId().equals(item.getThrower())) return;
        String owner = item.getPersistentDataContainer().get(plugin.deaths.dropOwnerKey(), PersistentDataType.STRING);
        if (p.getUniqueId().toString().equals(owner)) return;
        e.setCancelled(true);
    }

    public void forget(UUID id) {
        lastNotice.remove(id);
    }
}
