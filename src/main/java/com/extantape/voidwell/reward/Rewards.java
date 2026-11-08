package com.extantape.voidwell.reward;

import com.extantape.voidwell.Text;
import com.extantape.voidwell.VoidwellPlugin;
import com.extantape.voidwell.menu.Menu;
import com.extantape.voidwell.store.PlayerData;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * A full meter offers three rewards, rolled once and saved, so closing and reopening the menu can't reroll
 * them. The player picks one. The only chance involved, "the void gives back", says its odds up front.
 */
public final class Rewards {
    private final VoidwellPlugin plugin;
    private final Random random = new Random();
    private final NamespacedKey deliveryKey;
    private final java.util.Map<UUID, Reward> armed = new java.util.HashMap<>();

    public Rewards(VoidwellPlugin plugin) {
        this.plugin = plugin;
        this.deliveryKey = new NamespacedKey(plugin, "delivery");
    }

    public List<Reward> eligible(PlayerData d) {
        var s = plugin.settings;
        List<Reward> out = new ArrayList<>();
        if (s.featherfall) out.add(Reward.FEATHERFALL);
        if (s.voidSafe) out.add(Reward.VOID_SAFE);
        if (s.bountiful) out.add(Reward.BOUNTIFUL_BLOCK);
        if (s.givesBack && !d.pool.isEmpty()) out.add(Reward.VOID_GIVES_BACK);
        if (s.travel && plugin.social.circlesExist()) out.add(Reward.ISLAND_TRAVEL);
        return out;
    }

    public void rollOffer(PlayerData d) {
        List<Reward> pool = eligible(d);
        Collections.shuffle(pool, random);
        d.offer.clear();
        for (Reward r : pool.subList(0, Math.min(3, pool.size()))) d.offer.add(r.id);
    }

    // The menu.

    public void openMenu(Player p) {
        PlayerData d = plugin.store.get(p.getUniqueId());
        if (d.offer.isEmpty()) {
            Text.tell(p, "Your void meter is at " + plugin.meter.status(d) + ". Rewards are offered when it's full.");
            return;
        }
        Menu m = new Menu(3, Text.mm("Void meter full: choose one reward"));
        int[] slots = {11, 13, 15};
        Reward armedNow = armed.get(p.getUniqueId());
        for (int i = 0; i < d.offer.size() && i < 3; i++) {
            Reward r = Reward.byId(d.offer.get(i));
            if (r == null) continue;
            boolean isArmed = r == armedNow;
            List<Component> lore = new ArrayList<>(describe(r, d));
            lore.add(Component.empty());
            lore.add(Text.plain(isArmed ? "<green><bold>Click again to take this one.</bold></green>"
                    : "<yellow>Click to choose, then click again to confirm.</yellow>"));
            ItemStack icon = Menu.icon(iconOf(r), Text.plain("<light_purple><bold>" + title(r) + "</bold></light_purple>"
                    + (isArmed ? " <green>(chosen)</green>" : "")), lore);
            if (isArmed) icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            m.set(slots[i], icon, who -> {
                if (armed.get(who.getUniqueId()) == r) {
                    armed.remove(who.getUniqueId());
                    who.closeInventory();
                    take(who, r);
                } else {
                    armed.put(who.getUniqueId(), r);
                    openMenu(who);
                }
            });
        }
        m.set(22, Menu.icon(Material.BARRIER, Text.plain("<white>Decide later</white>"),
                List.of(Text.plain("<gray>Your choices stay the same until you pick one.</gray>"),
                        Text.plain("<gray>Sneak and ring your way bell to come back here.</gray>"))), who -> {
            armed.remove(who.getUniqueId());
            who.closeInventory();
        });
        m.open(p);
    }

    private static Material iconOf(Reward r) {
        return switch (r) {
            case FEATHERFALL -> Material.FEATHER;
            case VOID_SAFE -> Material.ENDER_EYE;
            case BOUNTIFUL_BLOCK -> Material.GRASS_BLOCK;
            case VOID_GIVES_BACK -> Material.NETHERITE_SWORD;
            case ISLAND_TRAVEL -> Material.ENDER_PEARL;
        };
    }

    public static String title(Reward r) {
        return switch (r) {
            case FEATHERFALL -> "Featherfall";
            case VOID_SAFE -> "Void-safe";
            case BOUNTIFUL_BLOCK -> "Bountiful block";
            case VOID_GIVES_BACK -> "The void gives back";
            case ISLAND_TRAVEL -> "Island travel";
        };
    }

    private List<Component> describe(Reward r, PlayerData d) {
        var s = plugin.settings;
        return switch (r) {
            case FEATHERFALL -> List.of(Text.plain("<white>Fall slowly for " + s.featherfallMinutes + " minutes.</white>"));
            case VOID_SAFE -> List.of(Text.plain("<white>For " + s.voidSafeMinutes + " minutes, falling into the void</white>"),
                    Text.plain("<white>lifts you back to your island, with everything you carry.</white>"));
            case BOUNTIFUL_BLOCK -> List.of(Text.plain("<white>Your one block drops double for " + s.bountifulMinutes + " minutes.</white>"));
            case VOID_GIVES_BACK -> {
                int gear = d.pool.size();
                int back = (gear + 1) / 2;
                yield List.of(
                        Text.plain("<white>A <bold>1 in " + s.givesBackOneIn + "</bold> chance.</white>"),
                        Text.plain("<white>If it comes: " + back + " of the " + gear + " weapons and armour you've given</white>"),
                        Text.plain("<white>the void return, each with one enchantment a level past the usual most.</white>"),
                        Text.plain("<white>If not: your meter starts again " + s.consolationPercent + "% full, and your gear</white>"),
                        Text.plain("<white>stays in the void for another try.</white>"));
            }
            case ISLAND_TRAVEL -> List.of(
                    Text.plain("<white>For " + s.travelMinutes + " minutes, visit islands of players who share a chat</white>"),
                    Text.plain("<white>circle with you and have visits turned on. You can look, not change things.</white>"),
                    Text.plain("<white>You come home by yourself when it ends.</white>"));
        };
    }

    // Taking a reward.

    private void take(Player p, Reward r) {
        PlayerData d = plugin.store.get(p.getUniqueId());
        if (!d.offer.contains(r.id)) return;
        var s = plugin.settings;
        d.offer.clear();
        d.meter.spend(s.full);
        long now = System.currentTimeMillis();
        switch (r) {
            case FEATHERFALL -> {
                d.powers.put(r.id, now + s.featherfallMinutes * 60_000L);
                applyFeatherfall(p, d);
                Text.tell(p, "<green>Featherfall</green> for " + s.featherfallMinutes + " minutes. You'll fall slowly until it wears off.");
            }
            case VOID_SAFE -> {
                d.powers.put(r.id, now + s.voidSafeMinutes * 60_000L);
                Text.tell(p, "<green>Void-safe</green> for " + s.voidSafeMinutes + " minutes. If you fall into the void, it lifts you back to your island.");
            }
            case BOUNTIFUL_BLOCK -> {
                d.powers.put(r.id, now + s.bountifulMinutes * 60_000L);
                Text.tell(p, "<green>Bountiful block</green> for " + s.bountifulMinutes + " minutes. Your one block drops double.");
            }
            case ISLAND_TRAVEL -> {
                d.powers.put(r.id, now + s.travelMinutes * 60_000L);
                Text.tell(p, "<green>Island travel</green> for " + s.travelMinutes + " minutes. Sneak and ring your way bell to pick an island.");
            }
            case VOID_GIVES_BACK -> givesBack(p, d);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        if (d.meter.isFull(s.full) && d.offer.isEmpty()) rollOffer(d);
        plugin.store.save(d);
        if (!d.offer.isEmpty()) plugin.meter.announceFull(p);
        if (r == Reward.ISLAND_TRAVEL) plugin.social.openTravel(p);
    }

    private void givesBack(Player p, PlayerData d) {
        var s = plugin.settings;
        boolean won = random.nextInt(s.givesBackOneIn) == 0;
        if (!won) {
            d.meter.points += s.full * s.consolationPercent / 100.0;
            Text.tell(p, "Not this time: the void kept your gear. Your meter starts again <aqua>" + s.consolationPercent
                    + "% full</aqua>, and the gear is still there for another try.");
            return;
        }
        List<String> pool = new ArrayList<>(d.pool);
        Collections.shuffle(pool, random);
        int back = (pool.size() + 1) / 2;
        String batch = Long.toString(System.currentTimeMillis(), 36);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < back; i++) {
            ItemStack it = ItemStack.deserializeBytes(Base64.getDecoder().decode(pool.get(i)));
            String upgrade = upgrade(it);
            names.add(name(it) + (upgrade == null ? "" : " (" + upgrade + ")"));
            String id = batch + "-" + i;
            it.editMeta(meta -> meta.getPersistentDataContainer().set(deliveryKey, PersistentDataType.STRING, id));
            d.deliveries.put(id, List.of(Base64.getEncoder().encodeToString(it.serializeAsBytes())));
        }
        d.pool.clear(); // the other half stays with the void
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Text.tell(p, "<light_purple><bold>The void gives back!</bold></light_purple> " + back + " came back: "
                + String.join(", ", names) + ".");
        // Saved first, then handed over: see deliver().
        plugin.store.save(d);
        deliver(p);
    }

    /** One enchantment one level past its usual most. Returns its description, or null if nothing fits. */
    private String upgrade(ItemStack it) {
        List<Enchantment> choices = new ArrayList<>();
        for (Enchantment e : it.getEnchantments().keySet())
            if (e.getMaxLevel() > 1 && !e.getKey().getKey().endsWith("_curse")) choices.add(e);
        if (choices.isEmpty())
            for (Enchantment e : io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT))
                if (e.getMaxLevel() > 1 && e.canEnchantItem(it) && !e.getKey().getKey().endsWith("_curse")) choices.add(e);
        if (choices.isEmpty()) return null;
        Enchantment e = choices.get(random.nextInt(choices.size()));
        int level = e.getMaxLevel() + 1;
        it.addUnsafeEnchantment(e, level);
        it.editMeta(meta -> {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Text.plain("<light_purple>Void-touched</light_purple>"));
            meta.lore(lore);
        });
        return pretty(e.getKey().getKey()) + " " + level;
    }

    private static String pretty(String key) {
        String n = key.replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static String name(ItemStack it) {
        return pretty(it.getType().getKey().getKey());
    }

    /**
     * Hands over owed items, only in the OneBlock world, where they belong. Each item carries its delivery id.
     * If the server crashed after the items were given and saved but before the delivery was crossed off, the
     * ids are already in the inventory and nothing is given twice. If it crashed before the player was saved,
     * the items aren't there and are given again, once.
     */
    public void deliver(Player p) {
        if (!plugin.bridge.inWorld(p.getLocation())) return;
        PlayerData d = plugin.store.get(p.getUniqueId());
        if (d.deliveries.isEmpty()) return;
        java.util.Set<String> held = new java.util.HashSet<>();
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || !it.hasItemMeta()) continue;
            String id = it.getItemMeta().getPersistentDataContainer().get(deliveryKey, PersistentDataType.STRING);
            if (id != null) held.add(id);
        }
        List<String> done = new ArrayList<>();
        int waiting = 0;
        for (var e : d.deliveries.entrySet()) {
            if (held.contains(e.getKey())) {
                done.add(e.getKey());
                continue;
            }
            ItemStack it = ItemStack.deserializeBytes(Base64.getDecoder().decode(e.getValue().getFirst()));
            if (p.getInventory().firstEmpty() < 0) {
                waiting++;
                continue;
            }
            p.getInventory().addItem(it);
            done.add(e.getKey());
        }
        if (done.isEmpty() && waiting == 0) return;
        p.saveData();
        done.forEach(d.deliveries::remove);
        plugin.store.save(d);
        if (waiting > 0)
            Text.warn(p, "The void is holding " + waiting + (waiting == 1 ? " item" : " items")
                    + " for you, but your inventory is full. Make room and sneak-ring your way bell to collect.");
    }

    // Powers.

    public void applyFeatherfall(Player p, PlayerData d) {
        long left = d.powerLeft(Reward.FEATHERFALL.id, System.currentTimeMillis());
        if (left <= 0) return;
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, (int) (left / 50), 0, false, true, true));
    }

    public NamespacedKey deliveryKey() {
        return deliveryKey;
    }

    public void forget(UUID id) {
        armed.remove(id);
    }
}
