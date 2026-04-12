# FoliaCustomCommands

Plugin nativo para **Folia / Paper** que permite crear comandos personalizados desde archivos YAML — sin necesidad de programar.

> **Compatible con**: Paper / Folia 1.21.1+  
> **Java**: 21+

---

## Tabla de contenidos

1. [Instalación](#instalación)
2. [Archivos de comandos](#archivos-de-comandos)
   - [Formato simple](#formato-simple--un-comando-por-archivo)
   - [Formato múltiple](#formato-múltiple--varios-comandos-en-un-archivo)
3. [Todas las opciones](#todas-las-opciones)
4. [Referencia de acciones](#referencia-de-acciones)
   - [Acciones básicas](#acciones-básicas)
   - [Condicionales `[IF]`](#condicionales-if)
   - [Azar `[CHANCE]`](#azar-chance)
   - [Retraso `[DELAY]`](#retraso-delay)
5. [Placeholders](#placeholders)
6. [Comandos del plugin](#comandos-del-plugin)
7. [Permisos](#permisos)
8. [Comando `/enderchest`](#comando-enderchest)
9. [config.yml](#configyml)
10. [Compilar](#compilar)
11. [Licencia](#licencia)

---

## Instalación

1. Descarga o compila el `.jar` (ver [Compilar](#compilar))
2. Colócalo en la carpeta `plugins/` de tu servidor
3. Inicia el servidor — se creará `plugins/FoliaCustomCommands/commands/` con archivos de ejemplo
4. Edita o crea archivos `.yml` en esa carpeta
5. Usa `/fccmds reload` o deja que el auto-reload lo detecte solo

---

## Archivos de comandos

Los archivos `.yml` van en `plugins/FoliaCustomCommands/commands/`.  
Hay dos formatos:

### Formato simple — un comando por archivo

El nombre del archivo (sin `.yml`) se convierte en el nombre del comando.

**`commands/survival.yml`**
```yaml
description: "Teletransporte al mundo survival"
permission: ""
no-permission-message: "&cNo tienes permiso."
console-allowed: false

cooldown: 60
cooldown-message: "&cEspera &e{remaining}s &cpara usar /survival de nuevo."

actions:
  - "[TITLE] &a¡Teletransportando!;&7Buscando zona segura..."
  - "[SOUND] ENTITY_ENDERMAN_TELEPORT"
  - "[CONSOLE] rtp {player} world_survival"
```

Esto registra el comando `/survival`.

---

### Formato múltiple — varios comandos en un archivo

Usa una sección `commands:`. Cada clave dentro se convierte en un comando.

**`commands/inicio.yml`**
```yaml
commands:

  inicio:
    description: "Ir al spawn"
    permission: ""
    cooldown: 30
    cooldown-message: "&cEspera &e{remaining}s."
    actions:
      - "[TITLE] &aBienvenido;&7de vuelta, {player}"
      - "[SOUND] ENTITY_PLAYER_LEVELUP"
      - "[CONSOLE] spawn {player}"

  dia:
    description: "Poner el tiempo en día"
    permission: "miserver.dia"
    console-allowed: true
    actions:
      - "[CONSOLE] time set day"
      - "[BROADCAST] &e{player} &7puso el tiempo en día."
```

---

## Todas las opciones

| Opción | Por defecto | Descripción |
|--------|-------------|-------------|
| `description` | `""` | Descripción visible en `/help` |
| `permission` | `""` | Nodo de permiso requerido (vacío = todos) |
| `no-permission-message` | `""` | Mensaje cuando el jugador no tiene permiso |
| `console-allowed` | `false` | ¿Puede la consola ejecutar este comando? |
| `cooldown` | `0` | Cooldown en segundos (0 = deshabilitado) |
| `cooldown-message` | `""` | Mensaje durante cooldown. Usa `{remaining}` |
| `aliases` | `[]` | Nombres alternativos del comando |
| `conditions.worlds` | `[]` | Mundos donde el comando **sí** funciona (whitelist) |
| `conditions.worlds-blacklist` | `[]` | Mundos donde el comando **no** funciona |
| `conditions.worlds-message` | `""` | Mensaje cuando falla la condición de mundo |
| `conditions.min-health` | `0` | Vida mínima requerida (en medios corazones) |
| `conditions.health-message` | `""` | Mensaje cuando falla la condición de vida |

---

## Referencia de acciones

Las acciones son cadenas de texto en la lista `actions:`. Soportan prefijos encadenables.

### Acciones básicas

| Prefijo | Ejemplo | Descripción |
|---------|---------|-------------|
| *(sin prefijo)* o `[MESSAGE]` | `&aHola, {player}!` | Mensaje al jugador |
| `[CONSOLE]` | `[CONSOLE] give {player} diamond 1` | Ejecuta un comando como consola |
| `[BROADCAST]` | `[BROADCAST] &e{player} &7se unió!` | Mensaje a todos los jugadores |
| `[ACTIONBAR]` | `[ACTIONBAR] &aVida: {health}` | Texto en la barra de acción |
| `[TITLE]` | `[TITLE] &aTítulo;&7Subtítulo` | Título en pantalla (`;` separa título y subtítulo) |
| `[TITLE:fi:stay:fo]` | `[TITLE:10:60:10] &aHola` | Título con fade-in, duración y fade-out en ticks |
| `[SOUND]` | `[SOUND] ENTITY_PLAYER_LEVELUP` | Reproduce un sonido (volumen y pitch por defecto) |
| `[SOUND:vol:pitch]` | `[SOUND:1.0:0.5] BLOCK_NOTE_BLOCK_PLING` | Sonido con volumen y pitch personalizados |
| `[DELAY:ticks]` | `[DELAY:40] [MESSAGE] ¡Listo!` | Espera N ticks antes de ejecutar la siguiente acción |

> Los nombres de sonido usan el enum `Sound` de Bukkit (ej. `ENTITY_ENDERMAN_TELEPORT`, `BLOCK_NOTE_BLOCK_PLING`).

---

### Condicionales `[IF]`

Ejecuta una acción solo si la condición es verdadera. Se puede combinar con cualquier otro prefijo.

```yaml
actions:
  - "[IF permission:miserver.vip] [MESSAGE] &6¡Bienvenido, VIP!"
  - "[IF !permission:miserver.vip] [MESSAGE] &7¡Hazte VIP para más beneficios!"
  - "[IF world:world_nether] [BROADCAST] {player} entró al Nether."
  - "[IF health>10] [MESSAGE] &aTienes suficiente vida."
  - "[IF health<5] [MESSAGE] &c¡Salud baja!"
```

| Condición | Descripción |
|-----------|-------------|
| `permission:nodo` | El jugador **tiene** el permiso |
| `!permission:nodo` | El jugador **no tiene** el permiso |
| `world:nombre` | El jugador **está** en ese mundo |
| `!world:nombre` | El jugador **no está** en ese mundo |
| `health>N` | La vida del jugador es **mayor** que N |
| `health<N` | La vida del jugador es **menor** que N |

---

### Azar `[CHANCE]`

Ejecuta una acción con probabilidad N% (0–100).

```yaml
actions:
  - "[CHANCE:10] [CONSOLE] give {player} diamond 1"
  - "[CHANCE:50] [MESSAGE] &a¡Tuviste suerte!"
  - "[CHANCE:100] [MESSAGE] Esto siempre aparece."
```

---

### Retraso `[DELAY]`

Espera N ticks antes de ejecutar la acción. 20 ticks = 1 segundo.  
Se puede combinar con cualquier prefijo, incluyendo `[IF]` y `[CHANCE]`.

```yaml
actions:
  - "[MESSAGE] &eProcesando..."
  - "[DELAY:60] [MESSAGE] &a¡Terminado! (3 segundos después)"
  - "[DELAY:100] [IF permission:admin] [CONSOLE] say {player} fue promovido"
```

---

## Placeholders

| Placeholder | Valor |
|-------------|-------|
| `{player}` | Nombre del jugador |
| `{world}` | Nombre del mundo actual |
| `{x}` | Coordenada X (bloque) |
| `{y}` | Coordenada Y (bloque) |
| `{z}` | Coordenada Z (bloque) |
| `{args}` | Todos los argumentos del comando unidos por espacio |
| `{arg0}`, `{arg1}`… | Argumentos individuales por índice |

Los colores usan `&` (ej. `&a` = verde, `&c` = rojo, `&e` = amarillo, `&6` = naranja).

---

## Comandos del plugin

| Comando | Permiso | Descripción |
|---------|---------|-------------|
| `/fccmds reload` | `fccmds.admin` | Recarga todos los archivos de `commands/` |
| `/enderchest` | `fccmds.enderchest` | Abre tu propio enderchest |
| `/enderchest <jugador>` | `fccmds.enderchest.others` | Abre el enderchest de otro jugador (online u offline) |

---

## Permisos

| Permiso | Por defecto | Descripción |
|---------|-------------|-------------|
| `fccmds.admin` | OP | Permite recargar la configuración con `/fccmds reload` |
| `fccmds.enderchest` | Todos | Permite abrir tu propio enderchest |
| `fccmds.enderchest.others` | OP | Permite ver y editar el enderchest de otros jugadores |

---

## Comando `/enderchest`

Comando integrado en el plugin (no necesita archivo `.yml`).

### Uso

```
/enderchest                  → Abre tu propio enderchest
/enderchest <nombre>         → Abre el enderchest de otro jugador
```

### Comportamiento según el estado del jugador

| Situación | Qué hace |
|-----------|----------|
| Jugador **online** | Abre su enderchest en vivo. Los cambios se reflejan de inmediato. |
| Jugador **offline** | Lee su `playerdata/<uuid>.dat` y muestra el inventario. Al cerrar, **guarda los cambios** automáticamente. |
| Jugador offline que **se conecta** mientras tienes el chest abierto | El guardado se **cancela** al cerrar para evitar sobrescribir su sesión activa. Se avisa al admin. |
| Jugador que **no existe / nunca jugó** | Mensaje de error. No hace nada. |

### Notas

- El guardado offline es **completo**: preserva enchantments, nombre, lore y todos los componentes del ítem en 1.21+.
- La escritura al `.dat` es **asíncrona** para no bloquear el hilo del servidor.
- Los cambios al enderchest de un jugador **online** se guardan automáticamente cuando el jugador cierre sesión (comportamiento nativo de Minecraft).

---

## config.yml

```yaml
# Muestra logs al registrar/desactivar comandos (por defecto: false)
verbose-outputs: false

# Recarga automáticamente los archivos de commands/ cuando detecta cambios (por defecto: true)
auto-reload: true
```

---

## Compilar

Requisitos: Java 21+, Maven 3.8+

```bash
git clone https://github.com/YOUR_USERNAME/FoliaCustomCommands.git
cd FoliaCustomCommands
mvn package
```

El `.jar` compilado estará en `target/FoliaCustomCommands-1.0.0.jar`.

---

## Licencia

MIT

