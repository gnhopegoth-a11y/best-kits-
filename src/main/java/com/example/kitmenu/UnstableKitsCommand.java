package com.example.kitmenu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** /unstablekits: admin commands for the kits, the menu layout and the shop entries. */
final class UnstableKitsCommand implements TabExecutor {
    private final KitMenuPlugin plugin;

    UnstableKitsCommand(KitMenuPlugin plugin) {
        this.plugin = plugin;
    }

    private KitStore store() {
        return plugin.store();
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!plugin.isAdmin(s)) {
            Util.say(s, Util.DENY);
            return true;
        }
        if (a.length == 0) {
            usage(s);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(s);
            case "save", "create" -> save(s, a);
            case "delete", "remove" -> delete(s, a);
            case "cooldown" -> cooldown(s, a);
            case "icon" -> icon(s, a);
            case "display", "name" -> display(s, a);
            case "permission" -> permission(s, a);
            case "resetcooldown" -> resetCooldown(s, a);
            case "slot", "position" -> slot(s, a);
            case "arrange" -> arrange(s);
            case "shop" -> shop(s, a);
            case "bypass" -> bypass(s, a);
            case "sync", "link" -> sync(s, a);
            case "color", "colour" -> color(s, a);
            case "reload" -> {
                plugin.reloadAll();
                Util.info(s, "<green>Reloaded the kits and the config.");
            }
            default -> usage(s);
        }
        return true;
    }

    private void usage(CommandSender s) {
        Util.say(s, "<gradient:#ff4fd8:#9863e7><b>ᴜɴsᴛᴀʙʟᴇᴘᴠᴘ ᴋɪᴛs</b></gradient>");
        help(s, "/unstablekits list", "every kit, its cooldown, icon and shop status");
        help(s, "/unstablekits save (kit)", "save YOUR inventory into a kit (creates it if new). Use _ for a space: The_Director");
        help(s, "/unstablekits delete (kit) [menu|shop]", "delete from the menu and the shop");
        help(s, "/unstablekits cooldown (kit|all) (30s|15m|12h|1d|0)", "set the cooldown (all = every kit)");
        help(s, "/unstablekits icon (kit) (hand|ITEM)", "change the icon: hand = the item you hold, or type its name");
        help(s, "/unstablekits slot (kit) (number)", "move a kit to a spot (1-28 is page 1, 29-56 is page 2)");
        help(s, "/unstablekits slot (kit) (page) (spot 1-28)", "same, with the page and the spot on it");
        help(s, "/unstablekits arrange", "open the menu and click kits into place");
        help(s, "/unstablekits display (kit) (name)", "change the name shown in the menu (_ is a space)");
        help(s, "/unstablekits color (kit) (json)", "set the name by hand with JSON text, or \"reset\" to undo");
        help(s, "/unstablekits sync [preview|force|add]", "link kits to the shop by name, apply cooldowns, colours and prices");
        help(s, "/unstablekits permission (kit) (node|none|default)", "who can claim it");
        help(s, "/unstablekits resetcooldown (kit) (player)", "let a player claim again");
        help(s, "/unstablekits shop", "price, icon, stock and removal in the Kit Shop");
        help(s, "/unstablekits bypass [on|off]", "admin bypass: off = see the menu and claim like a normal player");
        help(s, "/unstablekits reload", "reload kits and config");
    }

    /** Lets an admin test the menu like a normal player, without editing config.yml. */
    private void bypass(CommandSender s, String[] a) {
        if (!(s instanceof Player)) {
            Util.info(s, "<red>Only a player has a bypass to switch.");
            return;
        }
        Player p = (Player) s;
        if (!plugin.getConfig().getBoolean("admin-bypass", true)) {
            Util.info(p, "<yellow>admin-bypass is false in config.yml, so nobody gets the bypass. "
                    + "<gray>You already see the kits like a normal player.");
            return;
        }
        boolean want;
        if (a.length >= 2) {
            String v = a[1].toLowerCase(Locale.ROOT);
            if (v.equals("on")) {
                want = true;
            } else if (v.equals("off")) {
                want = false;
            } else {
                Util.info(p, "<red>Use <white>/unstablekits bypass on<red> or <white>/unstablekits bypass off<red>.");
                return;
            }
        } else {
            want = !plugin.bypass(p);
        }
        if (plugin.setBypass(p, want)) {
            Util.info(p, "<green>Admin bypass is ON. <gray>You can claim every kit and ignore cooldowns.");
        } else {
            Util.info(p, "<yellow>Admin bypass is OFF. <gray>The menu and /kit now treat you like a normal player: "
                    + "kits you have no permission for show as barriers.");
            Util.info(p, "<gray>If a kit still shows as unlocked, you are probably op or have <white>*<gray> in LuckPerms. "
                    + "It switches back on when the server restarts.");
        }
    }

    private void shopUsage(CommandSender s) {
        help(s, "/unstablekits shop list", "kits in the shop");
        help(s, "/unstablekits shop add (kit) (price)", "add a kit or change its price (3000, 3k)");
        help(s, "/unstablekits shop price (kit) (price)", "change the price");
        help(s, "/unstablekits shop icon (kit) (hand|ITEM)", "change the shop icon");
        help(s, "/unstablekits shop stock (kit) (amount|-1)", "limit how many can be bought (-1 = unlimited)");
        help(s, "/unstablekits shop remove (kit)", "take a kit out of the shop");
    }

    private void help(CommandSender s, String command, String text) {
        Util.say(s, "<#9863E7>" + command + " <dark_gray>- <gray>" + text);
    }

    /** Returns the kit name argument, or null (and prints the usage) if it is missing or invalid. */
    private String name(CommandSender s, String[] a, int i, String usage) {
        if (a.length <= i || !Util.validName(a[i])) {
            Util.info(s, "<red>Use: <white>" + usage);
            return null;
        }
        return a[i];
    }

    /** The shop's name for a kit: the linked shop kit if the menu kit has one, else what was typed. */
    private String shopKey(String typed) {
        Kit k = store().get(typed);
        return k != null && !k.shop.isEmpty() ? k.shop : typed;
    }

    private Kit kit(CommandSender s, String name) {
        Kit k = store().get(name);
        if (k == null) {
            Util.info(s, "<red>There is no kit called <white>" + name + "<red>. Use <white>/unstablekits list<red>.");
        }
        return k;
    }

    private static boolean wantsHand(String[] a, int index) {
        return a.length <= index || a[index].equalsIgnoreCase("hand");
    }

    private ItemStack held(CommandSender s) {
        if (s instanceof Player p && !p.getInventory().getItemInMainHand().getType().isAir()) {
            return p.getInventory().getItemInMainHand();
        }
        Util.info(s, "<red>Hold the item in your hand first. Or type an item name, like <white>DIAMOND_SWORD<red>.");
        return null;
    }

    private Material named(CommandSender s, String text) {
        Material m = Material.matchMaterial(text);
        if (m == null || m.isAir() || !m.isItem()) {
            Util.info(s, "<red>That is not an item name. Example: <white>DIAMOND_SWORD<red>, or use <white>hand<red>.");
            return null;
        }
        return m;
    }

    private void list(CommandSender s) {
        List<Kit> kits = store().all();
        Util.info(s, "<gray>" + kits.size() + " kit(s):");
        for (Kit k : kits) {
            String shop = !k.shop.isEmpty() && ShopLink.has(k.shop) ? "<#ffd34e>shop: " + k.shop
                    : ShopLink.has(k.id) ? "<#ffd34e>in shop" : "<dark_gray>not in shop";
            Util.say(s, " <white>" + k.id + " <dark_gray>| <gray>spot " + store().positionOf(k)
                    + " <dark_gray>| <gray>" + k.itemCount() + " items <dark_gray>| <gray>cooldown "
                    + Util.fmtCooldown(k.cooldown) + " <dark_gray>| <gray>" + k.icon + " <dark_gray>| " + shop);
        }
    }

    private void save(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            Util.info(s, "<red>Run this in game. It saves the inventory you are carrying.");
            return;
        }
        String typed = name(s, a, 1, "/unstablekits save (kit)  <gray>(letters, numbers, - and _ for a space: The_Director)");
        if (typed == null) {
            return;
        }
        String id = Util.kitId(typed);
        String pretty = Util.prettyName(typed);
        if (NameMatch.key(id).isEmpty() || pretty.isEmpty()) {
            Util.info(s, "<red>Pick a name with letters or numbers in it.");
            return;
        }
        if (id.equals("all")) {
            Util.info(s, "<red>\"all\" can't be a kit name, it means every kit in the cooldown command.");
            return;
        }
        Kit k = store().get(typed);
        boolean created = k == null;
        if (created) {
            k = new Kit(id);
            k.display = "<#9863E7><b>" + pretty;
            k.cooldown = store().defaultCooldown();
            store().add(k);
        }
        k.capture(p);
        if (created) {
            Material first = k.firstMaterial();
            k.icon = first != null ? first.name() : "IRON_SWORD";
        }
        store().save();
        Util.info(s, "<green>" + (created ? "Created" : "Updated") + " kit <white>" + Util.plain(k.display) + "<green> with <white>"
                + k.itemCount() + "<green> item(s) from your inventory.");
        if (k.itemCount() == 0) {
            Util.info(s, "<yellow>Your inventory is empty, so the kit has no items yet.");
        }
        if (created && plugin.getConfig().getBoolean("auto-link", true)) {
            // look for the same name in the shop and in the screenshot kits
            KitSync.Report report = KitSync.run(plugin, false, false, false);
            String mine = "<white>" + k.id + " ";
            boolean any = false;
            for (String line : report.lines) {
                if (line.startsWith(mine)) {
                    Util.say(s, " " + line);
                    any = true;
                }
            }
            if (!any && ShopLink.available()) {
                Util.info(s, "<gray>No kit with a similar name in the shop. Add it with <white>/unstablekits shop add "
                        + k.id + " (price)<gray>.");
            }
        }
    }

    private void delete(CommandSender s, String[] a) {
        String name = name(s, a, 1, "/unstablekits delete (kit) [menu|shop]");
        if (name == null) {
            return;
        }
        String where = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "both";
        if (!(where.equals("menu") || where.equals("shop") || where.equals("both"))) {
            Util.info(s, "<red>Use <white>menu<red>, <white>shop<red> or leave it out to delete from both.");
            return;
        }
        String shopName = shopKey(name);
        boolean fromMenu = !where.equals("shop") && store().remove(name);
        boolean fromShop = !where.equals("menu") && ShopLink.remove(shopName);
        if (!fromMenu && !fromShop) {
            Util.info(s, "<red>Nothing to delete: <white>" + name + "<red> is not in " + where + ".");
            return;
        }
        List<String> places = new ArrayList<>();
        if (fromMenu) {
            places.add("the kit menu");
        }
        if (fromShop) {
            places.add("the shop");
        }
        Util.info(s, "<green>Deleted <white>" + name + "<green> from " + String.join(" and ", places) + ".");
        if (fromMenu) {
            Util.info(s, "<gray>A backup of the old kits is in <white>plugins/KitMenu/kits.yml.bak");
        }
    }

    private void cooldown(CommandSender s, String[] a) {
        String name = name(s, a, 1, "/unstablekits cooldown (kit|all) (30s|15m|12h|1d|0)");
        if (name == null) {
            return;
        }
        long seconds = a.length > 2 ? Util.parseSeconds(a[2]) : -1L;
        if (seconds < 0L) {
            Util.info(s, "<red>Time must look like <white>30s<red>, <white>15m<red>, <white>12h<red>, <white>1d<red> or <white>0<red> for no cooldown.");
            return;
        }
        if (name.equalsIgnoreCase("all")) {
            store().setAllCooldowns(seconds);
            Util.info(s, "<green>All <white>" + store().size() + "<green> kits now have a cooldown of <white>"
                    + Util.fmtCooldown(seconds) + "<green>.");
            return;
        }
        Kit k = kit(s, name);
        if (k == null) {
            return;
        }
        k.cooldown = seconds;
        store().save();
        Util.info(s, "<green>Cooldown of <white>" + name + "<green> is now <white>" + Util.fmtCooldown(seconds) + "<green>.");
    }

    private void icon(CommandSender s, String[] a) {
        String name = name(s, a, 1, "/unstablekits icon (kit) (hand|ITEM)");
        if (name == null) {
            return;
        }
        Kit k = kit(s, name);
        if (k == null) {
            return;
        }
        Material m;
        if (wantsHand(a, 2)) {
            ItemStack hand = held(s);
            if (hand == null) {
                return;
            }
            ItemStack one = hand.clone();
            one.setAmount(1);
            m = hand.getType();
            k.icon = m.name();
            k.iconItem = Util.encode(one);
        } else {
            m = named(s, a[2]);
            if (m == null) {
                return;
            }
            k.icon = m.name();
            k.iconItem = "";
        }
        store().save();
        boolean shop = ShopLink.setIcon(k.shop.isEmpty() ? k.id : k.shop, m);
        Util.info(s, "<green>Icon of <white>" + name + "<green> is now <white>" + m.name() + "<green>"
                + (shop ? " (menu and shop)." : "."));
    }

    private void display(CommandSender s, String[] a) {
        String name = name(s, a, 1, "/unstablekits display (kit) (name)");
        if (name == null) {
            return;
        }
        Kit k = kit(s, name);
        if (k == null) {
            return;
        }
        if (a.length < 3) {
            Util.info(s, "<red>Use: <white>/unstablekits display (kit) (name)");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
        if (!text.contains("<")) {
            text = "<#9863E7><b>" + Util.prettyName(text);
        }
        k.display = text;
        k.displayManual = true;
        store().save();
        boolean shop = !k.shop.isEmpty() && ShopLink.setName(k.shop, text);
        Util.info(s, "<green>Kit <white>" + name + "<green> is now shown as " + text + "<reset><green>"
                + (shop ? " (menu and shop)." : "."));
    }

    private void color(CommandSender s, String[] a) {
        String usage = "/unstablekits color (kit) (json)";
        String name = name(s, a, 1, usage);
        if (name == null) {
            return;
        }
        Kit k = kit(s, name);
        if (k == null) {
            return;
        }
        if (a.length < 3) {
            Util.info(s, "<red>Use: <white>" + usage);
            Util.say(s, "<gray>Example: <white>/unstablekits color " + k.id + " {\"text\":\"" + Util.plain(k.display)
                    + "\",\"color\":\"#FF55FF\",\"bold\":true}");
            Util.say(s, "<gray>Two colours: <white>[{\"text\":\"EGG\",\"color\":\"red\",\"bold\":true},"
                    + "{\"text\":\"CHAN\",\"color\":\"gold\",\"bold\":true}]");
            Util.say(s, "<gray>Undo: <white>/unstablekits color " + k.id + " reset");
            return;
        }
        if (a[2].equalsIgnoreCase("reset") && a.length == 3) {
            String back = "<#9863E7><b>" + Util.prettyName(k.id);
            for (KnownKits.Known known : KnownKits.ALL) {
                if (known.id().equals(k.known)) {
                    back = known.display();
                }
            }
            k.display = back;
            k.displayManual = false;
            store().save();
            boolean shop = !k.shop.isEmpty() && ShopLink.setName(k.shop, back);
            Util.info(s, "<green>Kit <white>" + name + "<green> is back to " + back + "<reset><green>"
                    + (shop ? " (menu and shop)." : "."));
            return;
        }
        String mini = Util.colorToMini(String.join(" ", Arrays.copyOfRange(a, 2, a.length)));
        if (mini == null) {
            Util.info(s, "<red>I could not read that as JSON text, or it has no visible text.");
            Util.say(s, "<gray>It looks like <white>{\"text\":\"WEMMBU\",\"color\":\"#FF55FF\",\"bold\":true}<gray>. "
                    + "Check the quotes and brackets.");
            return;
        }
        k.display = mini;
        k.displayManual = true;
        store().save();
        boolean shop = !k.shop.isEmpty() && ShopLink.setName(k.shop, mini);
        Util.info(s, "<green>Kit <white>" + name + "<green> now looks like " + mini + "<reset><green>"
                + (shop ? " (menu and shop)." : "."));
    }

    private void sync(CommandSender s, String[] a) {
        boolean dry = false;
        boolean force = false;
        boolean add = false;
        for (int i = 1; i < a.length; ++i) {
            switch (a[i].toLowerCase(Locale.ROOT)) {
                case "preview", "dry" -> dry = true;
                case "force" -> force = true;
                case "add" -> add = true;
                default -> {
                    Util.info(s, "<red>Use: <white>/unstablekits sync [preview|force|add]");
                    return;
                }
            }
        }
        if (!ShopLink.available()) {
            Util.info(s, "<yellow>The Shop plugin is not running, so only cooldowns and colours are applied.");
        }
        KitSync.Report r = KitSync.run(plugin, dry, force, add);
        Util.info(s, dry ? "<gray>Preview, nothing was changed:" : "<green>Sync done:");
        if (r.lines.isEmpty()) {
            Util.say(s, " <gray>Nothing to do: every kit is already linked and up to date.");
        }
        for (String line : r.lines) {
            Util.say(s, " " + line);
        }
        if (!r.notInShop.isEmpty()) {
            Util.say(s, "<gray>Not in the shop: <white>" + String.join("<gray>, <white>", r.notInShop));
        }
        if (!r.shopOnly.isEmpty()) {
            Util.say(s, "<gray>In the shop but not in the menu: <white>" + String.join("<gray>, <white>", r.shopOnly));
        }
        if (dry) {
            Util.say(s, "<gray>Run <white>/unstablekits sync<gray> to do it.");
        } else if (!add && !r.notInShop.isEmpty()) {
            Util.say(s, "<gray>Kits with a known price can be put in the shop with <white>/unstablekits sync add<gray>.");
        }
    }

    private void permission(CommandSender s, String[] a) {
        String name = name(s, a, 1, "/unstablekits permission (kit) (node|none|default)");
        if (name == null) {
            return;
        }
        Kit k = kit(s, name);
        if (k == null) {
            return;
        }
        if (a.length < 3) {
            Util.info(s, "<red>Use: <white>/unstablekits permission (kit) (node|none|default)");
            return;
        }
        String value = a[2];
        if (value.equalsIgnoreCase("default")) {
            k.permission = "";
            store().save();
            List<String> nodes = new ArrayList<>();
            for (String prefix : plugin.acceptedPrefixes()) {
                nodes.add(prefix + (k.shop.isEmpty() ? k.id : k.shop));
            }
            Util.info(s, "<green>" + name + " is unlocked by any of: <white>" + String.join("<gray>, <white>", nodes)
                    + "<green> (the Shop gives one of them).");
        } else if (value.equalsIgnoreCase("none")) {
            k.permission = "none";
            store().save();
            Util.info(s, "<green>" + name + " is now free for everybody.");
        } else {
            k.permission = value;
            store().save();
            Util.info(s, "<green>" + name + " now needs <white>" + value + "<green>.");
        }
    }

    private void resetCooldown(CommandSender s, String[] a) {
        String name = name(s, a, 1, "/unstablekits resetcooldown (kit) (player)");
        if (name == null) {
            return;
        }
        Kit k = kit(s, name);
        if (k == null) {
            return;
        }
        Player target = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : null;
        if (target == null) {
            Util.info(s, "<red>That player is not online.");
            return;
        }
        store().clearClaim(target.getUniqueId(), k.id);
        Util.info(s, "<green>" + target.getName() + " can claim <white>" + name + "<green> again.");
    }

    private void slot(CommandSender s, String[] a) {
        String usage = "/unstablekits slot (kit) (number)   or   /unstablekits slot (kit) (page) (spot 1-28)";
        String name = name(s, a, 1, usage);
        if (name == null) {
            return;
        }
        Kit k = kit(s, name);
        if (k == null) {
            return;
        }
        int position = -1;
        try {
            if (a.length == 3) {
                position = Integer.parseInt(a[2]);
            } else if (a.length >= 4) {
                int page = Integer.parseInt(a[2]);
                int spot = Integer.parseInt(a[3]);
                if (page >= 1 && spot >= 1 && spot <= Menus.PER_PAGE) {
                    position = (page - 1) * Menus.PER_PAGE + spot;
                }
            }
        } catch (NumberFormatException e) {
            position = -1;
        }
        if (position < 1 || position > KitStore.MAX_POSITIONS) {
            Util.info(s, "<red>Use: <white>" + usage);
            Util.info(s, "<gray>Spots go from 1 to 280. Page 1 is 1-28, page 2 is 29-56, and so on.");
            return;
        }
        Kit other = store().atPosition(position - 1);
        store().place(k, position);
        int inPage = (position - 1) % Menus.PER_PAGE;
        String where = "page " + ((position - 1) / Menus.PER_PAGE + 1) + ", row " + (inPage / 7 + 1)
                + ", column " + (inPage % 7 + 1);
        Util.info(s, "<green>" + name + " is now at spot <white>" + position + "<green> (" + where + ").");
        if (other != null && other != k) {
            Util.info(s, "<gray>" + Util.plain(other.display) + " took its old place.");
        }
    }

    private void arrange(CommandSender s) {
        if (!(s instanceof Player p)) {
            Util.info(s, "<red>Run this in game. It opens a menu.");
            return;
        }
        plugin.menus().openArrange(p, 0);
    }

    private void shop(CommandSender s, String[] a) {
        if (!ShopLink.available()) {
            Util.info(s, "<red>The Shop plugin is not running, so there is no shop to edit.");
            return;
        }
        if (a.length < 2) {
            shopUsage(s);
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "list" -> shopList(s);
            case "add" -> shopAdd(s, a);
            case "price" -> shopPrice(s, a);
            case "icon" -> shopIcon(s, a);
            case "stock" -> shopStock(s, a);
            case "remove", "delete" -> shopRemove(s, a);
            default -> shopUsage(s);
        }
    }

    private void shopList(CommandSender s) {
        List<String> lines = ShopLink.lines();
        Util.info(s, "<gray>" + lines.size() + " kit(s) in the shop (cheapest first):");
        for (String line : lines) {
            Util.say(s, " " + line);
        }
    }

    private void shopAdd(CommandSender s, String[] a) {
        String name = name(s, a, 2, "/unstablekits shop add (kit) (price)");
        if (name == null) {
            return;
        }
        long price = a.length > 3 ? Util.parseAmount(a[3]) : -1L;
        if (price < 0L) {
            Util.info(s, "<red>The price must be a whole number, like <white>3000<red> or <white>3k<red>.");
            return;
        }
        if (!ShopLink.has(name) && ShopLink.count() >= ShopLink.MAX_KITS) {
            Util.info(s, "<red>The shop is full (28 kits). Remove one first.");
            return;
        }
        Kit menuKit = store().get(name);
        Material icon = menuKit != null ? Util.material(menuKit.icon, Material.IRON_SWORD) : Material.IRON_SWORD;
        boolean created = ShopLink.add(name, price, icon);
        Util.info(s, "<green>" + (created ? "Added" : "Updated") + " <white>" + name + "<green> in the shop for <#ffd34e>"
                + price + "<green> coins.");
        if (menuKit == null) {
            Util.info(s, "<yellow>There is no kit called " + name + " in the menu yet. Use <white>/unstablekits save "
                    + name + "<yellow> to fill it.");
        }
    }

    private void shopPrice(CommandSender s, String[] a) {
        String name = name(s, a, 2, "/unstablekits shop price (kit) (price)");
        if (name == null) {
            return;
        }
        long price = a.length > 3 ? Util.parseAmount(a[3]) : -1L;
        if (price < 0L) {
            Util.info(s, "<red>The price must be a whole number, like <white>3000<red> or <white>3k<red>.");
            return;
        }
        if (!ShopLink.setPrice(shopKey(name), price)) {
            Util.info(s, "<red>" + name + " is not in the shop. Add it with <white>/unstablekits shop add " + name + " " + price);
            return;
        }
        Util.info(s, "<green>" + name + " now costs <#ffd34e>" + price + "<green> coins.");
    }

    private void shopIcon(CommandSender s, String[] a) {
        String name = name(s, a, 2, "/unstablekits shop icon (kit) (hand|ITEM)");
        if (name == null) {
            return;
        }
        name = shopKey(name);
        if (!ShopLink.has(name)) {
            Util.info(s, "<red>" + name + " is not in the shop.");
            return;
        }
        Material m;
        if (wantsHand(a, 3)) {
            ItemStack hand = held(s);
            if (hand == null) {
                return;
            }
            m = hand.getType();
        } else {
            m = named(s, a[3]);
            if (m == null) {
                return;
            }
        }
        ShopLink.setIcon(name, m);
        Util.info(s, "<green>Shop icon of <white>" + name + "<green> is now <white>" + m.name() + "<green>.");
    }

    private void shopStock(CommandSender s, String[] a) {
        String name = name(s, a, 2, "/unstablekits shop stock (kit) (amount|-1)");
        if (name == null) {
            return;
        }
        int stock;
        try {
            stock = a.length > 3 ? Integer.parseInt(a[3]) : -2;
        } catch (NumberFormatException e) {
            stock = -2;
        }
        if (stock < -1) {
            Util.info(s, "<red>Stock must be a number, or <white>-1<red> for unlimited.");
            return;
        }
        if (!ShopLink.setStock(shopKey(name), stock)) {
            Util.info(s, "<red>" + name + " is not in the shop.");
            return;
        }
        Util.info(s, "<green>Stock of <white>" + name + "<green> is now <white>" + (stock < 0 ? "unlimited" : String.valueOf(stock)) + "<green>.");
    }

    private void shopRemove(CommandSender s, String[] a) {
        String name = name(s, a, 2, "/unstablekits shop remove (kit)");
        if (name == null) {
            return;
        }
        if (!ShopLink.remove(shopKey(name))) {
            Util.info(s, "<red>" + name + " is not in the shop.");
            return;
        }
        Kit linked = store().get(name);
        if (linked != null && !linked.shop.isEmpty()) {
            linked.shop = "";
            store().save();
        }
        Util.info(s, "<green>Removed <white>" + name + "<green> from the shop. It is still in the kit menu.");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (!plugin.isAdmin(s) || a.length == 0) {
            return out;
        }
        if (a.length == 1) {
            out.addAll(List.of("list", "save", "delete", "cooldown", "icon", "display", "color", "permission",
                    "resetcooldown", "slot", "arrange", "shop", "sync", "bypass", "reload"));
        } else if (a[0].equalsIgnoreCase("bypass")) {
            if (a.length == 2) {
                out.addAll(List.of("on", "off"));
            }
        } else if (a[0].equalsIgnoreCase("sync") || a[0].equalsIgnoreCase("link")) {
            out.addAll(List.of("preview", "force", "add"));
        } else if ((a[0].equalsIgnoreCase("color") || a[0].equalsIgnoreCase("colour")) && a.length == 3) {
            out.add("reset");
        } else if (a[0].equalsIgnoreCase("shop")) {
            if (a.length == 2) {
                out.addAll(List.of("list", "add", "price", "icon", "stock", "remove"));
            } else if (a.length == 4 && a[1].equalsIgnoreCase("icon")) {
                out.add("hand");
            } else if (a.length == 3) {
                if (a[1].equalsIgnoreCase("add")) {
                    for (Kit k : store().all()) {
                        out.add(k.id);
                    }
                } else {
                    out.addAll(ShopLink.names());
                }
            }
        } else if (a.length == 2) {
            for (Kit k : store().all()) {
                out.add(k.id);
            }
            if (a[0].equalsIgnoreCase("cooldown")) {
                out.add("all");
            }
        } else if (a.length == 3) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "icon" -> out.add("hand");
                case "cooldown" -> out.addAll(List.of("0", "10m", "30m", "1h", "12h", "1d"));
                case "permission" -> out.addAll(List.of("default", "none"));
                case "delete", "remove" -> out.addAll(List.of("menu", "shop", "both"));
                case "resetcooldown" -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        out.add(p.getName());
                    }
                }
                default -> {
                }
            }
        }
        String typed = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(typed));
        return out;
    }
}
