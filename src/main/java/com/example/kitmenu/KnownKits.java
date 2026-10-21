package com.example.kitmenu;

import java.util.ArrayList;
import java.util.List;

/**
 * The kits from the UnstablePVP screenshots: name as shown, colour, cooldown and shop price.
 * A kit in your menu whose name matches one of these (even with a typo) gets its colour, cooldown and price.
 */
final class KnownKits {
    /**
     * @param id       the kit id used when the kit is created from this table
     * @param name     the name as the screenshot shows it
     * @param color    MiniMessage colour tag (or the rainbow tag)
     * @param cooldown seconds, or -1 when no screenshot shows it (the kit keeps its own cooldown)
     * @param price    shop price, or -1 when the kit is not in the Kit Shop screenshots
     * @param free     true when everybody can claim it (permission "none" for a new kit)
     * @param aliases  other spellings that mean the same kit
     */
    record Known(String id, String name, String color, long cooldown, long price, boolean free, List<String> aliases) {
        /** The MiniMessage name shown in the menus: colour, bold, name. */
        String display() {
            return color + "<b>" + name;
        }

        /** Every spelling used to recognise the kit. */
        List<String> names() {
            List<String> all = new ArrayList<>();
            all.add(name);
            all.addAll(aliases);
            return all;
        }
    }

    private static final String RED = "<#FF5555>";
    private static final String DARK_RED = "<#AA0000>";
    private static final String GOLD = "<#FFAA00>";
    private static final String SAND = "<#FFD35A>";
    private static final String YELLOW = "<#FFFF55>";
    private static final String GREEN = "<#55FF55>";
    private static final String AQUA = "<#55FFFF>";
    private static final String PINK = "<#FF55FF>";
    private static final String PURPLE = "<#9B59B6>";

    /** In menu order. Prices are the Kit Shop screenshots, cooldowns the kit menu tooltips. */
    static final List<Known> ALL = List.of(
            kit("LAW", GOLD, 0L, -1L, true),
            new Known("parrot", "PARROT KIT", SAND, -1L, 500L, false, List.of("Parrot")),
            kit("SPOKE", RED, 600L, 800L, false),
            kit("JUMPER", PINK, -1L, 3000L, false),
            kit("FLAMEFRAGS", RED, 600L, 3250L, false),
            kit("MANEPEAR", GOLD, -1L, 4000L, false),
            kit("JADENMAN", AQUA, -1L, 5000L, false),
            kit("MISTRUL", AQUA, -1L, 6000L, false),
            kit("FLAMEFRAGS SWORD", RED, -1L, 6000L, false),
            kit("LETTUCEK", GREEN, 900L, 6500L, false),
            kit("JAMATOP", SAND, 1200L, 8500L, false),
            kit("MINUTETECH", DARK_RED, 600L, 9200L, false),
            kit("SARGELAW", RED, 900L, 9250L, false),
            kit("WEMMBU", PINK, -1L, 9500L, false),
            kit("FLAMEFRAGS CART", RED, 900L, 13000L, false),
            kit("THEOBALD", YELLOW, -1L, 13000L, false),
            kit("Swight", SAND, 600L, -1L, false),
            kit("Flowtives", SAND, 900L, -1L, false),
            kit("Princezam", SAND, 900L, -1L, false),
            kit("Saparata", SAND, 600L, -1L, false),
            kit("THE DIRECTOR", PURPLE, 600L, -1L, false, "Director"),
            kit("MAPICC", SAND, 600L, -1L, false),
            kit("SHARPNESS", DARK_RED, 600L, -1L, false),
            kit("ASSASSIN", YELLOW, 900L, -1L, false),
            kit("ASSASSIN(SWORD)", AQUA, 900L, -1L, false),
            kit("ASSASSIN(MACE)", PINK, 900L, -1L, false),
            kit("EGGCHAN", "<rainbow>", 600L, -1L, false),
            kit("CLOWNPIERCE", RED, 900L, -1L, false));

    private KnownKits() {
    }

    private static Known kit(String name, String color, long cooldown, long price, boolean free, String... aliases) {
        // the id is the name in lower case, letters and digits only ("ASSASSIN(SWORD)" is "assassinsword")
        return new Known(NameMatch.key(name), name, color, cooldown, price, free, List.of(aliases));
    }

    /** For each known kit, its spellings (the shape {@link NameMatch#pair} wants). */
    static List<List<String>> spellings(List<Known> list) {
        List<List<String>> out = new ArrayList<>();
        for (Known k : list) {
            out.add(k.names());
        }
        return out;
    }

    /** A shop key for a kit that is not in the shop yet: "FLAMEFRAGS CART" becomes "FlamefragsCart". */
    static String shopKey(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.replaceAll("[^A-Za-z0-9]+", " ").trim().split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return sb.toString();
    }
}
