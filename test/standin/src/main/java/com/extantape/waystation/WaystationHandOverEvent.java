package com.extantape.waystation;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Test stand-in with the same shape as Waystation 0.4.0's event. */
public class WaystationHandOverEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String gateId;
    private final String destination;
    private boolean handled;

    public WaystationHandOverEvent(Player player, String gateId, String destination) {
        super(player);
        this.gateId = gateId;
        this.destination = destination;
    }

    public String getGateId() { return gateId; }
    public String getDestination() { return destination; }
    public boolean isHandled() { return handled; }
    public void setHandled(boolean handled) { this.handled = handled; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
