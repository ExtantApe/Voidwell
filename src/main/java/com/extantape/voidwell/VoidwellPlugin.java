package com.extantape.voidwell;

import com.extantape.voidwell.bell.BellListener;
import com.extantape.voidwell.bell.WayBells;
import com.extantape.voidwell.menu.IslandMenus;
import com.extantape.voidwell.menu.MenuListener;
import com.extantape.voidwell.meter.ThrowListener;
import com.extantape.voidwell.meter.VoidMeter;
import com.extantape.voidwell.oneblock.OneBlockBridge;
import com.extantape.voidwell.reward.Powers;
import com.extantape.voidwell.reward.Rewards;
import com.extantape.voidwell.social.Social;
import com.extantape.voidwell.social.VisitorGuard;
import com.extantape.voidwell.store.IslandStore;
import com.extantape.voidwell.store.PlayerData;
import com.extantape.voidwell.store.PlayerStore;
import com.extantape.voidwell.trip.DeathListener;
import com.extantape.voidwell.trip.Trips;
import com.extantape.waystation.WaystationHandOverEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoidwellPlugin extends JavaPlugin implements Listener {
    public Settings settings;
    public final OneBlockBridge bridge = new OneBlockBridge();
    public PlayerStore store;
    public IslandStore islands;
    public WayBells bells;
    public Trips trips;
    public VoidMeter meter;
    public Rewards rewards;
    public Social social;
    public IslandMenus menus;
    public DeathListener deaths;
    private BellListener bellListener;
    private VisitorGuard guard;
    private Powers powers;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig());
        store = new PlayerStore(getDataPath().resolve("players"), getLogger());
        islands = new IslandStore(getDataPath().resolve("islands.yml"), getLogger());
        bells = new WayBells(this);
        trips = new Trips(this);
        meter = new VoidMeter(this);
        rewards = new Rewards(this);
        social = new Social(this);
        menus = new IslandMenus(this);
        deaths = new DeathListener(this);
        bellListener = new BellListener(this);
        guard = new VisitorGuard(this);
        powers = new Powers(this);

        var pm = getServer().getPluginManager();
        for (Listener l : List.of(this, deaths, bellListener, guard, powers, new ThrowListener(this),
                new MenuListener(), new CommandGuard()))
            pm.registerEvents(l, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e ->
                e.registrar().register(VoidwellCommand.build(this), "Your void meter and rewards", List.of()));

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            powers.tick();
            social.tick();
        }, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, bells::tickLabels, 40L, 40L);

        // Once OneBlock has loaded its world (it does so a few seconds after startup), say plainly what isn't right.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (String w : warnings()) getLogger().warning(w);
            for (String p : settings.problems) getLogger().warning("config.yml: " + p);
        }, 300L);
        for (Player p : Bukkit.getOnlinePlayers()) onJoin(p);
    }

    @Override
    public void onDisable() {
        if (bells != null) bells.removeAllLabels();
        if (store != null) for (PlayerData d : store.loaded()) store.save(d);
    }

    public void reload() {
        reloadConfig();
        settings = new Settings(getConfig());
        bells.removeAllLabels();
    }

    /** Problems an admin should fix, in words. Shown at startup and in /voidwell status. */
    public List<String> warnings() {
        List<String> out = new ArrayList<>(bridge.conflicts());
        World w = bridge.world();
        if (trips.landing().isEmpty())
            out.add("Waystation gives no landing spot for the OneBlock world yet. Bell trips and respawns can't reach "
                    + "the station until it does. Is its OneBlock gate set up with hand-over: true?");
        Plugin graves = Bukkit.getPluginManager().getPlugin("LanternGraves");
        if (graves != null && graves.isEnabled() && w != null
                && !graves.getConfig().getStringList("disabled-worlds").contains(w.getName()))
            out.add("Lantern Graves is making graves in the OneBlock world. Add " + w.getName()
                    + " to its disabled-worlds, so island deaths drop items as they should.");
        if (!social.circlesExist())
            out.add("No HouseRules circles exist, so island travel and team invites are off.");
        return out;
    }

    public List<String> health() {
        List<String> out = new ArrayList<>();
        World w = bridge.world();
        out.add("OneBlock world: " + (w == null ? "not set" : w.getName()) + ", way bells placed: " + islands.all().size());
        out.add("Waystation landing: " + trips.landing().map(l -> "ready").orElse("not available"));
        List<String> warn = warnings();
        if (warn.isEmpty()) out.add("No problems found.");
        out.addAll(warn);
        out.addAll(settings.problems);
        return out;
    }

    // Walking through the OneBlock gate.

    @EventHandler(priority = EventPriority.NORMAL)
    public void onHandOver(WaystationHandOverEvent e) {
        World w = bridge.world();
        if (e.isHandled() || w == null || !e.getDestination().equalsIgnoreCase(w.getName())) return;
        e.setHandled(true);
        trips.enter(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        onJoin(e.getPlayer());
    }

    private void onJoin(Player p) {
        PlayerData d = store.get(p.getUniqueId());
        rewards.applyFeatherfall(p, d);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            if (d.travelReturn != null && d.powerLeft(com.extantape.voidwell.reward.Reward.ISLAND_TRAVEL.id, System.currentTimeMillis()) <= 0)
                social.endTravel(p, "Island travel ended while you were away, so you're back home.");
            rewards.deliver(p);
        }, 40L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (bridge.inWorld(e.getPlayer().getLocation()))
            Bukkit.getScheduler().runTaskLater(this, () -> rewards.deliver(e.getPlayer()), 20L);
    }

    // Troubleshooting: with -Dvoidwell.trace=true, log every teleport and respawn with the plugin code that caused it.
    @EventHandler(priority = EventPriority.MONITOR)
    public void traceTp(org.bukkit.event.player.PlayerTeleportEvent e) {
        if (!Boolean.getBoolean("voidwell.trace")) return;
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement el : new Throwable().getStackTrace()) { String c = el.getClassName(); if (!c.startsWith("java.") && !c.startsWith("jdk.")) sb.append(" <").append(c.replaceAll("^.*\\.", "")).append('.').append(el.getMethodName()); if (sb.length() > 900) break; }
        getLogger().info("TRACE tp " + e.getPlayer().getName() + " " + e.getCause() + " -> " + e.getTo().getWorld().getName() + " " + e.getTo().getBlockX() + "," + e.getTo().getBlockY() + "," + e.getTo().getBlockZ() + " cancelled=" + e.isCancelled() + sb);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void traceRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        if (Boolean.getBoolean("voidwell.trace")) getLogger().info("TRACE respawn " + e.getPlayer().getName() + " -> " + e.getRespawnLocation().getWorld().getName() + " " + e.getRespawnLocation().getBlockX() + "," + e.getRespawnLocation().getBlockY() + "," + e.getRespawnLocation().getBlockZ());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        var id = e.getPlayer().getUniqueId();
        store.save(store.get(id));
        store.forget(id);
        bellListener.forget(id);
        guard.forget(id);
        rewards.forget(id);
        social.forget(id);
    }
}
