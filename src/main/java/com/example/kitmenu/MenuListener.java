package com.example.kitmenu;

import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** Handles clicks in the kit menus. Every click inside our menus is cancelled. */
final class MenuListener implements Listener {
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int CLAIM_SLOT = 49;

    private final KitMenuPlugin plugin;

    MenuListener(KitMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof Menus.Holder)) {
            return;
        }
        Menus.Holder h = (Menus.Holder) holder;
        e.setCancelled(true);
        HumanEntity who = e.getWhoClicked();
        if (!(who instanceof Player)) {
            return;
        }
        Player p = (Player) who;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        switch (h.type) {
            case LIST -> listClick(p, h, e.getRawSlot(), e.getClick());
            case PREVIEW -> previewClick(p, h, e.getRawSlot());
            case ARRANGE -> arrangeClick(p, h, e.getRawSlot(), e.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menus.Holder) {
            e.setCancelled(true);
        }
    }

    private void listClick(Player p, Menus.Holder h, int slot, ClickType click) {
        Menus menus = plugin.menus();
        int pages = Menus.pages(plugin.store().layout().length);
        if (slot == PREV_SLOT) {
            if (h.page > 0) {
                --h.page;
                click(p);
                plugin.later(() -> menus.fillList(p, h));
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            if (h.page < pages - 1) {
                ++h.page;
                click(p);
                plugin.later(() -> menus.fillList(p, h));
            }
            return;
        }
        int i = Menus.indexOf(slot);
        if (i < 0) {
            return;
        }
        Kit kit = plugin.store().atPosition(h.page * Menus.PER_PAGE + i);
        if (kit == null) {
            return;
        }
        if (click.isRightClick()) {
            // everybody can look inside a kit, locked or not (like on UnstablePVP)
            click(p);
            int back = h.page;
            plugin.later(() -> menus.openPreview(p, kit, back));
        } else if (click.isLeftClick()) {
            plugin.claim(p, kit);
            plugin.later(() -> menus.fillList(p, h));
        }
    }

    private void previewClick(Player p, Menus.Holder h, int slot) {
        Menus menus = plugin.menus();
        if (slot == PREV_SLOT) {
            click(p);
            int back = h.page;
            plugin.later(() -> menus.openList(p, back));
        } else if (slot == CLAIM_SLOT) {
            Kit kit = plugin.store().get(h.kit);
            if (kit != null) {
                plugin.claim(p, kit);
            }
        }
    }

    private void arrangeClick(Player p, Menus.Holder h, int slot, ClickType click) {
        if (!plugin.isAdmin(p)) {
            return;
        }
        Menus menus = plugin.menus();
        var store = plugin.store();
        int pages = Menus.arrangePages(store.layout().length);
        if (slot == PREV_SLOT) {
            if (h.page > 0) {
                --h.page;
                click(p);
                plugin.later(() -> menus.fillArrange(h));
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            if (h.page < pages - 1) {
                ++h.page;
                click(p);
                plugin.later(() -> menus.fillArrange(h));
            }
            return;
        }
        int i = Menus.indexOf(slot);
        if (i < 0) {
            return;
        }
        int index = h.page * Menus.PER_PAGE + i;
        Kit here = store.atPosition(index);
        if (click.isRightClick()) {
            h.selected = null;
            click(p);
            plugin.later(() -> menus.fillArrange(h));
            return;
        }
        if (!click.isLeftClick()) {
            return;
        }
        if (h.selected == null) {
            if (here == null) {
                return;
            }
            h.selected = here.id;
            click(p);
            plugin.later(() -> menus.fillArrange(h));
            return;
        }
        Kit moving = store.get(h.selected);
        h.selected = null;
        if (moving != null && moving != here) {
            store.place(moving, index + 1);
            String text = "<green>Moved <white>" + Util.plain(moving.display) + "<green> to spot <white>" + (index + 1) + "<green>.";
            if (here != null) {
                text = "<green>Moved <white>" + Util.plain(moving.display) + "<green> to spot <white>" + (index + 1)
                        + "<green> and <white>" + Util.plain(here.display) + "<green> took its old place.";
            }
            Util.info(p, text);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        } else {
            click(p);
        }
        plugin.later(() -> menus.fillArrange(h));
    }

    private static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }
}
