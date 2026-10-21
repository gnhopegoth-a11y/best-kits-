package com.example.kitmenu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Links the kit menu to the Shop and to the kit table, by name.
 * <ul>
 * <li>A kit called "wembu" is linked to the shop kit "Wemmbu" (typos, case, spaces and underscores do not matter).</li>
 * <li>A kit that matches a screenshot kit gets its colour, its cooldown and, in the shop, its price.</li>
 * <li>The shop shows the same coloured name as the menu.</li>
 * </ul>
 * Everything is applied once per kit, so later changes by hand are not overwritten. Only {@code force} redoes it.
 */
final class KitSync {
    private KitSync() {
    }

    /** What a run did (or would do). */
    static final class Report {
        /** One line per kit that changed, as MiniMessage. */
        final List<String> lines = new ArrayList<>();
        /** The same lines without colours, for the console. */
        final List<String> plainLines = new ArrayList<>();
        /** Kits without a shop kit. */
        final List<String> notInShop = new ArrayList<>();
        /** Shop kits without a kit in the menu. */
        final List<String> shopOnly = new ArrayList<>();
        int linked;
        int applied;
        int added;

        boolean changed() {
            return !lines.isEmpty();
        }
    }

    /**
     * @param dry        only report, change nothing
     * @param force      do it again for kits that were already done (names and colours set by hand are still kept)
     * @param addMissing put kits that have a known price but are not in the shop into the shop
     */
    static Report run(KitMenuPlugin plugin, boolean dry, boolean force, boolean addMissing) {
        KitStore store = plugin.store();
        Report report = new Report();
        if (!dry) {
            // the first sync changes a lot at once: keep the files as they were
            store.backupBeforeSync();
            ShopLink.backupOnce();
        }
        List<Kit> kits = store.all();
        boolean shopOn = ShopLink.available();
        ConfigurationSection shopSection = shopOn ? ShopLink.section() : null;
        boolean shopDirty = false;
        boolean kitsDirty = false;

        // 1. which shop kit belongs to which menu kit
        Map<Kit, String> link = new LinkedHashMap<>();
        Set<String> newlyLinked = new HashSet<>();
        if (shopOn) {
            List<String> shopKeys = ShopLink.names();
            Set<String> used = new HashSet<>();
            List<Kit> open = new ArrayList<>();
            for (Kit k : kits) {
                String live = k.shop.isEmpty() ? null : exactKey(shopKeys, k.shop);
                if (live != null) {
                    link.put(k, live);
                    used.add(live.toLowerCase(Locale.ROOT));
                } else {
                    open.add(k);
                }
            }
            List<String> freeKeys = new ArrayList<>();
            for (String key : shopKeys) {
                if (!used.contains(key.toLowerCase(Locale.ROOT))) {
                    freeKeys.add(key);
                }
            }
            List<String> openNames = new ArrayList<>();
            for (Kit k : open) {
                openNames.add(k.id);
            }
            int[] pairs = NameMatch.pairSimple(openNames, freeKeys);
            for (int i = 0; i < open.size(); ++i) {
                if (pairs[i] >= 0) {
                    link.put(open.get(i), freeKeys.get(pairs[i]));
                    newlyLinked.add(open.get(i).id);
                }
            }
        }

        // 2. which screenshot kit belongs to which menu kit
        List<Kit> pending = new ArrayList<>();
        Set<String> claimed = new HashSet<>();
        for (Kit k : kits) {
            if (k.known.isEmpty() || force) {
                pending.add(k);
            } else {
                claimed.add(k.known);
            }
        }
        List<KnownKits.Known> freeKnown = new ArrayList<>();
        for (KnownKits.Known known : KnownKits.ALL) {
            if (!claimed.contains(known.id())) {
                freeKnown.add(known);
            }
        }
        List<String> pendingNames = new ArrayList<>();
        for (Kit k : pending) {
            pendingNames.add(k.id);
        }
        int[] knownPairs = NameMatch.pair(pendingNames, KnownKits.spellings(freeKnown));
        Map<Kit, KnownKits.Known> known = new LinkedHashMap<>();
        for (int i = 0; i < pending.size(); ++i) {
            if (knownPairs[i] >= 0) {
                known.put(pending.get(i), freeKnown.get(knownPairs[i]));
            }
        }

        // the table entry each kit belongs to: the ones matched just now, and the ones matched in an earlier run
        Map<Kit, KnownKits.Known> entry = new LinkedHashMap<>(known);
        for (Kit k : kits) {
            if (!entry.containsKey(k) && !k.known.isEmpty()) {
                for (KnownKits.Known candidate : KnownKits.ALL) {
                    if (candidate.id().equals(k.known)) {
                        entry.put(k, candidate);
                        break;
                    }
                }
            }
        }

        // 3. change what has to change
        for (Kit k : kits) {
            List<String> what = new ArrayList<>();
            String key = link.get(k);
            boolean fresh = key != null && newlyLinked.contains(k.id);
            if (fresh) {
                if (!dry) {
                    k.shop = key;
                    kitsDirty = true;
                }
                what.add("linked to shop kit <white>" + key);
                ++report.linked;
            }
            KnownKits.Known kn = known.get(k);
            boolean displayChanged = false;
            if (kn != null) {
                if (kn.cooldown() >= 0L && k.cooldown != kn.cooldown()) {
                    what.add("cooldown <white>" + Util.fmtSeconds(k.cooldown) + "<gray> to <white>" + Util.fmtSeconds(kn.cooldown()));
                    if (!dry) {
                        k.cooldown = kn.cooldown();
                        kitsDirty = true;
                    }
                }
                if (!k.displayManual && !k.display.equals(kn.display())) {
                    what.add("name and colour " + kn.display() + "<reset><gray>");
                    displayChanged = true;
                    if (!dry) {
                        k.display = kn.display();
                        kitsDirty = true;
                    }
                }
                if (key != null && kn.price() >= 0L && shopSection != null
                        && shopSection.getLong(key + ".price", -1L) != kn.price()) {
                    what.add("shop price <white>" + money(shopSection.getLong(key + ".price", 0L)) + "<gray> to <white>" + money(kn.price()));
                    if (!dry) {
                        shopSection.set(key + ".price", kn.price());
                        shopDirty = true;
                    }
                }
                if (!kn.id().equals(k.known)) {
                    if (!dry) {
                        k.known = kn.id();
                        kitsDirty = true;
                    }
                    if (what.isEmpty()) {
                        what.add("matched with <white>" + kn.name());
                    }
                }
            }
            // the shop copies the menu name, but only a real name (from the table or typed by hand), not the default one
            boolean share = kn != null || k.displayManual || !k.known.isEmpty();
            if (key != null && shopSection != null && share) {
                String shopName = shopSection.getString(key + ".name", "");
                String want = dry && displayChanged ? kn.display() : k.display;
                if (!shopName.equals(want) && (shopName.isEmpty() || displayChanged || force)) {
                    what.add("shop name " + want + "<reset><gray>");
                    if (!dry) {
                        shopSection.set(key + ".name", want);
                        shopDirty = true;
                    }
                }
            }
            if (!what.isEmpty()) {
                ++report.applied;
                report.lines.add("<white>" + k.id + " <dark_gray>- <gray>" + String.join("<dark_gray>, <gray>", what));
                report.plainLines.add(k.id + ": " + Util.plain("<gray>" + String.join(", ", what)));
            }
        }

        // 4. kits with a known price that the shop does not have yet
        if (addMissing && shopOn) {
            for (Kit k : kits) {
                KnownKits.Known kn = entry.get(k);
                if (link.containsKey(k) || kn == null || kn.price() < 0L || ShopLink.count() >= ShopLink.MAX_KITS) {
                    continue;
                }
                String key = KnownKits.shopKey(kn.name());
                if (!dry) {
                    ShopLink.add(key, kn.price(), Util.material(k.icon, Material.IRON_SWORD));
                    ShopLink.setName(key, k.display);
                    k.shop = key;
                    kitsDirty = true;
                    link.put(k, key);
                }
                ++report.added;
                String line = "<white>" + k.id + " <dark_gray>- <gray>added to the shop as <white>" + key + "<gray> for <white>" + money(kn.price());
                report.lines.add(line);
                report.plainLines.add(k.id + ": added to the shop as " + key + " for " + money(kn.price()));
            }
        }

        // 5. what is left over, so nothing is a surprise
        if (shopOn) {
            for (Kit k : kits) {
                if (!link.containsKey(k)) {
                    report.notInShop.add(k.id);
                }
            }
            Set<String> linkedKeys = new HashSet<>();
            for (String key : link.values()) {
                linkedKeys.add(key.toLowerCase(Locale.ROOT));
            }
            for (String key : ShopLink.names()) {
                if (!linkedKeys.contains(key.toLowerCase(Locale.ROOT))) {
                    report.shopOnly.add(key);
                }
            }
        }

        if (!dry) {
            if (shopDirty) {
                ShopLink.save();
            }
            if (kitsDirty) {
                store.save();
            }
        }
        return report;
    }

    private static String exactKey(List<String> keys, String wanted) {
        for (String key : keys) {
            if (key.equalsIgnoreCase(wanted)) {
                return key;
            }
        }
        return null;
    }

    static String money(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }
}
