package com.extantape.waystation;

import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Stands in for Waystation in OneVoid's live tests. The "station" is a stone pad in the main world.
 * "/standin gate <player> <world>" plays the part of walking through that world's gate.
 * "/standin landing on|off" makes landing() answer or come back empty, to test the fallback.
 */
public final class StandinPlugin extends JavaPlugin {
    private volatile boolean landingOn = true;

    @Override
    public void onEnable() {
        LandingService service = world -> {
            if (!landingOn) return Optional.empty();
            World main = Bukkit.getWorlds().getFirst();
            return Optional.of(new Location(main, 0.5, 101, 3.5, 0f, 0f));
        };
        getServer().getServicesManager().register(LandingService.class, service, this, ServicePriority.Normal);
        Bukkit.getScheduler().runTask(this, () -> {
            World main = Bukkit.getWorlds().getFirst();
            for (int x = -4; x <= 4; x++)
                for (int z = -4; z <= 8; z++)
                    main.getBlockAt(x, 100, z).setType(Material.STONE);
            main.setSpawnLocation(0, 101, 0);
        });
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 2 && args[0].equals("landing")) {
            landingOn = args[1].equals("on");
            sender.sendMessage("standin landing " + (landingOn ? "on" : "off"));
            return true;
        }
        if (args.length >= 3 && args[0].equals("gate")) {
            Player player = Bukkit.getPlayerExact(args[1]);
            if (player == null) { sender.sendMessage("standin: no such player"); return true; }
            player.closeInventory();
            WaystationHandOverEvent event = new WaystationHandOverEvent(player, "gate-" + args[2], args[2]);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isHandled()) player.sendMessage("That world isn't open right now.");
            sender.sendMessage("standin gate " + player.getName() + " handled=" + event.isHandled());
            return true;
        }
        return false;
    }
}
