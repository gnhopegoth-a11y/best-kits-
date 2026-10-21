package com.example.kitmenu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Text, time and item helpers shared by the whole plugin. */
final class Util {
    static final MiniMessage MM = MiniMessage.miniMessage();
    /** Namespace of the resource pack models (arrow_left, frame, page_1_2 ...). */
    static final String PACK = "unstablepvp";
    static final String PREFIX = "<gradient:#ff4fd8:#9863e7><b>ᴜɴsᴛᴀʙʟᴇᴘᴠᴘ</b></gradient> <dark_gray>┃ ";
    static final String DENY = "<#9863E7>ᴜɴѕᴛᴀʙʟᴇᴘᴠᴘ |<#AAAAAA> ʏᴏᴜ ᴄᴀɴ'ᴛ ᴅᴏ ᴛʜᴀᴛ";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_()-]{1,24}");

    private Util() {
    }

    static void say(CommandSender to, String mini) {
        to.sendMessage(MM.deserialize(mini));
    }

    static void info(CommandSender to, String mini) {
        say(to, PREFIX + mini);
    }

    /** MiniMessage to a component without the default italic. */
    static Component comp(String mini) {
        return MM.deserialize("<!italic>" + mini);
    }

    /** MiniMessage to plain text (colors removed). */
    static String plain(String mini) {
        return PlainTextComponentSerializer.plainText().serialize(MM.deserialize(mini));
    }

    static boolean validName(String s) {
        return NAME.matcher(s).matches();
    }

    /** Kit ids are the lower-case name. */
    static String id(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private static final String LITERAL_UNDERSCORE = "\0";

    /**
     * The name an admin typed, made neat for the menu: an underscore is a space.
     * "The_Director" becomes "The Director". Write two underscores to keep one ("a__b" becomes "a_b").
     */
    static String prettyName(String typed) {
        String s = typed.replace("__", LITERAL_UNDERSCORE).replace('_', ' ').replace(LITERAL_UNDERSCORE, "_");
        return s.trim().replaceAll(" {2,}", " ");
    }

    /**
     * The kit id for a typed name: lower case, underscores gone (two in a row stay as one), only letters,
     * digits, - and _ kept. "The_Director" becomes "thedirector", "Assassin_(Sword)" becomes "assassinsword".
     */
    static String kitId(String typed) {
        String s = typed.toLowerCase(Locale.ROOT).replace("__", LITERAL_UNDERSCORE).replace("_", "")
                .replace(LITERAL_UNDERSCORE, "_");
        return s.replaceAll("[^a-z0-9_-]", "");
    }

    /**
     * Turns what an admin typed after /unstablekits color into MiniMessage.
     * Text starting with {, [ or " is Minecraft JSON text (like /tellraw); anything else is read as MiniMessage.
     * Returns null when it cannot be read or has no visible text.
     */
    static String colorToMini(String input) {
        String text = input.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            String mini;
            char first = text.charAt(0);
            if (first == '{' || first == '[' || first == '"') {
                mini = MM.serialize(GsonComponentSerializer.gson().deserialize(text));
            } else {
                MM.deserialize(text);
                mini = text;
            }
            return plain(mini).isBlank() ? null : mini;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "30s", "15m", "12h", "1d", "1w" or plain seconds to seconds. -1 if invalid. */
    static long parseSeconds(String text) {
        String s = text.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return -1L;
        }
        long mult = 1L;
        char last = s.charAt(s.length() - 1);
        if (Character.isLetter(last)) {
            switch (last) {
                case 's': mult = 1L; break;
                case 'm': mult = 60L; break;
                case 'h': mult = 3600L; break;
                case 'd': mult = 86400L; break;
                case 'w': mult = 604800L; break;
                default: return -1L;
            }
            s = s.substring(0, s.length() - 1);
        }
        try {
            long v = Long.parseLong(s);
            return v < 0L ? -1L : v * mult;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /** "3000", "3k" or "2m" to a whole amount. -1 if invalid. */
    static long parseAmount(String text) {
        String s = text.trim().toLowerCase(Locale.ROOT).replace(",", "");
        if (s.isEmpty()) {
            return -1L;
        }
        long mult = 1L;
        char last = s.charAt(s.length() - 1);
        if (last == 'k') {
            mult = 1000L;
            s = s.substring(0, s.length() - 1);
        } else if (last == 'm') {
            mult = 1000000L;
            s = s.substring(0, s.length() - 1);
        }
        try {
            long v = Long.parseLong(s);
            return v < 0L ? -1L : v * mult;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    static String fmtSeconds(long seconds) {
        if (seconds <= 0L) {
            return "0s";
        }
        long d = seconds / 86400L;
        long h = seconds % 86400L / 3600L;
        long m = seconds % 3600L / 60L;
        long s = seconds % 60L;
        StringBuilder sb = new StringBuilder();
        if (d > 0L) {
            sb.append(d).append("d ");
        }
        if (h > 0L) {
            sb.append(h).append("h ");
        }
        if (m > 0L) {
            sb.append(m).append("m ");
        }
        if (s > 0L && d == 0L) {
            sb.append(s).append("s");
        }
        return sb.toString().trim();
    }

    static String fmtCooldown(long seconds) {
        return seconds <= 0L ? "none" : fmtSeconds(seconds);
    }

    static Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material m = Material.matchMaterial(name.trim());
        if (m == null || m.isAir() || !m.isItem()) {
            return fallback;
        }
        return m;
    }

    static ItemStack item(Material type, String name, String... lore) {
        ItemStack it = new ItemStack(type);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(comp(name));
        if (lore.length > 0) {
            meta.lore(Arrays.stream(lore).map(Util::comp).toList());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    /** Copies a saved item (the kit icon) and gives it a new name and lore. */
    static ItemStack itemFrom(ItemStack base, String name, String... lore) {
        ItemStack it = base.clone();
        it.setAmount(1);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(comp(name));
        if (lore.length > 0) {
            meta.lore(Arrays.stream(lore).map(Util::comp).toList());
        } else {
            meta.lore(new ArrayList<Component>());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    /** Sets the resource pack model, e.g. "arrow_left", as unstablepvp:arrow_left. */
    static ItemStack model(ItemStack it, String model) {
        ItemMeta meta = it.getItemMeta();
        NamespacedKey key = NamespacedKey.fromString(PACK + ":" + model);
        if (key != null) {
            meta.setItemModel(key);
        }
        it.setItemMeta(meta);
        return it;
    }

    static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    static ItemStack decode(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(text));
        } catch (Exception e) {
            return null;
        }
    }
}
