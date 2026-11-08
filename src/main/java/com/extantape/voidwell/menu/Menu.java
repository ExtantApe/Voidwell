package com.extantape.voidwell.menu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** A chest menu. Nothing can be taken out or put in; clicks run the slot's action. */
public final class Menu implements InventoryHolder {
    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    public Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    public Menu set(int slot, ItemStack icon, Consumer<Player> action) {
        inventory.setItem(slot, icon);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
        return this;
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    void click(Player p, int slot) {
        Consumer<Player> a = actions.get(slot);
        if (a != null) a.accept(p);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** A menu icon with a plain name and lore lines. Tooltips other than these are hidden. */
    public static ItemStack icon(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        return it;
    }
}
