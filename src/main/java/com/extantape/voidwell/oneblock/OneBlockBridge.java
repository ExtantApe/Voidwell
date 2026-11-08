package com.extantape.voidwell.oneblock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import oneblock.CommandHandler;
import oneblock.OneBlock;
import oneblock.PlayerInfo;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Every call Voidwell makes into OneBlock 1.6.4, in one place. Reads use OneBlock's public static getters.
 * Islands are made, joined and left with OneBlock's own commands, run as the player, so OneBlock keeps
 * its own files. The single exception is {@link #leaveTeam}, explained there.
 */
public final class OneBlockBridge {

    /** The OneBlock world, or null until an admin has run {@code /ob set}. */
    public World world() {
        return OneBlock.getOffset() == 0 ? null : OneBlock.getWorld();
    }

    public boolean inWorld(Location loc) {
        World w = world();
        return w != null && loc != null && w.equals(loc.getWorld());
    }

    /** The island the player owns or belongs to, or -1. */
    public int islandOf(UUID player) {
        return PlayerInfo.getId(player);
    }

    /** The island whose square this spot is in. Only meaningful in the OneBlock world. */
    public int islandAt(Location loc) {
        return OneBlock.plugin.findNearestRegionId(loc);
    }

    /** The island's one block. */
    public Location oneBlock(int island) {
        int[] xz = OneBlock.plugin.getIslandCoordinates(island);
        return new Location(world(), xz[0], OneBlock.getY(), xz[1]);
    }

    /** Distance between islands, in blocks. */
    public int spacing() {
        return Math.abs(OneBlock.getOffset());
    }

    public UUID owner(int island) {
        if (island < 0 || island >= PlayerInfo.list.size()) return null;
        return PlayerInfo.get(island).uuid;
    }

    public boolean isOwner(UUID player) {
        return PlayerInfo.existsAsOwner(player);
    }

    /** The owner first, then teammates. */
    public List<UUID> members(int island) {
        List<UUID> out = new ArrayList<>();
        if (island < 0 || island >= PlayerInfo.list.size()) return out;
        PlayerInfo info = PlayerInfo.get(island);
        if (info.uuid != null) out.add(info.uuid);
        out.addAll(info.uuids);
        return out;
    }

    /** Makes the player's island if they have none, and puts them on it. OneBlock's own {@code /ob join}. */
    public void join(Player player) {
        player.performCommand("oneblock join");
    }

    /** OneBlock's invite, run as the owner. It opens OneBlock's own accept menu, which Voidwell closes again. */
    public void invite(Player owner, Player invitee) {
        owner.performCommand("oneblock invite " + invitee.getName());
    }

    /** OneBlock's accept, run as the invitee. It leaves their old island and puts them on the new one. */
    public void accept(Player invitee) {
        invitee.performCommand("oneblock accept");
    }

    /** OneBlock's kick, run as the owner. */
    public void kick(Player owner, String memberName) {
        owner.performCommand("oneblock kick " + memberName);
    }

    /**
     * Takes a teammate off their island. {@code /ob idreset} would do the same, then send them to OneBlock's
     * leave spot, so Voidwell calls the same public method that command uses and moves them itself.
     */
    public void leaveTeam(Player member) {
        CommandHandler.idresetCommand(member);
    }

    /** Whether OneBlock clears the inventory when a player leaves an island. Voidwell restores it either way. */
    public boolean clearsInventoryOnReset() {
        return !OneBlock.saveplayerinventory;
    }

    /** OneBlock settings that fight Voidwell, as readable warnings. Empty when all is well. */
    public List<String> conflicts() {
        List<String> out = new ArrayList<>();
        if (OneBlock.autojoin)
            out.add("OneBlock's autojoin is on. It cancels Voidwell's trip back to a player's bell. Run /ob autojoin false.");
        if (OneBlock.rebirth)
            out.add("OneBlock's rebirth_on_the_island is on. Voidwell overrides it, but set it off: /ob rebirth_on_the_island false.");
        if (OneBlock.getOffset() == 0)
            out.add("OneBlock has no island grid yet. An admin needs to run /ob set first.");
        return out;
    }
}
