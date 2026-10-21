package com.example.kitmenu;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

/**
 * Reads and edits the kits in the separate "Shop" plugin (its config section "kits").
 * Every method does nothing if the Shop plugin is not running.
 */
final class ShopLink {
    static final int MAX_KITS = 28;

    private ShopLink() {
    }

    static Plugin shop() {
        Plugin p = Bukkit.getPluginManager().getPlugin("Shop");
        return p != null && p.isEnabled() ? p : null;
    }

    static boolean available() {
        return shop() != null;
    }

    private static ConfigurationSection kits(Plugin shop) {
        ConfigurationSection s = shop.getConfig().getConfigurationSection("kits");
        return s != null ? s : shop.getConfig().createSection("kits");
    }

    /**
     * The name as the shop spells it. Case, spaces, underscores and doubled letters do not matter
     * ("wembu" finds "Wemmbu"). null if the shop has no such kit.
     */
    static String key(String kit) {
        Plugin shop = shop();
        if (shop == null || kit == null) {
            return null;
        }
        ConfigurationSection kits = kits(shop);
        for (String k : kits.getKeys(false)) {
            if (k.equalsIgnoreCase(kit)) {
                return k;
            }
        }
        String want = NameMatch.squash(NameMatch.key(kit));
        if (want.isEmpty()) {
            return null;
        }
        for (String k : kits.getKeys(false)) {
            if (NameMatch.squash(NameMatch.key(k)).equals(want)) {
                return k;
            }
        }
        return null;
    }

    /** The permission prefix the Shop gives when a kit is bought (its config "permission-prefix"), null without a shop. */
    static String permissionPrefix() {
        Plugin shop = shop();
        return shop == null ? null : shop.getConfig().getString("permission-prefix", "playerkits.kit.");
    }

    /** The shop's kit section for editing several things before one {@link #save()}. null without a shop. */
    static ConfigurationSection section() {
        Plugin shop = shop();
        return shop == null ? null : kits(shop);
    }

    /** Keeps a copy of the Shop's config.yml as config.yml.before-kitmenu-sync, once, before the first sync edits it. */
    static void backupOnce() {
        Plugin shop = shop();
        if (shop != null) {
            KitStore.copyOnce(new File(shop.getDataFolder(), "config.yml"),
                    new File(shop.getDataFolder(), "config.yml.before-kitmenu-sync"));
        }
    }

    /** Writes the Shop plugin's config.yml. */
    static void save() {
        Plugin shop = shop();
        if (shop != null) {
            shop.saveConfig();
        }
    }

    /** The price of a shop kit, -1 if it is not in the shop. */
    static long price(String kit) {
        Plugin shop = shop();
        String key = key(kit);
        return shop == null || key == null ? -1L : kits(shop).getLong(key + ".price", 0L);
    }

    /** The MiniMessage name the shop shows for this kit, "" if it has none. */
    static String displayName(String kit) {
        Plugin shop = shop();
        String key = key(kit);
        return shop == null || key == null ? "" : kits(shop).getString(key + ".name", "");
    }

    static boolean setName(String kit, String mini) {
        Plugin shop = shop();
        String key = key(kit);
        if (shop == null || key == null) {
            return false;
        }
        shop.getConfig().set("kits." + key + ".name", mini);
        shop.saveConfig();
        return true;
    }

    static boolean has(String kit) {
        return key(kit) != null;
    }

    static int count() {
        Plugin shop = shop();
        return shop == null ? 0 : kits(shop).getKeys(false).size();
    }

    static boolean remove(String kit) {
        Plugin shop = shop();
        String key = key(kit);
        if (shop == null || key == null) {
            return false;
        }
        shop.getConfig().set("kits." + key, null);
        shop.saveConfig();
        return true;
    }

    static boolean setPrice(String kit, long price) {
        Plugin shop = shop();
        String key = key(kit);
        if (shop == null || key == null) {
            return false;
        }
        shop.getConfig().set("kits." + key + ".price", price);
        shop.saveConfig();
        return true;
    }

    /** Adds the kit to the shop, or changes its price if it is already there (then returns false). */
    static boolean add(String kit, long price, Material icon) {
        Plugin shop = shop();
        if (shop == null) {
            return false;
        }
        String key = key(kit);
        if (key != null) {
            setPrice(key, price);
            return false;
        }
        shop.getConfig().set("kits." + kit + ".price", price);
        shop.getConfig().set("kits." + kit + ".icon", icon.name());
        shop.getConfig().set("kits." + kit + ".slot", freeSlot(shop));
        shop.saveConfig();
        return true;
    }

    static boolean setIcon(String kit, Material icon) {
        Plugin shop = shop();
        String key = key(kit);
        if (shop == null || key == null) {
            return false;
        }
        shop.getConfig().set("kits." + key + ".icon", icon.name());
        shop.saveConfig();
        return true;
    }

    static boolean setStock(String kit, int stock) {
        Plugin shop = shop();
        String key = key(kit);
        if (shop == null || key == null) {
            return false;
        }
        shop.getConfig().set("kits." + key + ".stock", stock);
        shop.saveConfig();
        return true;
    }

    /** One line per shop kit, cheapest first, for /unstablekits shop list. */
    static List<String> lines() {
        List<String> out = new ArrayList<>();
        Plugin shop = shop();
        if (shop == null) {
            return out;
        }
        ConfigurationSection kits = kits(shop);
        List<String> names = new ArrayList<>(kits.getKeys(false));
        names.sort((a, b) -> Long.compare(kits.getLong(a + ".price"), kits.getLong(b + ".price")));
        for (String n : names) {
            int stock = kits.getInt(n + ".stock", -1);
            out.add("<white>" + n + " <dark_gray>- <#ffd34e>" + kits.getLong(n + ".price")
                    + " <dark_gray>| <gray>" + kits.getString(n + ".icon", "IRON_SWORD")
                    + " <dark_gray>| <gray>stock " + (stock < 0 ? "unlimited" : String.valueOf(stock)));
        }
        return out;
    }

    static List<String> names() {
        Plugin shop = shop();
        return shop == null ? new ArrayList<>() : new ArrayList<>(kits(shop).getKeys(false));
    }

    private static int freeSlot(Plugin shop) {
        Set<Integer> used = new HashSet<>();
        ConfigurationSection kits = kits(shop);
        for (String k : kits.getKeys(false)) {
            used.add(kits.getInt(k + ".slot", -1));
        }
        int slot = 0;
        while (used.contains(slot)) {
            ++slot;
        }
        return slot;
    }
}
