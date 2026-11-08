package com.extantape.voidwell.social;

import com.extantape.voidwell.Text;
import com.extantape.voidwell.VoidwellPlugin;
import com.extantape.voidwell.menu.Menu;
import com.extantape.voidwell.reward.Reward;
import com.extantape.voidwell.store.PlayerData;
import com.extantape.voidwell.trip.Trips;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.permissions.Permission;

/**
 * Island travel and teams. Both only ever connect two players who are online and share a HouseRules circle,
 * so kids reach only kids and adults only adults. If there are no circles, or a check can't be made, the
 * answer is no.
 */
public final class Social {
    private static final String CIRCLE = "houserules.circle.";
    private final VoidwellPlugin plugin;
    private final Map<UUID, Invite> invites = new HashMap<>();

    private record Invite(UUID owner, int island, long expires) {}

    public Social(VoidwellPlugin plugin) {
        this.plugin = plugin;
    }

    // Circles.

    private List<String> circleNodes() {
        List<String> out = new ArrayList<>();
        for (Permission perm : Bukkit.getPluginManager().getPermissions()) {
            String n = perm.getName();
            if (n.startsWith(CIRCLE) && n.length() > CIRCLE.length() && !n.endsWith("*")) out.add(n);
        }
        return out;
    }

    public boolean circlesExist() {
        return !circleNodes().isEmpty();
    }

    /** True only if both are online and share at least one circle. */
    public boolean shareCircle(Player a, Player b) {
        if (a == null || b == null || !a.isOnline() || !b.isOnline()) return false;
        for (String node : circleNodes())
            if (a.hasPermission(node) && b.hasPermission(node)) return true;
        return false;
    }

    // Visitors: who's on whose island.

    /** True if the player is standing on an island that isn't theirs. */
    public boolean isVisiting(Player p) {
        if (!plugin.bridge.inWorld(p.getLocation())) return false;
        return plugin.bridge.islandAt(p.getLocation()) != plugin.bridge.islandOf(p.getUniqueId());
    }

    public List<Player> visitorsOn(int island) {
        List<Player> out = new ArrayList<>();
        var w = plugin.bridge.world();
        if (w == null) return out;
        for (Player p : w.getPlayers())
            if (plugin.bridge.islandAt(p.getLocation()) == island && plugin.bridge.islandOf(p.getUniqueId()) != island) out.add(p);
        return out;
    }

    public void sendHome(Player visitor, String why) {
        endTravel(visitor, why);
    }

    // Travel.

    private boolean mayVisit(Player visitor, Player owner) {
        if (owner == null || visitor.equals(owner)) return false;
        int island = plugin.bridge.islandOf(owner.getUniqueId());
        if (island < 0 || !plugin.bridge.isOwner(owner.getUniqueId())) return false;
        if (island == plugin.bridge.islandOf(visitor.getUniqueId())) return false;
        if (!plugin.store.get(owner.getUniqueId()).visitsOpen) return false;
        return shareCircle(visitor, owner);
    }

    public void openTravel(Player p) {
        PlayerData d = plugin.store.get(p.getUniqueId());
        long left = d.powerLeft(Reward.ISLAND_TRAVEL.id, System.currentTimeMillis());
        if (left <= 0) {
            Text.tell(p, "Island travel is a reward from a full void meter.");
            return;
        }
        List<Player> open = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) if (mayVisit(p, o)) open.add(o);
        Menu m = new Menu(4, Text.mm("Island travel: " + Text.minutes(left) + " left"));
        if (open.isEmpty()) {
            m.set(13, Menu.icon(Material.MAP, Text.plain("<white>No islands open right now</white>"), List.of(
                    Text.plain("<gray>You can visit players who are online, share a chat circle</gray>"),
                    Text.plain("<gray>with you, and have visits turned on at their way bell.</gray>"))), null);
        }
        int slot = 0;
        for (Player o : open) {
            if (slot >= 27) break;
            ItemStack head = Menu.icon(Material.PLAYER_HEAD, Text.plain("<aqua>" + o.getName() + "'s island</aqua>"),
                    List.of(Text.plain("<gray>Click to visit. You can look around, but not change anything.</gray>")));
            head.editMeta(SkullMeta.class, sm -> sm.setOwningPlayer(o));
            UUID target = o.getUniqueId();
            m.set(slot++, head, who -> {
                who.closeInventory();
                visit(who, Bukkit.getPlayer(target));
            });
        }
        if (isVisiting(p)) m.set(31, Menu.icon(Material.OAK_DOOR, Text.plain("<white>Go home now</white>"), List.of()),
                who -> { who.closeInventory(); endTravel(who, "You're back home."); });
        m.open(p);
    }

    private void visit(Player p, Player owner) {
        if (!mayVisit(p, owner)) {
            Text.warn(p, "That island isn't open to you right now.");
            return;
        }
        PlayerData d = plugin.store.get(p.getUniqueId());
        if (d.powerLeft(Reward.ISLAND_TRAVEL.id, System.currentTimeMillis()) <= 0) return;
        int island = plugin.bridge.islandOf(owner.getUniqueId());
        Location one = plugin.bridge.oneBlock(island);
        if (!one.getBlock().getType().isSolid()) {
            Text.warn(p, "That island's block is being remade. Try again in a few seconds.");
            return;
        }
        if (d.travelReturn == null) {
            // Remember where home is, unless they're already away.
            boolean home = plugin.bridge.inWorld(p.getLocation()) && !isVisiting(p);
            d.travelReturn = home && Trips.safe(p.getLocation()) ? Trips.toSpot(p.getLocation()) : null;
            plugin.store.save(d);
        }
        p.teleportAsync(one.add(0.5, 1.2, 0.5), TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!ok) return;
            Text.tell(p, "You're visiting <aqua>" + owner.getName() + "</aqua>'s island. You can look around, but not "
                    + "change anything. Sneak and ring any way bell to go home.");
            if (owner.isOnline()) Text.tell(owner, "<aqua>" + p.getName() + "</aqua> is visiting your island. Sneak and "
                    + "ring your way bell to send visitors home.");
        });
    }

    /** Ends a visit: back to where they were, or to their own island. Safe to call when not visiting. */
    public void endTravel(Player p, String message) {
        PlayerData d = plugin.store.get(p.getUniqueId());
        Location back = Trips.toLocation(d.travelReturn);
        d.travelReturn = null;
        plugin.store.save(d);
        if (!isVisiting(p)) return;
        if (back != null && Trips.safe(back)) p.teleportAsync(back, TeleportCause.PLUGIN);
        else plugin.bridge.join(p);
        Text.tell(p, message);
    }

    /** Every second: visits end as soon as the owner leaves, closes visits or stops sharing a circle. */
    public void tick() {
        var w = plugin.bridge.world();
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            if (!isVisiting(p) || p.hasPermission("voidwell.admin")) continue;
            int island = plugin.bridge.islandAt(p.getLocation());
            Player owner = ownerOnline(island);
            boolean travelling = plugin.store.get(p.getUniqueId()).powerLeft(Reward.ISLAND_TRAVEL.id, System.currentTimeMillis()) > 0;
            if (!travelling) continue; // walking or bridging there is OneBlock's business; visitors still can't change anything
            if (owner == null || !mayVisit(p, owner)) endTravel(p, "That island has closed to visitors, so you're back home.");
        }
    }

    private Player ownerOnline(int island) {
        UUID owner = plugin.bridge.owner(island);
        return owner == null ? null : Bukkit.getPlayer(owner);
    }

    // Teams.

    public List<Player> inviteCandidates(Player owner) {
        int island = plugin.bridge.islandOf(owner.getUniqueId());
        List<Player> out = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers())
            if (!o.equals(owner) && plugin.bridge.islandOf(o.getUniqueId()) != island && shareCircle(owner, o)) out.add(o);
        return out;
    }

    public void invite(Player owner, Player invitee) {
        int island = plugin.bridge.islandOf(owner.getUniqueId());
        if (!plugin.bridge.isOwner(owner.getUniqueId()) || island < 0) return;
        if (!shareCircle(owner, invitee)) {
            Text.warn(owner, "You can only invite players who share a chat circle with you.");
            return;
        }
        if (plugin.bridge.members(island).size() >= plugin.settings.maxTeam) {
            Text.warn(owner, "Your island already has " + plugin.settings.maxTeam + " players, the most it can have.");
            return;
        }
        invites.put(invitee.getUniqueId(), new Invite(owner.getUniqueId(), island,
                System.currentTimeMillis() + plugin.settings.inviteSeconds * 1000L));
        Text.tell(owner, "Invite sent to <aqua>" + invitee.getName() + "</aqua>. It lasts " + plugin.settings.inviteSeconds + " seconds.");
        invitee.sendMessage(Text.mm("<dark_aqua>Voidwell:</dark_aqua> <aqua>" + owner.getName() + "</aqua> <white>invites you "
                        + "to share their OneBlock island.</white> ")
                .append(Text.mm("<green><underlined>[Join]</underlined></green>")
                        .clickEvent(ClickEvent.runCommand("/voidwell team accept " + owner.getName())))
                .append(Text.mm(" "))
                .append(Text.mm("<gray><underlined>[No thanks]</underlined></gray>")
                        .clickEvent(ClickEvent.runCommand("/voidwell team decline"))));
    }

    public void decline(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        if (inv == null) return;
        Text.tell(p, "Invite declined.");
        Player owner = Bukkit.getPlayer(inv.owner);
        if (owner != null) Text.tell(owner, "<aqua>" + p.getName() + "</aqua> said no thanks to your invite.");
    }

    public void accept(Player p, String ownerName, boolean confirmed) {
        Invite inv = invites.get(p.getUniqueId());
        Player owner = Bukkit.getPlayerExact(ownerName);
        if (inv == null || inv.expires < System.currentTimeMillis() || owner == null || !owner.getUniqueId().equals(inv.owner)) {
            invites.remove(p.getUniqueId());
            Text.warn(p, "That invite has run out or been withdrawn. Ask for a new one.");
            return;
        }
        if (!shareCircle(p, owner) || plugin.bridge.islandOf(owner.getUniqueId()) != inv.island
                || plugin.bridge.members(inv.island).size() >= plugin.settings.maxTeam) {
            invites.remove(p.getUniqueId());
            Text.warn(p, "That invite can't be used any more. Ask for a new one.");
            return;
        }
        int own = plugin.bridge.islandOf(p.getUniqueId());
        if (own >= 0 && !confirmed) {
            boolean teammates = plugin.bridge.members(own).size() > 1;
            p.sendMessage(Text.mm("<dark_aqua>Voidwell:</dark_aqua> <yellow>Joining means leaving your own island for good. "
                            + (teammates ? "Your teammates keep it." : "Its blocks stay, but you won't be able to go back to it.")
                            + " Your OneBlock inventory comes with you.</yellow> ")
                    .append(Text.mm("<green><underlined>[Yes, join]</underlined></green>")
                            .clickEvent(ClickEvent.runCommand("/voidwell team accept " + owner.getName() + " confirm")))
                    .append(Text.mm(" "))
                    .append(Text.mm("<gray><underlined>[Cancel]</underlined></gray>")
                            .clickEvent(ClickEvent.runCommand("/voidwell team decline"))));
            return;
        }
        invites.remove(p.getUniqueId());
        // OneBlock's accept resets the player's old island membership, which also empties the inventory they're
        // holding right now (in whichever world). Put it straight back, in the same tick.
        ItemStack[] keep = cloneAll(p.getInventory().getContents());
        plugin.bridge.invite(owner, p);
        p.closeInventory(); // OneBlock's own accept menu
        plugin.bridge.accept(p);
        p.getInventory().setContents(keep);
        PlayerData d = plugin.store.get(p.getUniqueId());
        d.lastSpot = null;
        d.lastIsland = -1;
        plugin.store.save(d);
        if (plugin.bridge.islandOf(p.getUniqueId()) == inv.island) {
            Text.tell(p, "You've joined <aqua>" + owner.getName() + "</aqua>'s island. Its way bell is yours to ring now.");
            Text.tell(owner, "<aqua>" + p.getName() + "</aqua> joined your island.");
        } else {
            Text.warn(p, "OneBlock didn't add you to the island. Nothing else changed.");
        }
    }

    public void removeMember(Player owner, UUID member) {
        int island = plugin.bridge.islandOf(owner.getUniqueId());
        if (!plugin.bridge.isOwner(owner.getUniqueId()) || !plugin.bridge.members(island).contains(member)) return;
        var name = Bukkit.getOfflinePlayer(member).getName();
        if (name == null) return;
        Player online = Bukkit.getPlayer(member);
        ItemStack[] keep = online == null ? null : cloneAll(online.getInventory().getContents());
        plugin.bridge.kick(owner, name);
        if (online != null) {
            online.getInventory().setContents(keep);
            Text.tell(online, "<aqua>" + owner.getName() + "</aqua> took you off their island. Next time you go through "
                    + "the OneBlock gate, you'll get an island of your own.");
        }
        Text.tell(owner, "<aqua>" + name + "</aqua> is no longer on your island.");
    }

    public void leaveTeam(Player member) {
        int island = plugin.bridge.islandOf(member.getUniqueId());
        if (island < 0 || plugin.bridge.isOwner(member.getUniqueId())) return;
        ItemStack[] keep = cloneAll(member.getInventory().getContents());
        plugin.bridge.leaveTeam(member);
        member.getInventory().setContents(keep);
        PlayerData d = plugin.store.get(member.getUniqueId());
        d.lastSpot = null;
        d.lastIsland = -1;
        plugin.store.save(d);
        UUID owner = plugin.bridge.owner(island);
        Player o = owner == null ? null : Bukkit.getPlayer(owner);
        if (o != null) Text.tell(o, "<aqua>" + member.getName() + "</aqua> left your island.");
        plugin.bridge.join(member); // a new island of their own, with the inventory they had
        Text.tell(member, "You've left the team. This is your own island now.");
    }

    private static ItemStack[] cloneAll(ItemStack[] in) {
        ItemStack[] out = new ItemStack[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[i] == null ? null : in[i].clone();
        return out;
    }

    public void forget(UUID id) {
        invites.remove(id);
    }
}
