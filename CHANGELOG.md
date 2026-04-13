# Changelog

Todos los cambios significativos de ETCCore se documentan aquí.

Este proyecto sigue [Semantic Versioning](https://semver.org/spec/v2.0.0.html) y el formato [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [Unreleased]

### Added
- Integración con Vault Economy: acciones `[MONEY_GIVE:X]`, `[MONEY_TAKE:X]` y condición `[IF money>=X]`
- Update checker: notifica a los OPs cuando hay una versión nueva en GitHub
- Command logging: auditoría de ejecuciones en `logs/commands.log` (config: `log-commands`)
- Tareas programadas: YAML en `commands/scheduled/*.yml` con `[BROADCAST]` y `[CONSOLE]`
- Soporte Geyser/Floodgate: los jugadores Bedrock ven formularios nativos en lugar de GUIs de cofre
- Paquete `listener/` separado de `manager/` para mejor organización
- bStats: métricas anónimas de uso del plugin
- Dep Maven: Vault API, Floodgate API, bStats-bukkit
- `logs/` carpeta con `commands.log` rotable

### Changed
- Plugin renombrado de `FoliaCustomCommands` a `ETCCore`
- `author` en plugin.yml actualizado a `EmmanuelTC`
- Listeners (`BuildProtectionListener`, `ChatProtectionListener`, `CommandBlockListener`) movidos al paquete `listener/`

---

## [1.0.0] - 2026-04-13

### Added
- Motor de acciones YAML completo: `[IF]`, `[CHANCE]`, `[DELAY]`, `[VAR]`, `[INPUT]`, `[MENU]`, `[CONSOLE]`, `[MESSAGE]`, `[BROADCAST]`, `[ACTIONBAR]`, `[TITLE]`, `[SOUND]`, `[CLOSE]`
- Comandos personalizados cargados desde `commands/*.yml` — formato simple y multi-comando
- Sistema de GUIs de inventario desde `menus/*.yml`
- Variables por jugador con persistencia en YAML (`playerdata/<uuid>.yml`)
- Cooldowns globales y por jugador
- Condiciones por mundo, salud y variables
- Completado automático con `arg-types`
- Protección de construcción (`fccmds.build`) y de ítems (`fccmds.items`)
- Bloqueo de comandos por grupo LP desde `blocked-commands.yml`
- Formato de chat con prefijo/sufijo LuckPerms y PlaceholderAPI desde `chat-format.yml`
- Sistema de mute temporal y permanente con `/mute` y `/unmute`
- `/enderchest` con acceso offline mediante NMS reflection
- `/invsee` con acceso offline mediante NMS reflection
- Auto-reload al guardar archivos en `commands/` y `menus/` (FileWatcher)
- Soft-dependencies: LuckPerms, PlaceholderAPI
- Compatible con Paper y Folia 1.21.1+, Java 21+
