package com.extantape.onevoid;

import java.util.Locale;
import java.util.Set;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

/**
 * Players don't type OneBlock commands: the gate, the way bell and its menu do everything. Typed /ob is
 * refused with a pointer to what to do instead, and it's hidden from tab-complete. Commands OneVoid runs on a
 * player's behalf aren't typed, so they still work. Admins with onevoid.oneblock-commands keep /ob.
 */
public final class CommandGuard implements Listener {
    static final String BYPASS = "onevoid.oneblock-commands";
    private static final Set<String> LABELS = Set.of("ob", "oneblock", "oneblock:ob", "oneblock:oneblock", "ob j", "oneblock:ob j");

    static boolean isOneBlock(String message) {
        String m = message.startsWith("/") ? message.substring(1) : message;
        String label = m.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        return LABELS.contains(label);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!isOneBlock(e.getMessage()) || e.getPlayer().hasPermission(BYPASS)) return;
        e.setCancelled(true);
        Text.tell(e.getPlayer(), "OneBlock has no commands here. Walk through the OneBlock gate at the Waystation to "
                + "reach your island. On your island, ring your way bell to leave, or sneak and ring it for everything else.");
    }

    @EventHandler
    public void onTabList(PlayerCommandSendEvent e) {
        if (e.getPlayer().hasPermission(BYPASS)) return;
        e.getCommands().removeIf(c -> LABELS.contains(c.toLowerCase(Locale.ROOT)));
    }
}
