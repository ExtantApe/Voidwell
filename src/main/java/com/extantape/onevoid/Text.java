package com.extantape.onevoid;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

/**
 * Chat and labels. Every message starts with "OneVoid:" in words, so it's clear where it came from, and never
 * relies on colour alone: what happened is always in the text.
 */
public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Component PREFIX = Component.text("OneVoid: ", NamedTextColor.DARK_AQUA);

    private Text() {}

    public static Component mm(String s, TagResolver... r) {
        return MM.deserialize(s, r);
    }

    public static TagResolver p(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    public static void tell(Player p, String mini, TagResolver... r) {
        p.sendMessage(PREFIX.append(mm("<white>" + mini, r)));
    }

    public static void warn(Player p, String mini, TagResolver... r) {
        p.sendMessage(PREFIX.append(mm("<yellow>" + mini, r)));
    }

    /** Item names in menus lose Minecraft's default italics, so they read plainly. */
    public static Component plain(String mini, TagResolver... r) {
        return mm(mini, r).decoration(TextDecoration.ITALIC, false);
    }

    /** A meter as text: "▮▮▮▮▯▯▯▯▯▯ 42%". The number carries the meaning; the bar only helps. */
    public static String bar(double points, double full) {
        int pct = (int) Math.floor(100 * Math.min(points, full) / full);
        int filled = Math.min(10, pct / 10);
        return "▮".repeat(filled) + "▯".repeat(10 - filled) + " " + pct + "%";
    }

    public static String minutes(long millis) {
        long s = Math.max(0, (millis + 999) / 1000);
        return s >= 60 ? (s / 60) + " min " + (s % 60) + " s" : s + " s";
    }
}
