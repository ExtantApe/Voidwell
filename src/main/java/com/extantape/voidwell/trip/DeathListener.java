package com.extantape.voidwell.trip;

import com.extantape.voidwell.Text;
import com.extantape.voidwell.VoidwellPlugin;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Deaths in the OneBlock world. In the void, the items go to the void meter. On an island, they drop as normal
 * and last longer. Either way the player respawns outside the OneBlock gate at the Waystation, bed or not.
 */
public final class DeathListener implements Listener {
    private final VoidwellPlugin plugin;
    private final Set<UUID> diedHere = new HashSet<>();
    private final NamespacedKey lives, dropOwner;
    /** Who just died where, so the items that spawn from that death this tick can be marked as theirs. */
    private UUID dying;
    private Location dyingAt;

    public DeathListener(VoidwellPlugin plugin) {
        this.plugin = plugin;
        this.lives = new NamespacedKey(plugin, "despawn-count");
        this.dropOwner = new NamespacedKey(plugin, "death-drop-of");
    }

    public NamespacedKey dropOwnerKey() {
        return dropOwner;
    }

    private static boolean voidDeath(Player p) {
        EntityDamageEvent last = p.getLastDamageCause();
        return (last != null && last.getCause() == EntityDamageEvent.DamageCause.VOID)
                || p.getLocation().getY() < p.getWorld().getMinHeight();
    }

    /** Runs before Lantern Graves (HIGHEST), so a void death's items are never also put in a grave. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!plugin.bridge.inWorld(p.getLocation())) return;
        diedHere.add(p.getUniqueId());
        if (voidDeath(p) && !e.getKeepInventory()) {
            plugin.meter.sacrifice(p.getUniqueId(), new java.util.ArrayList<ItemStack>(e.getDrops()), true);
            e.getDrops().clear();
        } else {
            dying = p.getUniqueId();
            dyingAt = p.getLocation();
            Bukkit.getScheduler().runTask(plugin, () -> dying = null);
        }
    }

    /** After the drops exist: save the player, and the world, so a crash now can't duplicate or lose them. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!diedHere.contains(p.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) p.saveData();
            World w = plugin.bridge.world();
            if (plugin.settings.saveWorld && w != null) w.save();
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDropSpawn(ItemSpawnEvent e) {
        if (dying == null || dyingAt == null) return;
        Item item = e.getEntity();
        if (item.getWorld() != dyingAt.getWorld() || item.getLocation().distanceSquared(dyingAt) > 9) return;
        item.getPersistentDataContainer().set(dropOwner, PersistentDataType.STRING, dying.toString());
    }

    /** Respawn outside the OneBlock gate, even with a bed or anchor set. Runs after OneBlock's own handler. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!diedHere.remove(p.getUniqueId())) return;
        Optional<Location> gate = plugin.trips.landing();
        if (gate.isPresent()) {
            e.setRespawnLocation(gate.get());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                p.saveData();
                Text.tell(p, "You're back at the Waystation. Walk through the OneBlock gate to return to your island.");
            });
        } else {
            // Waystation can't say where its gate is. Respawning on the island at least keeps OneBlock's inventory
            // in the OneBlock world. The player is told, rather than left wondering.
            int island = plugin.bridge.islandOf(p.getUniqueId());
            if (island >= 0) e.setRespawnLocation(plugin.bridge.oneBlock(island).add(0.5, 1.2, 0.5));
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) Text.warn(p, "The Waystation isn't answering, so you're back on your island instead.");
            });
        }
    }

    /** Items in the OneBlock world last {@code item-life-minutes}: each despawn is refused until then. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent e) {
        if (!plugin.bridge.inWorld(e.getLocation())) return;
        // Items nobody can pick up are for show, such as the copy /give makes and removes at once.
        // Keeping those would turn them into real, duplicate items.
        if (e.getEntity().getPickupDelay() >= Short.MAX_VALUE || e.getEntity().isUnlimitedLifetime()) return;
        var pdc = e.getEntity().getPersistentDataContainer();
        int done = pdc.getOrDefault(lives, PersistentDataType.INTEGER, 0) + 1;
        if (done < plugin.settings.itemLifeSteps) {
            pdc.set(lives, PersistentDataType.INTEGER, done);
            e.setCancelled(true); // the server starts the item's five minutes again
        }
    }
}
