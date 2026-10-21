package com.example.kitmenu;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** One kit: its menu look, cooldown, permission and the items it gives. */
final class Kit {
    /** Player inventory slots stored in a kit: 0-35 main + hotbar, 36-39 armor, 40 offhand. */
    static final int SIZE = 41;

    final String id;
    /** MiniMessage name shown in the menu, e.g. "<#9863E7><b>Parrot". Its colors are kept everywhere. */
    String display = "";
    String icon = "IRON_SWORD";
    /** Base64 item used as the icon, or empty to use {@link #icon}. */
    String iconItem = "";
    long cooldown = 600L;
    /** 1-based position in the menu, 0 = not placed yet. */
    int slot = 0;
    /** "" or "default" = needs the default node, "none" = free, anything else = that exact node. */
    String permission = "";
    /** The Shop plugin's name for this kit when it is linked to the shop ("Wemmbu" for a kit called "wembu"), else "". */
    String shop = "";
    /** Id of the screenshot kit this one was matched with (cooldown, colour and price already applied), else "". */
    String known = "";
    /** True when an admin typed the name or colour by hand (display / color command): the sync keeps its hands off it. */
    boolean displayManual = false;
    final ItemStack[] items = new ItemStack[SIZE];

    Kit(String id) {
        this.id = id;
    }

    int itemCount() {
        int n = 0;
        for (ItemStack it : this.items) {
            if (it == null || it.getType().isAir()) {
                continue;
            }
            ++n;
        }
        return n;
    }

    /** Saves a copy of the player's current inventory into this kit. */
    void capture(Player p) {
        ItemStack[] now = p.getInventory().getContents();
        for (int i = 0; i < SIZE; ++i) {
            ItemStack it = i < now.length ? now[i] : null;
            this.items[i] = it == null || it.getType().isAir() ? null : it.clone();
        }
    }

    Material firstMaterial() {
        for (ItemStack it : this.items) {
            if (it == null || it.getType().isAir()) {
                continue;
            }
            return it.getType();
        }
        return null;
    }
}
