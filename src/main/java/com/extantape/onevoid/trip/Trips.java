package com.extantape.onevoid.trip;

import com.extantape.onevoid.Text;
import com.extantape.onevoid.OneVoidPlugin;
import com.extantape.onevoid.store.PlayerData;
import com.extantape.onevoid.store.PlayerData.Spot;
import com.extantape.waystation.LandingService;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/** Getting into OneBlock from the Waystation, and back out by the way bell. */
public final class Trips {
    private final OneVoidPlugin plugin;

    public Trips(OneVoidPlugin plugin) {
        this.plugin = plugin;
    }

    /** The spot in front of the OneBlock gate at the Waystation. Asked fresh every time, never cached. */
    public Optional<Location> landing() {
        World w = plugin.bridge.world();
        if (w == null) return Optional.empty();
        LandingService service = Bukkit.getServicesManager().load(LandingService.class);
        if (service == null) return Optional.empty();
        return service.landing(w.getName());
    }

    /** Through the OneBlock gate. First time: a new island. After that: back where they rang the bell. */
    public void enter(Player p) {
        World w = plugin.bridge.world();
        if (w == null) {
            Text.warn(p, "OneBlock isn't set up yet, so there's nowhere to go. Please tell an admin.");
            return;
        }
        int island = plugin.bridge.islandOf(p.getUniqueId());
        if (island < 0) {
            plugin.bridge.join(p);
            afterArrival(p, true);
            return;
        }
        PlayerData d = plugin.store.get(p.getUniqueId());
        Location back = d.lastIsland == island ? toLocation(d.lastSpot) : null;
        if (back != null && safe(back)) {
            p.teleportAsync(back, TeleportCause.PLUGIN).thenAccept(ok -> {
                if (ok) afterArrival(p, false);
            });
        } else {
            // No saved spot, a spot on an island they've since left, or one that's no longer safe:
            // OneBlock puts them on their island's block.
            plugin.bridge.join(p);
            afterArrival(p, false);
        }
    }

    private void afterArrival(Player p, boolean first) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !plugin.bridge.inWorld(p.getLocation())) return;
            int island = plugin.bridge.islandOf(p.getUniqueId());
            plugin.bells.ensure(island);
            if (first) {
                Text.tell(p, "<green>This is your island.</green> Break the block under you and it comes back as "
                        + "something new. Your <aqua>way bell</aqua> is beside it: ring it " + plugin.settings.rings
                        + " times to go back to the Waystation. Sneak and ring it for your void meter and island options.");
            }
            plugin.rewards.deliver(p);
            plugin.meter.remindOffer(p);
        }, 10L);
    }

    /** Rung the bell: save where they are, then go to the Waystation. */
    public void leave(Player p, int island) {
        Optional<Location> dest = landing();
        if (dest.isEmpty()) {
            Text.warn(p, "The Waystation isn't ready right now, so the bell can't take you there. "
                    + "Nothing has changed. Try again in a minute, and tell an admin if it keeps happening.");
            return;
        }
        PlayerData d = plugin.store.get(p.getUniqueId());
        d.lastSpot = standingOn(p) ? toSpot(p.getLocation()) : null;
        d.lastIsland = island;
        plugin.store.save(d);
        p.sendActionBar(Text.mm("<aqua>The way bell takes you to the Waystation…</aqua>"));
        p.teleportAsync(dest.get(), TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!ok) {
                Text.warn(p, "Something stopped the trip to the Waystation. You're still on your island. Try again.");
                return;
            }
            p.saveData();
            // The inventory on disk now includes anything picked up on the island. Save the island too, or a crash
            // would bring those items back on the ground as well: a duplicate.
            World ob = plugin.bridge.world();
            if (plugin.settings.saveWorld && ob != null) ob.save();
            Text.tell(p, "You're at the Waystation. Walk through the OneBlock gate to go back to your way bell, "
                    + "with everything as you left it.");
        });
    }

    private static boolean standingOn(Player p) {
        Block below = p.getLocation().getBlock().getRelative(BlockFace.DOWN);
        return below.getType().isSolid() && p.getFallDistance() < 1;
    }

    /** Feet and head clear, solid ground under them. */
    public static boolean safe(Location l) {
        if (l.getWorld() == null || !l.isChunkLoaded() && !l.getWorld().loadChunk(l.getBlockX() >> 4, l.getBlockZ() >> 4, false)) return false;
        Block feet = l.getBlock();
        return feet.isPassable() && !feet.isLiquid() && feet.getRelative(BlockFace.UP).isPassable()
                && feet.getRelative(BlockFace.DOWN).getType().isSolid();
    }

    public static Spot toSpot(Location l) {
        return new Spot(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public static Location toLocation(Spot s) {
        if (s == null) return null;
        World w = Bukkit.getWorld(s.world());
        return w == null ? null : new Location(w, s.x(), s.y(), s.z(), s.yaw(), s.pitch());
    }
}
