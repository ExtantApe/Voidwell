package com.extantape.voidwell;

import com.extantape.voidwell.store.PlayerData;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /voidwell. For players it shows the meter and opens the reward menu; the clickable chat buttons use it too.
 * Everything else is for admins.
 */
final class VoidwellCommand {
    private VoidwellCommand() {}

    static LiteralCommandNode<CommandSourceStack> build(VoidwellPlugin plugin) {
        return Commands.literal("voidwell")
                .requires(s -> s.getSender().hasPermission("voidwell.use"))
                .executes(c -> {
                    if (c.getSource().getExecutor() instanceof Player p) {
                        PlayerData d = plugin.store.get(p.getUniqueId());
                        if (!d.offer.isEmpty()) plugin.rewards.openMenu(p);
                        else Text.tell(p, "Your void meter: " + plugin.meter.status(d)
                                + ". Sneak and ring your way bell for more.");
                    }
                    return 1;
                })
                .then(Commands.literal("reward").executes(c -> {
                    if (c.getSource().getExecutor() instanceof Player p) plugin.rewards.openMenu(p);
                    return 1;
                }))
                .then(Commands.literal("team")
                        .then(Commands.literal("decline").executes(c -> {
                            if (c.getSource().getExecutor() instanceof Player p) plugin.social.decline(p);
                            return 1;
                        }))
                        .then(Commands.literal("accept").then(Commands.argument("owner", StringArgumentType.word())
                                .executes(c -> accept(plugin, c.getSource(), StringArgumentType.getString(c, "owner"), false))
                                .then(Commands.literal("confirm").executes(c ->
                                        accept(plugin, c.getSource(), StringArgumentType.getString(c, "owner"), true))))))
                .then(Commands.literal("reload").requires(s -> s.getSender().hasPermission("voidwell.admin"))
                        .executes(c -> {
                            plugin.reload();
                            status(plugin, c.getSource().getSender());
                            return 1;
                        }))
                .then(Commands.literal("status").requires(s -> s.getSender().hasPermission("voidwell.admin"))
                        .executes(c -> { status(plugin, c.getSource().getSender()); return 1; }))
                .then(Commands.literal("meter").requires(s -> s.getSender().hasPermission("voidwell.admin"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("points", DoubleArgumentType.doubleArg(0))
                                        .executes(c -> {
                                            Player p = c.getArgument("player", PlayerSelectorArgumentResolver.class)
                                                    .resolve(c.getSource()).getFirst();
                                            PlayerData d = plugin.store.get(p.getUniqueId());
                                            boolean wasFull = d.meter.isFull(plugin.settings.full);
                                            d.meter.points = Math.min(DoubleArgumentType.getDouble(c, "points"), plugin.settings.full * 2);
                                            if (!wasFull && d.meter.isFull(plugin.settings.full) && d.offer.isEmpty()) plugin.rewards.rollOffer(d);
                                            plugin.store.save(d);
                                            c.getSource().getSender().sendMessage("Voidwell: " + p.getName() + "'s meter is now " + plugin.meter.status(d)
                                                    + (d.offer.isEmpty() ? "" : ", offering " + String.join(", ", d.offer)));
                                            if (!d.offer.isEmpty()) plugin.meter.announceFull(p);
                                            return 1;
                                        }))))
                .then(Commands.literal("offer").requires(s -> s.getSender().hasPermission("voidwell.admin"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("rewards", StringArgumentType.greedyString()).executes(c -> {
                                    Player p = c.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(c.getSource()).getFirst();
                                    PlayerData d = plugin.store.get(p.getUniqueId());
                                    d.offer.clear();
                                    var allowed = plugin.rewards.eligible(d);
                                    for (String id : StringArgumentType.getString(c, "rewards").split(" ")) {
                                        var r = com.extantape.voidwell.reward.Reward.byId(id);
                                        if (r != null && allowed.contains(r) && d.offer.size() < 3) d.offer.add(id);
                                    }
                                    if (d.offer.isEmpty()) {
                                        c.getSource().getSender().sendMessage("Voidwell: none of those can be offered to " + p.getName() + " now. Rewards: featherfall, void-safe, bountiful-block, void-gives-back (needs gear given to the void), island-travel (needs HouseRules circles). Turned-off rewards are never offered.");
                                        return 0;
                                    }
                                    d.meter.points = Math.max(d.meter.points, plugin.settings.full);
                                    plugin.store.save(d);
                                    plugin.meter.announceFull(p);
                                    c.getSource().getSender().sendMessage("Voidwell: offered " + p.getName() + " " + String.join(", ", d.offer) + ".");
                                    return 1;
                                }))))
                .then(Commands.literal("bell").requires(s -> s.getSender().hasPermission("voidwell.admin"))
                        .then(Commands.argument("player", ArgumentTypes.player()).executes(c -> {
                            Player p = c.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(c.getSource()).getFirst();
                            int island = plugin.bridge.islandOf(p.getUniqueId());
                            var pos = island < 0 ? null : plugin.bells.ensure(island);
                            c.getSource().getSender().sendMessage(pos == null
                                    ? "Voidwell: " + p.getName() + " has no island, or its bell can't be placed (is the island loaded?)."
                                    : "Voidwell: island " + island + "'s way bell is at " + pos.x() + " " + pos.y() + " " + pos.z() + ".");
                            return 1;
                        })))
                .build();
    }

    private static int accept(VoidwellPlugin plugin, CommandSourceStack src, String owner, boolean confirm) {
        if (src.getExecutor() instanceof Player p) plugin.social.accept(p, owner, confirm);
        return 1;
    }

    static void status(VoidwellPlugin plugin, CommandSender to) {
        to.sendMessage("Voidwell " + plugin.getPluginMeta().getVersion() + ":");
        for (String line : plugin.health()) to.sendMessage(" - " + line);
    }
}
