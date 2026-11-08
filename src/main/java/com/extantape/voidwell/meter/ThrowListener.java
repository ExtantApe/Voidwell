package com.extantape.voidwell.meter;

import com.extantape.voidwell.VoidwellPlugin;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Items thrown into the void charge the thrower's meter once they're really gone: the server removes them
 * for falling out of the world. Picking an item back up first means it was never gone, so it never counts.
 * A death on the island whose drops later fall in counts for the player who died. Items nobody threw or
 * dropped by dying, such as drops from the one block, don't count.
 */
public final class ThrowListener implements Listener {
    private final VoidwellPlugin plugin;

    public ThrowListener(VoidwellPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent e) {
        if (e.getCause() != EntityRemoveEvent.Cause.OUT_OF_WORLD) return;
        if (!(e.getEntity() instanceof Item item)) return;
        if (!plugin.bridge.inWorld(item.getLocation())) return;
        UUID thrower = item.getThrower();
        if (thrower == null) {
            // A death on the island whose drops then fell in: lost to the void by accident, so it counts too.
            String owner = item.getPersistentDataContainer().get(plugin.deaths.dropOwnerKey(), PersistentDataType.STRING);
            if (owner == null) return;
            thrower = UUID.fromString(owner);
        }
        plugin.meter.sacrifice(thrower, List.of(item.getItemStack()), false);
    }

    /** Two players' thrown items never merge, so each counts for whoever threw it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent e) {
        if (!plugin.bridge.inWorld(e.getEntity().getLocation())) return;
        if (!Objects.equals(e.getEntity().getThrower(), e.getTarget().getThrower())) e.setCancelled(true);
    }
}
