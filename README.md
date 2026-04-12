# FoliaCustomCommands

A Folia-native Minecraft plugin that lets you create custom server commands through simple YAML files — no coding required.

> **Compatible with**: Paper / Folia 1.21.1+  
> **Java**: 21+

---

## Features

- **File-based commands** — each `.yml` file in the `commands/` folder defines one or more commands
- **Hot reload** — changes to command files are detected and applied automatically (no restart needed)
- **Cooldowns** — per-player cooldowns with customizable messages
- **Rich actions** — titles, action bars, sounds, broadcasts, console commands, and more
- **Conditional logic** — `[IF]` with permission checks, world checks, and health checks
- **Chance** — `[CHANCE:N]` to run an action with N% probability
- **Delays** — `[DELAY:ticks]` to schedule future actions
- **Placeholders** — `{player}`, `{world}`, `{x}`, `{y}`, `{z}`, `{args}`, `{arg0}`, `{arg1}`...
- **Aliases** — multiple names for the same command
- **World conditions** — allow or block commands in specific worlds
- **Health conditions** — require minimum/maximum health to run
- **Folia-safe** — all scheduling uses Folia's region and global schedulers

---

## Installation

1. Download or compile the `.jar` (see [Building](#building))
2. Drop it into your server's `plugins/` folder
3. Start the server — a `plugins/FoliaCustomCommands/commands/` folder will be created with example files
4. Edit or add `.yml` files in that folder to create your commands
5. Run `/fccmds reload` or let auto-reload handle it

---

## Command Files

Place `.yml` files inside `plugins/FoliaCustomCommands/commands/`.

### Simple format — one command per file

The filename (without `.yml`) becomes the command name.

**`commands/survival.yml`**
```yaml
description: "Teleport to survival world"
permission: ""
no-permission-message: "&cYou don't have permission."
console-allowed: false

cooldown: 60
cooldown-message: "&cWait &e{remaining}s &cbefore using /survival again."

actions:
  - "[TITLE] &aTeleporting!;&7Looking for a safe zone..."
  - "[SOUND] ENTITY_ENDERMAN_TELEPORT"
  - "[CONSOLE] rtp {player} world server"
```

This creates the `/survival` command.

---

### Multi-command format — several commands in one file

Use a `commands:` section. Each key under it becomes a command name.

**`commands/misc.yml`**
```yaml
commands:

  spawn:
    description: "Go to spawn"
    permission: ""
    cooldown: 30
    cooldown-message: "&cWait &e{remaining}s."
    actions:
      - "[TITLE] &aWelcome;&7back, {player}"
      - "[SOUND] ENTITY_PLAYER_LEVELUP"
      - "[CONSOLE] spawn {player}"

  day:
    description: "Set time to day"
    permission: "myserver.day"
    console-allowed: true
    actions:
      - "[CONSOLE] time set day"
      - "[BROADCAST] &e{player} &7set the time to day."
```

---

## All Command Options

| Option | Default | Description |
|---|---|---|
| `description` | `""` | Shown in `/help` |
| `permission` | `""` | Required permission node (empty = everyone) |
| `no-permission-message` | `""` | Message when player lacks permission |
| `console-allowed` | `false` | Whether the console can run this command |
| `cooldown` | `0` | Cooldown in seconds (0 = disabled) |
| `cooldown-message` | `""` | Message shown while on cooldown. Use `{remaining}` |
| `aliases` | `[]` | List of alternative names for the command |
| `conditions.worlds` | `[]` | Whitelist of worlds where the command works |
| `conditions.worlds-blacklist` | `[]` | Worlds where the command is blocked |
| `conditions.worlds-message` | `""` | Message shown when world condition fails |
| `conditions.min-health` | `0` | Minimum health required (hearts × 2) |
| `conditions.max-health` | `0` | Maximum health allowed (0 = no limit) |
| `conditions.health-message` | `""` | Message shown when health condition fails |
| `actions` | `[]` | List of actions to execute |

---

## Actions Reference

Actions are strings in the `actions:` list. They support prefix chaining.

### Basic actions

| Prefix | Example | Description |
|---|---|---|
| *(none)* or `[MESSAGE]` | `Hello, {player}!` | Send a message to the player |
| `[CONSOLE]` | `[CONSOLE] give {player} diamond 1` | Run a command as console |
| `[BROADCAST]` | `[BROADCAST] &e{player} joined!` | Send a message to all players |
| `[ACTIONBAR]` | `[ACTIONBAR] &aCooldown: {remaining}s` | Show action bar text |
| `[TITLE]` | `[TITLE] &aLine 1;&7Line 2` | Show a title (`;` separates title and subtitle) |
| `[TITLE:fi:stay:fo]` | `[TITLE:10:60:10] &aHello` | Title with custom fade-in, stay, fade-out (ticks) |
| `[SOUND]` | `[SOUND] ENTITY_PLAYER_LEVELUP` | Play a sound at default volume and pitch |
| `[SOUND:vol:pitch]` | `[SOUND:1.0:0.8] ENTITY_BELL_RESONATE` | Play a sound with custom volume and pitch |
| `[DELAY:ticks]` | `[DELAY:40] [MESSAGE] Done!` | Wait N ticks before running the next action |

> Sound names use the Bukkit `Sound` enum (e.g., `ENTITY_ENDERMAN_TELEPORT`, `BLOCK_NOTE_BLOCK_PLING`).

---

### Conditional actions — `[IF]`

Run an action only if a condition is true. Can be chained with other prefixes.

```yaml
actions:
  - "[IF permission:myserver.vip] [MESSAGE] &6Welcome, VIP!"
  - "[IF !permission:myserver.vip] [MESSAGE] &7Get VIP for perks!"
  - "[IF world:world_nether] [BROADCAST] {player} entered the Nether!"
  - "[IF health>10] [MESSAGE] &aYou have enough health."
  - "[IF health<5] [MESSAGE] &cYou are low on health!"
```

| Condition | Description |
|---|---|
| `permission:node` | Player has the permission |
| `!permission:node` | Player does NOT have the permission |
| `world:name` | Player is in the specified world |
| `!world:name` | Player is NOT in the specified world |
| `health>N` | Player's health is greater than N |
| `health<N` | Player's health is less than N |

---

### Chance — `[CHANCE:N]`

Run an action with N% probability.

```yaml
actions:
  - "[CHANCE:10] [CONSOLE] give {player} diamond 1"
  - "[CHANCE:50] [MESSAGE] &aYou got lucky!"
```

---

## Placeholders

| Placeholder | Value |
|---|---|
| `{player}` | Player's name |
| `{world}` | Current world name |
| `{x}` | Player's X coordinate (block) |
| `{y}` | Player's Y coordinate (block) |
| `{z}` | Player's Z coordinate (block) |
| `{args}` | All command arguments joined by spaces |
| `{arg0}`, `{arg1}`... | Individual arguments by index |

Color codes use `&` (e.g., `&a` = green, `&c` = red, `&e` = yellow).

---

## Plugin Commands

| Command | Permission | Description |
|---|---|---|
| `/fccmds reload` | `fccmds.admin` (OP) | Reload all command files |

---

## Config

**`plugins/FoliaCustomCommands/config.yml`**

```yaml
# Show logs when commands are registered/unregistered
verbose-outputs: false

# Automatically reload commands when a file changes
auto-reload: true
```

---

## Building

Requirements: Java 21+, Maven 3.8+

```bash
git clone https://github.com/YOUR_USERNAME/FoliaCustomCommands.git
cd FoliaCustomCommands
mvn package
```

The compiled `.jar` will be at `target/FoliaCustomCommands-1.0.0.jar`.

---

## License

MIT
