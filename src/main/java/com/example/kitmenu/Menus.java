package com.example.kitmenu;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Builds the three inventory menus: the kit list, the kit preview and the arrange screen. */
final class Menus {
    private static final String LIST_TITLE = "<#43286b><b>ᴋɪᴛs";
    private static final String PREVIEW_TITLE = "<#43286b><b>ᴘʀᴇᴠɪᴇᴡ</b> <#6b4aa0>";
    private static final String ARRANGE_TITLE = "<#43286b><b>ᴀʀʀᴀɴɢᴇ ᴋɪᴛs";
    private static final int MAX_DOT_PAGES = 5;

    /** Inventory slots of the 7 x 4 kit grid on one page. */
    static final int[] SLOTS = buildSlots();
    static final int PER_PAGE = SLOTS.length;
    static final int PREV = 45;
    static final int COUNTER = 49;
    static final int NEXT = 53;
    static final int BACK = 45;
    static final int CLAIM = 49;
    static final int INFO = 53;
    static final int HELP = 4;

    private final KitMenuPlugin plugin;

    Menus(KitMenuPlugin plugin) {
        this.plugin = plugin;
    }

    private static int[] buildSlots() {
        int[] slots = new int[28];
        int n = 0;
        for (int row = 1; row <= 4; ++row) {
            for (int col = 1; col <= 7; ++col) {
                slots[n++] = row * 9 + col;
            }
        }
        return slots;
    }

    static int pages(int positions) {
        return Math.max(1, (positions + PER_PAGE - 1) / PER_PAGE);
    }

    static int arrangePages(int positions) {
        return Math.min(10, pages(positions) + 1);
    }

    static int indexOf(int slot) {
        for (int i = 0; i < SLOTS.length; ++i) {
            if (SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    void openList(Player p, int page) {
        Holder h = new Holder(Type.LIST, page, null);
        h.inventory = Bukkit.createInventory(h, 54, Util.MM.deserialize(LIST_TITLE));
        fillList(p, h);
        p.openInventory(h.inventory);
    }

    void fillList(Player p, Holder h) {
        Inventory inv = h.inventory;
        inv.clear();
        Kit[] layout = plugin.store().layout();
        int pages = pages(layout.length);
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        frame(inv);
        for (int i = 0; i < PER_PAGE; ++i) {
            int index = h.page * PER_PAGE + i;
            if (index >= layout.length) {
                break;
            }
            if (layout[index] == null) {
                continue;
            }
            inv.setItem(SLOTS[i], kitIcon(p, layout[index]));
        }
        navigation(inv, h.page, pages, plugin.store().size() + " kits in total");
    }

    /**
     * The icon of one kit in the list, laid out like the UnstablePVP tooltip. A kit the player cannot use is a
     * barrier that keeps the kit's own colored name, shows the cooldown and says it is not unlocked.
     */
    private ItemStack kitIcon(Player p, Kit k) {
        String cooldown = "<dark_gray>▪ <white>Cooldown: <#FFD35A>" + Util.fmtSeconds(k.cooldown);
        if (!plugin.canUse(p, k)) {
            return Util.item(Material.BARRIER, k.display, cooldown, "", "<red>✖ You have not unlocked this kit", "",
                    "<gray>Right-click to <white>preview");
        }
        long left = plugin.cooldownLeft(p, k);
        String state = left > 0L ? "<red>Ready in <white>" + Util.fmtSeconds(left) : "<green>✔ Ready to claim";
        List<String> lore = new ArrayList<>(List.of(cooldown, "<dark_gray>▪ <white>Items: <#FFD35A>" + k.itemCount(),
                "", state, "", "<gray>Left-click to <white>take the kit", "<gray>Right-click to <white>preview"));
        if (plugin.unlockedByBypassOnly(p, k)) {
            // an admin sees this kit unlocked only because of the bypass: a normal player would see a barrier
            lore.add("");
            lore.add("<yellow>Admin bypass is on");
            lore.add("<gray>Players without the permission see a barrier.");
        }
        return icon(k, k.display, lore.toArray(new String[0]));
    }

    /** The kit's saved item if it has one, otherwise the chosen material (default iron sword). */
    private static ItemStack icon(Kit k, String name, String... lore) {
        ItemStack base = Util.decode(k.iconItem);
        if (base != null) {
            return Util.itemFrom(base, name, lore);
        }
        return Util.item(Util.material(k.icon, Material.IRON_SWORD), name, lore);
    }

    private void navigation(Inventory inv, int page, int pages, String counterText) {
        if (page > 0) {
            inv.setItem(PREV, Util.model(Util.item(Material.ARROW, "<#9863E7><b>« Previous page",
                    "<gray>Go to page <white>" + page), "arrow_left"));
        } else {
            inv.setItem(PREV, Util.model(Util.item(Material.ARROW, "<dark_gray>« Previous page"), "arrow_left_off"));
        }
        if (page < pages - 1) {
            inv.setItem(NEXT, Util.model(Util.item(Material.ARROW, "<#9863E7><b>Next page »",
                    "<gray>Go to page <white>" + (page + 2)), "arrow_right"));
        } else {
            inv.setItem(NEXT, Util.model(Util.item(Material.ARROW, "<dark_gray>Next page »"), "arrow_right_off"));
        }
        ItemStack counter = Util.item(Material.PAPER,
                "<#9863E7><b>Page <white>" + (page + 1) + "<gray>/<white>" + pages, "<gray>" + counterText);
        if (pages <= MAX_DOT_PAGES) {
            Util.model(counter, "page_" + (page + 1) + "_" + pages);
        }
        inv.setItem(COUNTER, counter);
    }

    void openArrange(Player p, int page) {
        Holder h = new Holder(Type.ARRANGE, page, null);
        h.inventory = Bukkit.createInventory(h, 54, Util.MM.deserialize(ARRANGE_TITLE));
        fillArrange(h);
        p.openInventory(h.inventory);
    }

    void fillArrange(Holder h) {
        Inventory inv = h.inventory;
        inv.clear();
        Kit[] layout = plugin.store().layout();
        int pages = arrangePages(layout.length);
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        frame(inv);
        Kit selected = h.selected == null ? null : plugin.store().get(h.selected);
        for (int i = 0; i < PER_PAGE; ++i) {
            int index = h.page * PER_PAGE + i;
            int position = index + 1;
            Kit k = index < layout.length ? layout[index] : null;
            if (k == null) {
                inv.setItem(SLOTS[i], Util.item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Spot " + position, "",
                        selected != null ? "<#9863E7>Click <gray>to put the kit here" : "<gray>Empty"));
                continue;
            }
            if (k == selected) {
                inv.setItem(SLOTS[i], icon(k, "<green><b>▶ " + Util.plain(k.display), "",
                        "<gray>Spot <white>" + position, "", "<green>Picked up",
                        "<gray>Click a spot to put it there.", "<gray>Right click to let go."));
                continue;
            }
            inv.setItem(SLOTS[i], icon(k, k.display, "", "<gray>Spot <white>" + position, "",
                    selected != null ? "<#9863E7>Click <gray>to swap with the picked kit" : "<#9863E7>Click <gray>to pick it up"));
        }
        String picked = selected == null ? "<gray>Nothing picked up yet."
                : "<gray>Picked up: <white>" + Util.plain(selected.display);
        inv.setItem(HELP, Util.item(Material.PAPER, "<#9863E7><b>Arrange kits", "",
                "<gray>1. Click a kit to pick it up.", "<gray>2. Click the spot it should go to.",
                "<gray>A kit already there swaps places.", "", picked));
        navigation(inv, h.page, pages, "Last page is empty: use it for a new page");
    }

    void openPreview(Player p, Kit k, int backPage) {
        Holder h = new Holder(Type.PREVIEW, backPage, k.id);
        Inventory inv = Bukkit.createInventory(h, 54, Util.MM.deserialize(PREVIEW_TITLE + Util.plain(k.display)));
        h.inventory = inv;
        for (int i = 0; i < 27; ++i) {
            put(inv, i, k.items[9 + i]);
        }
        for (int i = 0; i < 9; ++i) {
            put(inv, 27 + i, k.items[i]);
        }
        put(inv, 36, k.items[39]);
        put(inv, 37, k.items[38]);
        put(inv, 38, k.items[37]);
        put(inv, 39, k.items[36]);
        put(inv, 40, k.items[40]);
        for (int slot = 41; slot < 54; ++slot) {
            inv.setItem(slot, filler());
        }
        inv.setItem(BACK, Util.model(Util.item(Material.ARROW, "<#9863E7><b>« Back", "<gray>Return to the kit list"),
                "arrow_left"));
        inv.setItem(CLAIM, claimButton(p, k));
        inv.setItem(INFO, icon(k, k.display, "", "<gray>Items: <white>" + k.itemCount(),
                "<gray>Cooldown: <white>" + Util.fmtCooldown(k.cooldown)));
        p.openInventory(inv);
    }

    private ItemStack claimButton(Player p, Kit k) {
        if (!plugin.canUse(p, k)) {
            return Util.item(Material.BARRIER, "<red><b>Locked", "<gray>Buy this kit in the <#9863E7>/shop<gray>.");
        }
        long left = plugin.cooldownLeft(p, k);
        if (left > 0L) {
            return Util.item(Material.CLOCK, "<red><b>On cooldown", "<gray>Ready in <white>" + Util.fmtSeconds(left));
        }
        return Util.item(Material.LIME_DYE, "<green><b>Claim kit", "<gray>Click to claim <white>" + Util.plain(k.display));
    }

    private static void put(Inventory inv, int slot, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            inv.setItem(slot, item.clone());
        }
    }

    private void frame(Inventory inv) {
        for (int slot = 0; slot < 54; ++slot) {
            if (indexOf(slot) >= 0) {
                continue;
            }
            inv.setItem(slot, filler());
        }
    }

    private static ItemStack filler() {
        return Util.model(Util.item(Material.PURPLE_STAINED_GLASS_PANE, " "), "frame");
    }

    static final class Holder implements InventoryHolder {
        final Type type;
        int page;
        final String kit;
        String selected;
        Inventory inventory;

        Holder(Type type, int page, String kit) {
            this.type = type;
            this.page = page;
            this.kit = kit;
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    enum Type {
        LIST,
        PREVIEW,
        ARRANGE
    }
}
