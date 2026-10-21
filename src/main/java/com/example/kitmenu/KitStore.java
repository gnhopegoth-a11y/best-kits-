package com.example.kitmenu;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/** Loads and saves kits.yml (the kits) and claims.yml (when each player last claimed a kit). */
final class KitStore {
    static final int MAX_PAGES = 10;
    static final int MAX_POSITIONS = 280;
    static final long TEN_MINUTES = 600L;

    private final KitMenuPlugin plugin;
    private final File kitFile;
    private final File claimFile;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private YamlConfiguration claims = new YamlConfiguration();
    private boolean cooldownsSet;

    KitStore(KitMenuPlugin plugin) {
        this.plugin = plugin;
        this.kitFile = new File(plugin.getDataFolder(), "kits.yml");
        this.claimFile = new File(plugin.getDataFolder(), "claims.yml");
    }

    List<Kit> all() {
        return new ArrayList<>(this.kits.values());
    }

    int size() {
        return this.kits.size();
    }

    /**
     * Finds a kit by the name an admin or player typed. The exact id first, then the same letters
     * ignoring case, spaces and underscores ("The_Director" finds "thedirector"), then the name of its shop link,
     * and last the same name with doubled letters folded ("wemmbu" finds "wembu").
     */
    Kit get(String name) {
        if (name == null) {
            return null;
        }
        Kit exact = this.kits.get(Util.id(name));
        if (exact != null) {
            return exact;
        }
        String want = NameMatch.key(name);
        if (want.isEmpty()) {
            return null;
        }
        for (Kit k : this.kits.values()) {
            if (NameMatch.key(k.id).equals(want)) {
                return k;
            }
        }
        for (Kit k : this.kits.values()) {
            if (!k.shop.isEmpty() && NameMatch.key(k.shop).equals(want)) {
                return k;
            }
        }
        String folded = NameMatch.squash(want);
        for (Kit k : this.kits.values()) {
            if (NameMatch.squash(NameMatch.key(k.id)).equals(folded)
                    || !k.shop.isEmpty() && NameMatch.squash(NameMatch.key(k.shop)).equals(folded)) {
                return k;
            }
        }
        return null;
    }

    /** The kits in menu order. Kits without a spot go after the last placed one. */
    Kit[] layout() {
        TreeMap<Integer, Kit> placed = new TreeMap<>();
        List<Kit> loose = new ArrayList<>();
        for (Kit kit : this.kits.values()) {
            if (kit.slot >= 1 && !placed.containsKey(kit.slot)) {
                placed.put(kit.slot, kit);
            } else {
                loose.add(kit);
            }
        }
        int next = placed.isEmpty() ? 1 : placed.lastKey() + 1;
        for (Kit kit : loose) {
            placed.put(next++, kit);
        }
        Kit[] array = new Kit[placed.isEmpty() ? 0 : placed.lastKey()];
        for (Map.Entry<Integer, Kit> e : placed.entrySet()) {
            array[e.getKey() - 1] = e.getValue();
        }
        return array;
    }

    Kit atPosition(int index) {
        Kit[] lay = this.layout();
        return index >= 0 && index < lay.length ? lay[index] : null;
    }

    int positionOf(Kit kit) {
        Kit[] lay = this.layout();
        for (int i = 0; i < lay.length; ++i) {
            if (lay[i] == kit) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Puts the kit on a spot (1-based). The kit already there moves to the old spot. */
    void place(Kit kit, int position) {
        Kit[] lay = this.layout();
        int old = this.positionOf(kit);
        Kit other = position <= lay.length ? lay[position - 1] : null;
        for (int i = 0; i < lay.length; ++i) {
            if (lay[i] == null) {
                continue;
            }
            lay[i].slot = i + 1;
        }
        if (other != null && other != kit) {
            other.slot = old;
        }
        kit.slot = position;
        this.save();
    }

    void add(Kit kit) {
        this.kits.put(kit.id, kit);
    }

    boolean remove(String name) {
        Kit k = this.get(name);
        if (k == null) {
            return false;
        }
        this.backup();
        this.kits.remove(k.id);
        this.forget(k.id);
        this.save();
        return true;
    }

    void setAllCooldowns(long seconds) {
        for (Kit k : this.kits.values()) {
            k.cooldown = seconds;
        }
        this.save();
    }

    long lastClaim(UUID player, String kit) {
        return this.claims.getLong(player + "." + kit, 0L);
    }

    void setClaim(UUID player, String kit, long time) {
        this.claims.set(player + "." + kit, time);
        this.saveClaims();
    }

    void clearClaim(UUID player, String kit) {
        this.claims.set(player + "." + kit, null);
        this.saveClaims();
    }

    private void forget(String kit) {
        for (String uuid : this.claims.getKeys(false)) {
            this.claims.set(uuid + "." + kit, null);
        }
        this.saveClaims();
    }

    void load() {
        this.plugin.getDataFolder().mkdirs();
        this.kits.clear();
        if (!this.kitFile.exists()) {
            this.seed();
            // the seeded kits already have their own cooldowns: skip the one-time "everything is 10m" migration
            this.cooldownsSet = true;
            this.save();
        }
        YamlConfiguration c = YamlConfiguration.loadConfiguration(this.kitFile);
        this.cooldownsSet = c.getBoolean("meta.cooldown-10m", false);
        ConfigurationSection root = c.getConfigurationSection("kits");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                Kit k = new Kit(Util.id(id));
                k.display = s.getString("display", "<#9863E7><b>" + id);
                k.icon = s.getString("icon", "IRON_SWORD");
                k.iconItem = s.getString("icon-item", "");
                k.cooldown = s.getLong("cooldown", this.defaultCooldown());
                k.slot = s.getInt("slot", 0);
                k.permission = s.getString("permission", "");
                k.shop = s.getString("shop", "");
                k.known = s.getString("known", "");
                k.displayManual = s.getBoolean("display-manual", false);
                ConfigurationSection items = s.getConfigurationSection("items");
                if (items != null) {
                    for (String key : items.getKeys(false)) {
                        try {
                            int slot = Integer.parseInt(key);
                            if (slot < 0 || slot >= Kit.SIZE) {
                                continue;
                            }
                            k.items[slot] = Util.decode(items.getString(key));
                        } catch (NumberFormatException ignored) {
                            // not a slot number, skip it
                        }
                    }
                }
                this.kits.put(k.id, k);
            }
        }
        this.claims = YamlConfiguration.loadConfiguration(this.claimFile);
        if (!this.cooldownsSet) {
            // one-time migration: every kit gets the 10 minute cooldown once
            for (Kit k : this.kits.values()) {
                k.cooldown = TEN_MINUTES;
            }
            this.cooldownsSet = true;
            this.save();
        }
    }

    void save() {
        YamlConfiguration c = new YamlConfiguration();
        c.set("meta.cooldown-10m", this.cooldownsSet);
        for (Kit k : this.kits.values()) {
            String base = "kits." + k.id;
            c.set(base + ".display", k.display);
            c.set(base + ".icon", k.icon);
            if (!k.iconItem.isEmpty()) {
                c.set(base + ".icon-item", k.iconItem);
            }
            c.set(base + ".cooldown", k.cooldown);
            if (k.slot > 0) {
                c.set(base + ".slot", k.slot);
            }
            c.set(base + ".permission", k.permission);
            if (!k.shop.isEmpty()) {
                c.set(base + ".shop", k.shop);
            }
            if (!k.known.isEmpty()) {
                c.set(base + ".known", k.known);
            }
            if (k.displayManual) {
                c.set(base + ".display-manual", true);
            }
            for (int i = 0; i < Kit.SIZE; ++i) {
                ItemStack it = k.items[i];
                if (it == null || it.getType().isAir()) {
                    continue;
                }
                c.set(base + ".items." + i, Util.encode(it));
            }
        }
        try {
            c.save(this.kitFile);
        } catch (IOException e) {
            this.plugin.getLogger().warning("Could not save kits.yml: " + e.getMessage());
        }
    }

    private void saveClaims() {
        try {
            this.claims.save(this.claimFile);
        } catch (IOException e) {
            this.plugin.getLogger().warning("Could not save claims.yml: " + e.getMessage());
        }
    }

    /** Copies kits.yml to kits.yml.before-sync the first time, so the very first sync can always be undone. */
    void backupBeforeSync() {
        copyOnce(this.kitFile, new File(this.plugin.getDataFolder(), "kits.yml.before-sync"));
    }

    /** Copies a file unless the copy already exists or there is nothing to copy. */
    static void copyOnce(File from, File to) {
        try {
            if (from.exists() && !to.exists()) {
                Files.copy(from.toPath(), to.toPath());
            }
        } catch (IOException e) {
            // a missing backup must never stop the work
        }
    }

    private void backup() {
        try {
            if (this.kitFile.exists()) {
                Files.copy(this.kitFile.toPath(), new File(this.plugin.getDataFolder(), "kits.yml.bak").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            this.plugin.getLogger().warning("Could not back up kits.yml: " + e.getMessage());
        }
    }

    /** First start: create every kit from the screenshots, so the menu is not empty. The sync links them to the shop. */
    private void seed() {
        for (KnownKits.Known known : KnownKits.ALL) {
            Kit k = new Kit(known.id());
            k.display = known.display();
            k.icon = known.id().equals("parrot") ? "SPORE_BLOSSOM" : "IRON_SWORD";
            k.cooldown = known.cooldown() >= 0L ? known.cooldown() : this.defaultCooldown();
            k.permission = known.free() ? "none" : "";
            this.add(k);
        }
    }

    long defaultCooldown() {
        long seconds = Util.parseSeconds(this.plugin.getConfig().getString("default-cooldown", "10m"));
        return seconds < 0L ? TEN_MINUTES : seconds;
    }
}
