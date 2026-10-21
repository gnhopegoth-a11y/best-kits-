# KitMenu

Paged kit menu for Paper 1.21 with kit arranging, icons, cooldowns, per-kit permissions and an
admin command for kits and the kit shop. It does not need any other kit plugin.

## Build

Needs Java 21 and Maven. The Paper API is pulled from the PaperMC repository in `pom.xml`.

    mvn package

The jar is written to `target/KitMenu.jar`. Put it in your server's `plugins/` folder.

## Commands

- `/kit [list|preview|claim] [kit]` opens the menu (`/kits` works too).
- `/unstablekits` (`/ukits`) is the admin command: list, save, delete, cooldown, icon, display, color,
  permission, resetcooldown, slot, arrange, shop, sync, bypass, reload.
- `/unstablekits save The_Director` creates a kit. An underscore is a space in the menu name (`The Director`),
  write two underscores to keep one. Typing `Wemmbu` when a kit called `wembu` exists updates that kit.
- `/unstablekits color (kit) (json)` sets the name by hand with Minecraft JSON text, for example
  `/unstablekits color wemmbu {"text":"WEMMBU","color":"#FF55FF","bold":true}` or a list of parts for two colours.
  `/unstablekits color (kit) reset` undoes it. A name set by hand is never changed by the sync.
- `/unstablekits sync [preview|force|add]` links the kits to the shop by name (see below).

## Shop link and sync (2.4)

Kit names are matched to the Shop plugin's kits and to the UnstablePVP kit table (`KnownKits.java`) by name.
Case, spaces, underscores, brackets and doubled letters do not matter, and a typo or two in a longer name is fine,
so a kit called `wembu` is linked to the shop kit `Wemmbu`. Two names that are equally close are never guessed.

For every kit that is matched once (and only once, so your own changes stay):

- the kit stores the shop name it belongs to (`shop:` in `kits.yml`),
- it gets the name, colour and cooldown from the screenshots (kits whose cooldown no screenshot shows keep theirs),
- the shop price is set to the screenshot price and the shop shows the same coloured name as the menu.

It runs when the server starts, on `/unstablekits reload` and when a kit is created (`auto-link: true` in `config.yml`).
`/unstablekits sync preview` only shows what it would do, `sync force` does it again for kits that are already done,
`sync add` puts kits that have a known price but are not in the shop into the shop.

## Permissions

Every kit has a permission setting, changed with `/unstablekits permission (kit) (node|none|default)`:

- `none`: everyone can claim the kit.
- `default`: the player needs one of the accepted prefixes + the kit id (or the name of its shop kit): the
  plugin's own `permission-prefix` (`kitmenu.kit.`), the prefix the Shop plugin grants when a kit is bought
  (read from the Shop's config, `playerkits.kit.` by default) and every prefix under `accepted-prefixes`.
  Case, spaces, underscores and doubled letters do not matter, so a bought `playerkits.kit.Wemmbu` unlocks the kit `wembu`.
- any other text: the player needs exactly that permission node.

A kit the player cannot claim shows as a barrier block named with the kit's own colored name.

## Config

`permission-prefix`, `accepted-prefixes`, `auto-link`, `default-cooldown`, `admins` and `admin-bypass` are in
`config.yml`. The config file is not overwritten on an existing server, but a key that is missing from it falls
back to the value shipped in the jar, so nothing has to be added by hand.

## Changes in 2.4

- Kits are linked to the shop by name and get the cooldown, colour and price from the screenshots (see above).
- Permissions: a bought shop kit now unlocks the kit. The Shop's own prefix is accepted automatically.
- Tooltips follow UnstablePVP: `Cooldown: 10m`, `You have not unlocked this kit`, `Right-click to preview`.
  Locked kits can be previewed with a right click.
- `/unstablekits save The_Director`: underscores are spaces. `/unstablekits color (kit) (json)`: names from JSON.
- New kits table: 28 kits from the screenshots, used for a new server and for the sync.

## Changes in 2.2

- Locked kits: the barrier block uses the kit's colored display name (before: a plain red name).
- Standalone: the `loadbefore: [PlayerKits2]` link is gone, the default permission prefix is
  `kitmenu.kit.`, and `/unstablekits permission` shows the configured prefix instead of a hard-coded one.
- `/kit` tab completion no longer throws an error when no argument is typed.

## Source layout

    src/main/java/com/example/kitmenu/
      KitMenuPlugin.java    plugin entry point, /kit command, permission and cooldown checks
      Menus.java            builds the list, preview and arrange menus
      MenuListener.java     click handling for the menus
      KitStore.java         kits.yml and claims.yml
      Kit.java              one kit
      ShopLink.java         reads and edits the Shop plugin's kit entries
      KitSync.java          links kits to the shop and the kit table by name
      KnownKits.java        the screenshot kits: name, colour, cooldown, price
      NameMatch.java        fuzzy name matching (typos, case, spaces, underscores)
      UnstableKitsCommand.java  /unstablekits
      Util.java             text, time and item helpers
    src/main/resources/     plugin.yml, config.yml
