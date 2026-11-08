package com.extantape.voidwell.meter;

import com.extantape.voidwell.Text;
import com.extantape.voidwell.VoidwellPlugin;
import com.extantape.voidwell.store.PlayerData;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

/** Turns items given to the void into meter points, and tells the player what happened. */
public final class VoidMeter {
    private final VoidwellPlugin plugin;

    public VoidMeter(VoidwellPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Items gone into the void, thrown or carried in a death. Charges the player's meter, keeps weapons and
     * armour for "the void gives back", and saves at once.
     */
    public void sacrifice(UUID who, List<ItemStack> items, boolean death) {
        PlayerData d = plugin.store.get(who);
        var s = plugin.settings;
        LocalDate today = LocalDate.now();
        double added = 0, wasted = 0;
        boolean capped = false, full = false;
        String biggest = null;
        double biggestWorth = -1;
        List<ItemStack> flat = new ArrayList<>();
        for (ItemStack it : items) flatten(it, flat, 0);
        for (ItemStack it : flat) {
            if (it == null || it.isEmpty()) continue;
            double unit = unitWorth(it);
            Meter.Charge c = d.meter.charge(it.getType().name(), unit, it.getAmount(), today, s.full, s.dailyCap, s.repeatFactor);
            added += c.added();
            wasted += c.wasted();
            capped |= c.capped();
            full |= c.becameFull();
            if (c.added() > biggestWorth) {
                biggestWorth = c.added();
                biggest = nameOf(it);
            }
            if (ValueTable.isGear(it.getType().name())) {
                ItemStack one = it.clone();
                for (int i = 0; i < it.getAmount(); i++) d.pool.add(Base64.getEncoder().encodeToString(one.asOne().serializeAsBytes()));
            }
        }
        if (full && d.offer.isEmpty()) plugin.rewards.rollOffer(d);
        plugin.store.save(d);
        Player p = Bukkit.getPlayer(who);
        if (p == null) return;
        show(p, d, added, biggest, capped, death);
        if (full) announceFull(p);
    }

    /** Shulker boxes and bundles count with what's inside them. */
    private static void flatten(ItemStack it, List<ItemStack> out, int depth) {
        if (it == null || it.isEmpty()) return;
        out.add(it);
        if (depth > 3) return;
        ItemContainerContents box = it.getData(DataComponentTypes.CONTAINER);
        if (box != null) for (ItemStack in : box.contents()) flatten(in, out, depth + 1);
        BundleContents bundle = it.getData(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) for (ItemStack in : bundle.contents()) flatten(in, out, depth + 1);
    }

    /** One item's worth: its material, then wear, then enchantments. */
    public double unitWorth(ItemStack it) {
        ItemRarity rarity = it.getData(DataComponentTypes.RARITY);
        double base = ValueTable.base(it.getType().name(), rarity == null ? "COMMON" : rarity.name());
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof Damageable dmg && it.getType().getMaxDurability() > 0) {
            base *= ValueTable.durabilityFactor(dmg.getDamage(), it.getType().getMaxDurability(), plugin.settings.minDurability);
        }
        double ench = 0;
        Map<Enchantment, Integer> all = new java.util.HashMap<>(it.getEnchantments());
        if (meta instanceof EnchantmentStorageMeta book) all.putAll(book.getStoredEnchants());
        for (var e : all.entrySet()) {
            String key = e.getKey().getKey().getKey();
            boolean curse = key.endsWith("_curse");
            boolean treasure = key.equals("mending") || key.equals("frost_walker") || key.equals("soul_speed")
                    || key.equals("swift_sneak") || key.equals("wind_burst");
            ench += ValueTable.enchantment(e.getValue(), treasure, curse);
        }
        return base + ench;
    }

    private static String nameOf(ItemStack it) {
        String n = it.getType().getKey().getKey().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private void show(Player p, PlayerData d, double added, String biggest, boolean capped, boolean death) {
        var s = plugin.settings;
        String gain = added >= 0.05 ? String.format("+%.1f", added) : "barely moved";
        String from = biggest == null ? "" : " <gray>(" + biggest + (death ? " and more" : "") + ")</gray>";
        p.sendActionBar(Text.mm("<light_purple>Void meter</light_purple> <white>" + Text.bar(d.meter.points, s.full)
                + "</white> <aqua>" + gain + "</aqua>" + from));
        if (death && added > 0)
            Text.tell(p, "The void took what you carried, and gave something back: your void meter went up by "
                    + String.format("%.1f", added) + " to " + pct(d) + ".");
        if (capped)
            Text.tell(p, "The void has taken all it will count for today. Items still fall in, but the meter won't "
                    + "move until tomorrow.");
    }

    private String pct(PlayerData d) {
        return (int) Math.floor(100 * Math.min(d.meter.points, plugin.settings.full) / plugin.settings.full) + "%";
    }

    public void announceFull(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
        p.sendMessage(Text.mm("<dark_aqua>Voidwell:</dark_aqua> <light_purple><bold>Your void meter is full!</bold></light_purple> "
                + "<white>Choose one of three rewards.</white> ")
                .append(Text.mm("<aqua><underlined>[Choose a reward]</underlined></aqua>")
                        .clickEvent(ClickEvent.runCommand("/voidwell reward"))));
    }

    /** On arriving: if a reward is waiting, say so once. */
    public void remindOffer(Player p) {
        PlayerData d = plugin.store.get(p.getUniqueId());
        if (!d.offer.isEmpty()) announceFull(p);
    }

    public String status(PlayerData d) {
        var s = plugin.settings;
        d.meter.rollDay(LocalDate.now());
        return Text.bar(d.meter.points, s.full) + (d.meter.points > s.full
                ? String.format(" (and %.0f%% towards the next)", 100 * (d.meter.points - s.full) / s.full) : "");
    }
}
