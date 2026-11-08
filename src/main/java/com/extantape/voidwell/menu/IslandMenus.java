package com.extantape.voidwell.menu;

import com.extantape.voidwell.Text;
import com.extantape.voidwell.VoidwellPlugin;
import com.extantape.voidwell.reward.Reward;
import com.extantape.voidwell.store.PlayerData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** What sneaking and ringing a way bell opens. Every choice says what it does in words. */
public final class IslandMenus {
    private final VoidwellPlugin plugin;

    public IslandMenus(VoidwellPlugin plugin) {
        this.plugin = plugin;
    }

    /** For the island's own players. */
    public void island(Player p, int island) {
        plugin.rewards.deliver(p);
        boolean owner = plugin.bridge.isOwner(p.getUniqueId());
        PlayerData d = plugin.store.get(p.getUniqueId());
        PlayerData ownerData = plugin.store.get(plugin.bridge.owner(island) == null ? p.getUniqueId() : plugin.bridge.owner(island));
        var s = plugin.settings;
        Menu m = new Menu(3, Text.mm("Your island"));

        m.set(4, Menu.icon(Material.BELL, Text.plain("<aqua>Way bell</aqua>"), List.of(
                Text.plain("<white>Ring it " + s.rings + " times to go to the Waystation.</white>"),
                Text.plain("<gray>You'll come back right here, with everything as you left it.</gray>"))), null);

        // The void meter.
        d.meter.rollDay(LocalDate.now());
        List<Component> meterLore = new ArrayList<>(List.of(
                Text.plain("<white>" + plugin.meter.status(d) + "</white>"),
                Text.plain(String.format("<gray>Charged today: %.0f of %.0f.</gray>", d.meter.chargedToday, s.dailyCap)),
                Text.plain("<gray>Throw items into the void, or lose them there, to charge it.</gray>"),
                Text.plain("<gray>Gear and enchantments count most. Common blocks hardly count.</gray>")));
        if (!d.offer.isEmpty()) meterLore.addFirst(Text.plain("<green><bold>Full! Click to choose a reward.</bold></green>"));
        m.set(10, Menu.icon(d.offer.isEmpty() ? Material.ENDER_EYE : Material.NETHER_STAR,
                Text.plain("<light_purple>Void meter</light_purple>"), meterLore),
                d.offer.isEmpty() ? null : who -> plugin.rewards.openMenu(who));

        // Visits.
        boolean open = ownerData.visitsOpen;
        List<Component> visitLore = new ArrayList<>(List.of(
                Text.plain(open ? "<green>Visits are ON.</green>" : "<white>Visits are OFF.</white>"),
                Text.plain("<gray>When on, players who share a chat circle with you and</gray>"),
                Text.plain("<gray>have island travel can visit. They can't change anything.</gray>")));
        visitLore.add(Text.plain(owner ? "<yellow>Click to turn visits " + (open ? "off" : "on") + ".</yellow>"
                : "<gray>Only the island's owner can change this.</gray>"));
        m.set(12, Menu.icon(open ? Material.LIME_DYE : Material.GRAY_DYE,
                Text.plain("<white>Visits: " + (open ? "on" : "off") + "</white>"), visitLore), owner ? who -> {
            ownerData.visitsOpen = !ownerData.visitsOpen;
            plugin.store.save(ownerData);
            if (!ownerData.visitsOpen)
                for (Player v : plugin.social.visitorsOn(island)) plugin.social.sendHome(v, "That island has closed to visitors, so you're back home.");
            Text.tell(who, "Visits are now " + (ownerData.visitsOpen ? "<green>on</green>" : "off") + ".");
            island(who, island);
        } : null);

        // Visitors here now.
        List<Player> visitors = plugin.social.visitorsOn(island);
        m.set(13, Menu.icon(Material.OAK_DOOR, Text.plain("<white>Visitors here: " + visitors.size() + "</white>"),
                visitors.isEmpty() ? List.of(Text.plain("<gray>Nobody is visiting.</gray>"))
                        : List.of(Text.plain("<yellow>Click to send every visitor home.</yellow>"))),
                visitors.isEmpty() ? null : who -> {
                    for (Player v : plugin.social.visitorsOn(island))
                        plugin.social.sendHome(v, "The island's players sent visitors home, so you're back on your own island.");
                    Text.tell(who, "Visitors sent home.");
                    who.closeInventory();
                });

        // Teams.
        boolean circles = plugin.social.circlesExist();
        if (owner) {
            m.set(14, Menu.icon(Material.WRITABLE_BOOK, Text.plain("<white>Invite a teammate</white>"), List.of(
                    Text.plain("<gray>Share this island with a player who shares a chat circle</gray>"),
                    Text.plain("<gray>with you. Up to " + s.maxTeam + " players in all.</gray>"),
                    Text.plain(circles ? "<yellow>Click to pick who.</yellow>" : "<gray>Not available: this server has no chat circles.</gray>"))),
                    circles ? this::invitePicker : null);
        }
        List<UUID> members = plugin.bridge.members(island);
        m.set(15, Menu.icon(Material.PLAYER_HEAD, Text.plain("<white>Teammates: " + (members.size() - 1) + "</white>"),
                List.of(Text.plain(owner ? "<yellow>Click to see or remove teammates.</yellow>"
                        : "<yellow>Click to see the team, or leave it.</yellow>"))), who -> teammates(who, island));

        // Travel.
        long travel = d.powerLeft(Reward.ISLAND_TRAVEL.id, System.currentTimeMillis());
        if (travel > 0)
            m.set(16, Menu.icon(Material.ENDER_PEARL, Text.plain("<aqua>Island travel: " + Text.minutes(travel) + " left</aqua>"),
                    List.of(Text.plain("<yellow>Click to pick an island to visit.</yellow>"))), plugin.social::openTravel);

        if (!d.deliveries.isEmpty())
            m.set(22, Menu.icon(Material.CHEST, Text.plain("<light_purple>The void is holding " + d.deliveries.size() + " items for you</light_purple>"),
                    List.of(Text.plain("<yellow>Make room in your inventory, then click to collect.</yellow>"))),
                    who -> { plugin.rewards.deliver(who); who.closeInventory(); });
        m.open(p);
    }

    /** For anyone else who sneak-rings a bell, usually a visitor. */
    public void visitor(Player p, int island) {
        Menu m = new Menu(3, Text.mm("Someone else's island"));
        m.set(11, Menu.icon(Material.BELL, Text.plain("<white>This is another island's way bell</white>"),
                List.of(Text.plain("<gray>Only its own players can ring it to leave.</gray>"))), null);
        boolean away = plugin.social.isVisiting(p);
        m.set(15, Menu.icon(Material.OAK_DOOR, Text.plain("<white>Go home now</white>"), List.of(Text.plain(
                away ? "<yellow>Click to go back to your own island.</yellow>" : "<gray>You're not visiting.</gray>"))),
                away ? who -> { who.closeInventory(); plugin.social.endTravel(who, "You're back home."); } : null);
        m.open(p);
    }

    private void invitePicker(Player owner) {
        List<Player> list = plugin.social.inviteCandidates(owner);
        Menu m = new Menu(4, Text.mm("Invite a teammate"));
        if (list.isEmpty())
            m.set(13, Menu.icon(Material.PAPER, Text.plain("<white>Nobody to invite right now</white>"), List.of(
                    Text.plain("<gray>You can invite players who are online and share a</gray>"),
                    Text.plain("<gray>chat circle with you.</gray>"))), null);
        int slot = 0;
        for (Player o : list) {
            if (slot >= 27) break;
            UUID id = o.getUniqueId();
            m.set(slot++, head(o, "<aqua>" + o.getName() + "</aqua>", "<yellow>Click to invite. They'll be asked first.</yellow>"),
                    who -> {
                        who.closeInventory();
                        Player target = Bukkit.getPlayer(id);
                        if (target != null) plugin.social.invite(who, target);
                    });
        }
        m.open(owner);
    }

    private void teammates(Player p, int island) {
        boolean owner = plugin.bridge.isOwner(p.getUniqueId());
        List<UUID> members = plugin.bridge.members(island);
        Menu m = new Menu(4, Text.mm("Teammates"));
        int slot = 0;
        for (UUID id : members) {
            if (slot >= 27) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = op.getName() == null ? "a player" : op.getName();
            boolean isOwner = id.equals(plugin.bridge.owner(island));
            boolean removable = owner && !isOwner;
            m.set(slot++, head(op, "<aqua>" + name + "</aqua>" + (isOwner ? " <gray>(owner)</gray>" : ""),
                    removable ? "<yellow>Click to take them off this island.</yellow>" : ""),
                    removable ? who -> confirm(who, "Take " + name + " off your island?",
                            "They'll get an island of their own next time.", w -> { w.closeInventory(); plugin.social.removeMember(w, id); }) : null);
        }
        if (!owner && members.contains(p.getUniqueId()))
            m.set(31, Menu.icon(Material.IRON_DOOR, Text.plain("<white>Leave this team</white>"), List.of(
                    Text.plain("<gray>You'll get an island of your own, and keep your inventory.</gray>"),
                    Text.plain("<yellow>Click, then confirm.</yellow>"))),
                    who -> confirm(who, "Leave this team for good?", "You'll start a new island of your own.",
                            w -> { w.closeInventory(); plugin.social.leaveTeam(w); }));
        m.open(p);
    }

    private void confirm(Player p, String question, String detail, java.util.function.Consumer<Player> yes) {
        Menu m = new Menu(3, Text.mm(question));
        m.set(11, Menu.icon(Material.LIME_CONCRETE, Text.plain("<green><bold>Yes</bold></green>"),
                List.of(Text.plain("<gray>" + detail + "</gray>"))), yes);
        m.set(15, Menu.icon(Material.RED_CONCRETE, Text.plain("<white><bold>No, go back</bold></white>"), List.of()),
                Player::closeInventory);
        m.open(p);
    }

    private static ItemStack head(OfflinePlayer who, String name, String line) {
        ItemStack head = Menu.icon(Material.PLAYER_HEAD, Text.plain(name), line.isEmpty() ? List.of() : List.of(Text.plain(line)));
        head.editMeta(SkullMeta.class, sm -> sm.setOwningPlayer(who));
        return head;
    }
}
