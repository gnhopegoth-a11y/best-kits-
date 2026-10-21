package com.example.kitmenu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * KitMenu: /kit opens the kit menu, /unstablekits is the admin command.
 * This plugin does not need any other kit plugin.
 */
public final class KitMenuPlugin extends JavaPlugin implements TabExecutor {
    /** Default permission prefix: a "default" kit needs this + its id (e.g. kitmenu.kit.parrot). */
    static final String DEFAULT_PERMISSION_PREFIX = "kitmenu.kit.";

    private KitStore store;
    private Menus menus;

    /** Admins who switched their own bypass off with /unstablekits bypass off (kept until the server restarts). */
    private final Set<UUID> bypassOff = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new KitStore(this);
        store.load();
        menus = new Menus(this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        PluginCommand kit = getCommand("kit");
        if (kit != null) {
            kit.setExecutor(this);
            kit.setTabCompleter(this);
        }
        PluginCommand admin = getCommand("unstablekits");
        if (admin != null) {
            UnstableKitsCommand handler = new UnstableKitsCommand(this);
            admin.setExecutor(handler);
            admin.setTabCompleter(handler);
        }
        getLogger().info("Loaded " + store.size() + " kit(s).");
        // one tick later, so the Shop plugin has certainly finished loading
        later(this::autoSync);
    }

    void reloadAll() {
        reloadConfig();
        store.load();
        autoSync();
    }

    KitStore store() {
        return store;
    }

    Menus menus() {
        return menus;
    }

    /** The console, and the names listed under "admins" in config.yml. */
    boolean isAdmin(CommandSender s) {
        if (s instanceof ConsoleCommandSender) {
            return true;
        }
        if (!(s instanceof Player)) {
            return false;
        }
        Player p = (Player) s;
        List<String> names = getConfig().getStringList("admins");
        if (names.isEmpty()) {
            names = List.of("rem0i", "Rem0iX");
        }
        for (String n : names) {
            if (n.equalsIgnoreCase(p.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when this player gets the admin bypass: every kit unlocked and no cooldowns.
     * They must be listed under "admins", "admin-bypass" must be true in config.yml,
     * and they must not have switched it off with /unstablekits bypass off.
     */
    boolean bypass(Player p) {
        return isAdmin(p) && getConfig().getBoolean("admin-bypass", true) && !bypassOff.contains(p.getUniqueId());
    }

    /** Turns the admin's own bypass on or off. Returns whether the bypass is on afterwards. */
    boolean setBypass(Player p, boolean on) {
        if (on) {
            bypassOff.remove(p.getUniqueId());
        } else {
            bypassOff.add(p.getUniqueId());
        }
        return bypass(p);
    }

    /** Can this player claim the kit? Admins with the bypass can claim every kit. */
    boolean canUse(Player p, Kit k) {
        return bypass(p) || hasAccess(p, k);
    }

    /** True when the kit looks unlocked to this admin only because of the bypass. */
    boolean unlockedByBypassOnly(Player p, Kit k) {
        return bypass(p) && !hasAccess(p, k);
    }

    /**
     * The permission prefixes that unlock a kit set to "default": this plugin's own prefix, the one the Shop plugin
     * hands out when a kit is bought, and the extra ones listed under "accepted-prefixes" in config.yml.
     */
    List<String> acceptedPrefixes() {
        List<String> out = new ArrayList<>();
        out.add(getConfig().getString("permission-prefix", DEFAULT_PERMISSION_PREFIX));
        String shop = ShopLink.permissionPrefix();
        if (shop != null) {
            out.add(shop);
        }
        out.addAll(getConfig().getStringList("accepted-prefixes"));
        List<String> unique = new ArrayList<>();
        for (String prefix : out) {
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            boolean seen = false;
            for (String have : unique) {
                if (have.equalsIgnoreCase(prefix)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                unique.add(prefix);
            }
        }
        return unique;
    }

    /**
     * The permission rules on their own, without the admin bypass.
     * "none" = everyone. "" or "default" = any accepted prefix + the kit's id or the name of its shop link,
     * ignoring case, spaces, underscores and doubled letters (so a bought "playerkits.kit.Wemmbu" unlocks the kit "wembu").
     * Anything else = needs exactly that permission node.
     */
    boolean hasAccess(Player p, Kit k) {
        String node = k.permission == null ? "" : k.permission.trim();
        if (node.equalsIgnoreCase("none")) {
            return true;
        }
        if (!node.isEmpty() && !node.equalsIgnoreCase("default")) {
            return hasNode(p, node);
        }
        List<String> prefixes = acceptedPrefixes();
        List<String> names = new ArrayList<>();
        names.add(k.id);
        if (!k.shop.isEmpty()) {
            names.add(k.shop);
        }
        for (String prefix : prefixes) {
            for (String name : names) {
                if (p.hasPermission(prefix + name)) {
                    return true;
                }
            }
        }
        Set<String> wanted = new HashSet<>();
        for (String name : names) {
            wanted.add(NameMatch.squash(NameMatch.key(name)));
        }
        for (PermissionAttachmentInfo info : p.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String perm = info.getPermission().toLowerCase(Locale.ROOT);
            for (String prefix : prefixes) {
                String start = prefix.toLowerCase(Locale.ROOT);
                if (perm.startsWith(start) && wanted.contains(NameMatch.squash(NameMatch.key(perm.substring(start.length()))))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** One exact permission node, ignoring case. */
    private boolean hasNode(Player p, String node) {
        if (p.hasPermission(node)) {
            return true;
        }
        String wanted = node.toLowerCase(Locale.ROOT);
        for (PermissionAttachmentInfo info : p.getEffectivePermissions()) {
            if (info.getValue() && info.getPermission().toLowerCase(Locale.ROOT).equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** Links kits to the shop and applies the screenshot values, when "auto-link" is on in config.yml. Quiet unless something changed. */
    void autoSync() {
        if (!getConfig().getBoolean("auto-link", true)) {
            return;
        }
        KitSync.Report report = KitSync.run(this, false, false, false);
        for (String line : report.plainLines) {
            getLogger().info("Sync: " + line);
        }
    }

    /** Seconds until the player may claim this kit again, 0 if now. */
    long cooldownLeft(Player p, Kit k) {
        if (k.cooldown <= 0L || bypass(p)) {
            return 0L;
        }
        long last = store.lastClaim(p.getUniqueId(), k.id);
        if (last <= 0L) {
            return 0L;
        }
        long leftMs = last + k.cooldown * 1000L - System.currentTimeMillis();
        return leftMs <= 0L ? 0L : (leftMs + 999L) / 1000L;
    }

    void claim(Player p, Kit k) {
        String name = Util.plain(k.display);
        if (!canUse(p, k)) {
            Util.info(p, "<red>You haven't unlocked <white>" + name + "<red>. Buy it in the <#9863E7>/shop<red>.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        if (k.itemCount() == 0) {
            Util.info(p, "<red>" + name + " <red>has no items yet.");
            return;
        }
        long left = cooldownLeft(p, k);
        if (left > 0L) {
            Util.info(p, "<red>You can claim <white>" + name + "<red> again in <white>" + Util.fmtSeconds(left) + "<red>.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        give(p, k);
        if (!bypass(p)) {
            store.setClaim(p.getUniqueId(), k.id, System.currentTimeMillis());
        }
        Util.info(p, "<green>You claimed <white>" + name + "<green>!");
        if (!hasAccess(p, k)) {
            // only possible through the admin bypass: say so, so nobody thinks the permission check is broken
            Util.info(p, "<yellow>Admin bypass is on: a normal player would not get this kit. "
                    + "<gray>Use <white>/unstablekits bypass off<gray> to test like a player.");
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    /** Puts the kit's items in the player's inventory. Full inventory: the rest is dropped at their feet. */
    private void give(Player p, Kit k) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < Kit.SIZE; ++i) {
            ItemStack it = k.items[i];
            if (it == null || it.getType().isAir()) {
                continue;
            }
            it = it.clone();
            // slots 36-40 are armor and offhand: put the item there if it is free
            if (i >= 36 && (inv.getItem(i) == null || inv.getItem(i).getType().isAir())) {
                inv.setItem(i, it);
                continue;
            }
            for (ItemStack leftover : inv.addItem(it).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player p = (Player) sender;
        if (args.length == 0) {
            menus.openList(p, 0);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(p);
            case "preview" -> {
                Kit k = args.length > 1 ? store.get(args[1]) : null;
                if (k == null) {
                    Util.info(p, "<red>Unknown kit. Use <white>/kit list<red>.");
                } else {
                    menus.openPreview(p, k, 0);
                }
            }
            case "claim" -> claimByName(p, args.length > 1 ? args[1] : null);
            default -> claimByName(p, args[0]);
        }
        return true;
    }

    private void claimByName(Player p, String name) {
        Kit k = name == null ? null : store.get(name);
        if (k == null) {
            Util.info(p, "<red>Unknown kit. Use <white>/kit list<red>.");
            return;
        }
        if (canUse(p, k)) {
            claim(p, k);
            return;
        }
        // Not allowed: claim() shows the "not unlocked" message. Run it a moment later like before.
        later(15L, () -> {
            if (p.isOnline()) {
                claim(p, k);
            }
        });
    }

    private void list(Player p) {
        Util.info(p, "<gray>Your kits <dark_gray>(green = unlocked)<gray>:");
        for (Kit k : store.all()) {
            String color = canUse(p, k) ? "<green>" : "<red>";
            Util.say(p, "  " + color + Util.plain(k.display));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 0) {
            return out;
        }
        if (args.length == 1) {
            out.add("list");
            out.add("preview");
            out.add("claim");
            for (Kit k : store.all()) {
                out.add(k.id);
            }
        } else if (args.length == 2) {
            for (Kit k : store.all()) {
                out.add(k.id);
            }
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(typed));
        return out;
    }

    void later(Runnable task) {
        Bukkit.getScheduler().runTask(this, task);
    }

    void later(long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(this, task, ticks);
    }
}
