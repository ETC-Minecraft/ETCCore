# FoliaCustomCommands

Plugin nativo para **Folia / Paper** que permite crear comandos personalizados desde archivos YAML, menús de inventario GUI, variables por jugador, protección de construcción y más — sin necesidad de programar.

> **Compatible con**: Paper / Folia 1.21.1+  
> **Java**: 21+

---

## Tabla de contenidos

1. [Instalación](#instalación)
2. [Archivos de comandos](#archivos-de-comandos)
   - [Formato simple](#formato-simple--un-comando-por-archivo)
   - [Formato múltiple](#formato-múltiple--varios-comandos-en-un-archivo)
3. [Todas las opciones de comando](#todas-las-opciones-de-comando)
4. [Referencia de acciones](#referencia-de-acciones)
   - [Acciones básicas](#acciones-básicas)
   - [Condicionales `[IF]`](#condicionales-if)
   - [Azar `[CHANCE]`](#azar-chance)
   - [Retraso `[DELAY]`](#retraso-delay)
   - [Variables `[VAR]`](#variables-var)
   - [Input de chat `[INPUT]`](#input-de-chat-input)
   - [Menús `[MENU]`](#menús-menu)
5. [Placeholders](#placeholders)
6. [PlaceholderAPI](#placeholderapi)
7. [Args tipados y tab-complete](#args-tipados-y-tab-complete)
8. [Cooldown global](#cooldown-global)
9. [Menús de inventario (GUIs)](#menús-de-inventario-guis)
10. [Protección de construcción](#protección-de-construcción)
11. [Permisos dinámicos para LuckPerms](#permisos-dinámicos-para-luckperms)
12. [Comandos del plugin](#comandos-del-plugin)
13. [Permisos](#permisos)
14. [Comando `/enderchest`](#comando-enderchest)
15. [Comando `/invsee`](#comando-invsee)
16. [config.yml](#configyml)
17. [Compilar](#compilar)

---

## Instalación

1. Descarga o compila el `.jar` (ver [Compilar](#compilar))
2. Colócalo en la carpeta `plugins/` de tu servidor
3. Inicia el servidor — se crearán `plugins/FoliaCustomCommands/commands/` y `menus/` con archivos de ejemplo
4. Edita o crea archivos `.yml` en esas carpetas
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
  - "[TITLE:10:70:20] &a¡Teletransportando!;&7Buscando zona segura..."
  - "[SOUND] ENTITY_ENDERMAN_TELEPORT"
  - "[CONSOLE] rtp {player} world_survival"
```

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
      - "[TITLE:10:70:20] &aBienvenido;&7de vuelta, {player}"
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

## Todas las opciones de comando

| Opción | Por defecto | Descripción |
|---|---|---|
| `description` | `""` | Descripción visible en `/help` |
| `permission` | `""` | Nodo de permiso requerido. Vacío = cualquiera puede usarlo |
| `no-permission-message` | `"&cNo tienes permiso."` | Mensaje cuando el jugador no tiene permiso |
| `console-allowed` | `false` | Si la consola puede ejecutar este comando |
| `cooldown` | `0` | Cooldown **por jugador** en segundos (`0` = deshabilitado) |
| `global-cooldown` | `0` | Cooldown **compartido entre todos** en segundos (`0` = deshabilitado) |
| `cooldown-message` | (predefinido) | Mensaje durante cooldown. Usa `{remaining}` y `{command}` |
| `aliases` | `[]` | Nombres alternativos del comando |
| `arg-types` | `{}` | Tipos de argumentos para tab-complete por índice |
| `conditions.worlds` | `[]` | Lista de mundos donde el comando **sí** funciona (whitelist) |
| `conditions.worlds-blacklist` | `[]` | Lista de mundos donde el comando **no** funciona |
| `conditions.worlds-message` | (predefinido) | Mensaje cuando falla la condición de mundo |
| `conditions.min-health` | `0` | Vida mínima requerida en medios corazones |
| `conditions.health-message` | (predefinido) | Mensaje cuando falla la condición de vida |

---

## Referencia de acciones

Las acciones son cadenas de texto en la lista `actions:`. Los prefijos son **encadenables** entre sí.

### Acciones básicas

| Prefijo | Descripción |
|---|---|
| *(sin prefijo)* | El jugador ejecuta ese texto como si lo hubiera escrito en el chat |
| `[MESSAGE] texto` | Mensaje privado al jugador (admite `&colores`) |
| `[CONSOLE] cmd` | Ejecuta un comando como consola |
| `[BROADCAST] texto` | Mensaje visible para todos los jugadores online |
| `[ACTIONBAR] texto` | Texto sobre el hotbar del jugador |
| `[TITLE] título;subtítulo` | Título con tiempos por defecto (fade 10, stay 70, fade 20 ticks) |
| `[TITLE:fi:st:fo] título;subtítulo` | Título con tiempos en ticks personalizados |
| `[SOUND] NOMBRE` | Sonido al jugador (volumen 1.0, pitch 1.0) |
| `[SOUND:vol:pitch] NOMBRE` | Sonido con volumen y pitch personalizados |

```yaml
actions:
  - "[MESSAGE] &aHola, {player}!"
  - "[CONSOLE] give {player} diamond 1"
  - "[BROADCAST] &e{player} &7llegó al servidor."
  - "[ACTIONBAR] &e¡Bienvenido!"
  - "[TITLE] &a¡Hola!;&7Bienvenido al servidor"
  - "[TITLE:10:60:10] &a¡Hola!;&7Bienvenido"
  - "[SOUND] ENTITY_PLAYER_LEVELUP"
  - "[SOUND:0.8:2.0] BLOCK_NOTE_BLOCK_PLING"
```

---

### Condicionales `[IF]`

Sintaxis: `[IF condición] acción_si_verdadero`

> No existe rama "else" nativa — para el caso contrario añade otra línea con la condición negada (`!`).

```yaml
actions:
  # Permiso
  - "[IF permission:fccmds.vip] [MESSAGE] &6¡Eres VIP!"
  - "[IF !permission:fccmds.vip] [MESSAGE] &7No eres VIP."

  # Mundo
  - "[IF world:world_nether] [BROADCAST] {player} entró al Nether."
  - "[IF !world:world] [MESSAGE] &cEsto solo funciona en el mundo principal."

  # Vida (medios corazones: 20.0 = 10 corazones llenos)
  - "[IF health>10.0] [MESSAGE] &aTienes más de 5 corazones."
  - "[IF health<4.0] [MESSAGE] &c¡Cuídate, tienes poca vida!"

  # Variables numéricas
  - "[IF var:puntos>=100] [CONSOLE] give {player} diamond 1"
  - "[IF var:puntos<100] [MESSAGE] &cNecesitas al menos 100 puntos."

  # Variables de texto (igualdad y desigualdad)
  - "[IF var:estado=activo] [MESSAGE] &aTu cuenta está activa."
  - "[IF var:estado!=activo] [MESSAGE] &cTu cuenta está inactiva."

  # Encadenado (IF dentro de IF)
  - "[IF world:mundo_pvp] [IF health<8.0] [PLAYER] spawn"
```

| Condición | Descripción |
|---|---|
| `permission:nodo` | El jugador **tiene** el permiso |
| `!permission:nodo` | El jugador **no tiene** el permiso |
| `world:nombre` | El jugador **está** en ese mundo |
| `!world:nombre` | El jugador **no está** en ese mundo |
| `health>N` | La vida es **mayor** que N |
| `health<N` | La vida es **menor** que N |
| `var:nombre=valor` | La variable es **igual** al valor |
| `var:nombre!=valor` | La variable es **distinta** del valor |
| `var:nombre>N` | La variable numérica es **mayor** que N |
| `var:nombre<N` | La variable numérica es **menor** que N |
| `var:nombre>=N` | **Mayor o igual** que N |
| `var:nombre<=N` | **Menor o igual** que N |

---

### Azar `[CHANCE]`

Sintaxis: `[CHANCE:N] acción` — ejecuta la acción con probabilidad N% (0–100).  
Cada línea es independiente: puede cumplirse más de una en la misma ejecución.

```yaml
actions:
  - "[CHANCE:10] [CONSOLE] give {player} netherite_ingot 1"
  - "[CHANCE:40] [CONSOLE] give {player} diamond 2"
  - "[CHANCE:80] [CONSOLE] give {player} gold_ingot 5"
  # Combinable con IF:
  - "[IF permission:vip] [CHANCE:25] [CONSOLE] give {player} elytra 1"
```

---

### Retraso `[DELAY]`

Sintaxis: `[DELAY:N] acción` — espera N ticks antes de ejecutar la acción.  
`20 ticks = 1 segundo`. Combinable con cualquier otro prefijo.

```yaml
actions:
  - "[MESSAGE] &eCuenta regresiva..."
  - "[DELAY:20] [BROADCAST] &c3..."
  - "[DELAY:40] [BROADCAST] &e2..."
  - "[DELAY:60] [BROADCAST] &a1..."
  - "[DELAY:80] [BROADCAST] &b&l¡YA!"
  # Con IF y CHANCE:
  - "[DELAY:100] [IF permission:admin] [CHANCE:50] [CONSOLE] say Sorteo completado"
```

---

### Variables `[VAR]`

Las variables se guardan por jugador en `plugins/FoliaCustomCommands/playerdata/<uuid>.yml` y persisten entre sesiones.

| Acción | Sintaxis | Descripción |
|---|---|---|
| `[VAR:SET]` | `[VAR:SET] nombre = expresión` | Asigna o sobreescribe el valor |
| `[VAR:ADD]` | `[VAR:ADD] nombre = N` | Alias de `SET`, semánticamente "sumar N" |
| `[VAR:DEL]` | `[VAR:DEL] nombre` | Elimina la variable |

La expresión puede usar `{var:nombre}` combinado con operadores `+ - * /`:

```yaml
actions:
  # Asignar valor fijo
  - "[VAR:SET] rango = Novato"
  - "[VAR:SET] puntos = 0"

  # Sumar 50 al valor actual
  - "[VAR:SET] puntos = {var:puntos} + 50"

  # Restar — [VAR:ADD] con número negativo
  - "[VAR:ADD] puntos = -100"

  # Multiplicar
  - "[VAR:SET] bonus = {var:puntos} * 2"

  # Eliminar
  - "[VAR:DEL] rango"

  # Usar en mensaje
  - "[MESSAGE] &7Tienes &e{var:puntos} &7puntos. Rango: &6{var:rango}"

  # Usar en comando de consola
  - "[IF var:rango!=] [CONSOLE] lp user {player} parent set {var:rango}"
```

> **Operadores soportados**: `+` `-` `*` `/` (una sola operación por expresión)

---

### Input de chat `[INPUT]`

Sintaxis: `[INPUT] variable;mensaje_al_jugador`

Muestra un mensaje al jugador y espera a que escriba en el chat. Lo escrito se guarda en la variable. **El mensaje en chat se cancela** — no lo ven otros jugadores.  
El mensaje/prompt al jugador es opcional.

```yaml
# Pedir el nombre de destino
pre-tp:
  actions:
    - "[INPUT] destino;&e¿A qué mundo quieres ir? Escribe el nombre:"
    # El jugador escribe → guardado en {var:destino}

# Otro comando usa el resultado
confirmar-tp:
  actions:
    - "[IF var:destino!=] [CONSOLE] mvtp {player} {var:destino}"
    - "[IF var:destino=] [MESSAGE] &cEscribe primero el destino con /pre-tp"
```

```yaml
# Multi-paso: nombre + apellido
registro:
  actions:
    - "[INPUT] reg_nombre;&7Paso 1/2 — Escribe tu nombre:"
    # (el jugador escribe)
    # (el juego invoca la siguiente acción cuando ya tiene reg_nombre)
    - "[INPUT] reg_apellido;&7Paso 2/2 — Escribe tu apellido:"
    - "[MESSAGE] &aRegistrado como &e{var:reg_nombre} {var:reg_apellido}&a."
```

---

### Menús `[MENU]`

Sintaxis: `[MENU] nombre`

Abre el menú definido en `plugins/FoliaCustomCommands/menus/<nombre>.yml`.

```yaml
actions:
  - "[MENU] principal"
  # Combinable con condición:
  - "[IF permission:vip] [MENU] tienda-vip"
  - "[IF !permission:vip] [MESSAGE] &cEsta tienda es solo para VIP."
```

---

## Placeholders

Disponibles en cualquier texto de acción, nombre de ítem, lore, mensajes, etc.

| Placeholder | Valor |
|---|---|
| `{player}` | Nombre del jugador |
| `{world}` | Mundo actual del jugador |
| `{x}` | Coordenada X (bloque) |
| `{y}` | Coordenada Y (bloque) |
| `{z}` | Coordenada Z (bloque) |
| `{args}` | Todos los argumentos del comando, separados por espacio |
| `{arg0}`, `{arg1}`, `{arg2}`… | Argumento individual por índice (empieza en 0) |
| `{var:nombre}` | Valor de una variable del jugador |

**Códigos de color** (`&` + código):

| Código | Color | Código | Color |
|---|---|---|---|
| `&0` | Negro | `&8` | Gris oscuro |
| `&1` | Azul oscuro | `&9` | Azul |
| `&2` | Verde oscuro | `&a` | Verde |
| `&3` | Cian oscuro | `&b` | Aqua |
| `&4` | Rojo oscuro | `&c` | Rojo |
| `&5` | Morado | `&d` | Rosa |
| `&6` | Naranja/Oro | `&e` | Amarillo |
| `&7` | Gris | `&f` | Blanco |
| `&l` | **Negrita** | `&o` | *Cursiva* |
| `&m` | ~~Tachado~~ | `&n` | Subrayado |
| `&r` | Reset | `&k` | Obfuscado |

---

## PlaceholderAPI

Si **PlaceholderAPI** está instalado, sus placeholders (`%...%`) funcionan automáticamente en todas las acciones sin configuración adicional:

```yaml
actions:
  - "[MESSAGE] &7Tu dinero: &e%vault_balance%"
  - "[MESSAGE] &7Rango: &b%luckperms_prefix%"
  - "[IF var:puntos>0] [MESSAGE] &7TPS: &a%server_tps_1%"
  - "[TITLE] &aHola %player_name%;&7Dinero: %vault_balance%"
```

El plugin detecta PAPI en runtime — si no está instalado, los `%placeholders%` se dejan sin sustituir sin lanzar ningún error.

---

## Args tipados y tab-complete

Define el tipo de cada argumento para activar autocompletado al escribir el comando:

```yaml
# commands/dar-rango.yml
permission: "admin.rango"
arg-types:
  0: player   # primer argumento → lista de jugadores online
  1: text     # segundo argumento → texto libre, sin sugerencias
actions:
  - "[CONSOLE] lp user {arg0} parent set {arg1}"
  - "[MESSAGE] &aRango &e{arg1} &aaplicado a &e{arg0}&a."
```

| Tipo | Tab-complete |
|---|---|
| `player` | Jugadores online que coincidan con lo escrito |
| `number` | Sin sugerencias |
| `text` | Sin sugerencias (texto libre) |

---

## Cooldown global

`global-cooldown` bloquea el comando para **todos los jugadores** a la vez desde que alguien lo usa.  
`cooldown` (sin "global") solo afecta al jugador que lo usó.  
Ambos pueden usarse simultáneamente.

```yaml
# commands/evento.yml
permission: "miserver.evento"
cooldown: 600           # este jugador: no puede repetirlo en 10 min
global-cooldown: 3600   # todos: nadie puede usarlo en 1 hora tras que alguien lo active
cooldown-message: "&cEl evento se activó hace poco. Espera &e{remaining}s."
actions:
  - "[BROADCAST] &6&l★ EVENTO ACTIVO ★ &fActivado por {player}."
  - "[CONSOLE] evento start"
```

---

## Menús de inventario (GUIs)

Los menús van en `plugins/FoliaCustomCommands/menus/`. El nombre del archivo (sin `.yml`) es el identificador con el que se abre.

**`menus/principal.yml`**
```yaml
title: "&8&lPanel principal"
rows: 3                  # 1 a 6 filas (9 slots por fila)
permission: ""           # vacío = cualquiera puede abrirlo
                         # con valor: requiere ese nodo de permiso

items:
  13:                    # número de slot (0-based, izquierda→derecha, arriba→abajo)
    material: COMPASS
    name: "&aIr al spawn"
    lore:
      - "&7Haz clic para teleportarte"
      - "&7Tus puntos: &e{var:puntos}"   # los placeholders funcionan aquí
    glow: true
    close-on-click: true
    custom-model-data: 0
    actions:
      - "[CONSOLE] spawn {player}"
      - "[MESSAGE] &a¡Teletransportado!"

  22:
    material: BOOK
    name: "&bMi perfil"
    actions:
      - "[MENU] perfil"   # abre otro menú

  26:
    material: BARRIER
    name: "&cCerrar"
    actions:
      - "[CLOSE]"         # cierra el inventario
```

### Opciones de ítem

| Opción | Por defecto | Descripción |
|---|---|---|
| `material` | `STONE` | Nombre del material ([lista oficial](https://jd.papermc.io/paper/1.21/org/bukkit/Material.html)) |
| `name` | `""` | Nombre del ítem. Admite `&colores` y `{var:x}` |
| `lore` | `[]` | Líneas de descripción. Admite `&colores` y `{var:x}` |
| `glow` | `false` | Brillo visual sin enchantment visible |
| `close-on-click` | `true` | Cierra el menú al hacer clic en este ítem |
| `custom-model-data` | `0` | CustomModelData para resource packs |
| `actions` | `[]` | Acciones al hacer clic. Admite todos los prefijos |

### Acción especial `[CLOSE]`

```yaml
actions:
  - "[CLOSE]"    # cierra el inventario del jugador
```

### Campo `permission:` en menús

```yaml
title: "&2Tienda VIP"
rows: 4
permission: "fccmds.menus.tienda"
```

Si el jugador intenta abrirlo sin ese nodo verá: `&cNo tienes permiso para abrir este menú.`

---

## Protección de construcción

Sistema nativo para bloquear colocar y romper bloques a jugadores sin permiso. Reemplaza al antiguo `minecraft.build` / `minecraft.break` que no funciona en Paper/Folia sin EssentialsX.

Se activa en `config.yml` con `build-protection: true` (activo por defecto).

| Nodo | Default | Descripción |
|---|---|---|
| `fccmds.build` | OP | Permite colocar y romper bloques |
| `fccmds.build.bypass` | OP | Ignora la protección completamente (para staff) |

### Setup típico con LuckPerms

```bash
# El grupo default (nebulosa, civiles...) no tiene fccmds.build → bloqueado sin config
# Solo hay que dar el permiso al grupo "aprobado":
/lp group cometa permission set fccmds.build true

# Staff nunca bloqueado:
/lp group enana_blanca permission set fccmds.build.bypass true
# enana_blanca hereda hacia supernova y agujero_negro automáticamente
```

**Comportamiento por modo de juego**:

| Situación | Resultado |
|---|---|
| Survival/Adventure sin `fccmds.build` | Bloqueado. Mensaje en action bar |
| Creative sin `fccmds.build` | Bloqueado también (evita exploit con `/gmc`) |
| Cualquier modo con `fccmds.build.bypass` | Libre, sin mensaje |
| Modo espectador | Nunca bloqueado (no puede interactuar físicamente) |

---

## Permisos dinámicos para LuckPerms

Al cargar comandos y menús, el plugin registra automáticamente nodos en Bukkit:

- `fccmds.commands.<nombre>` — uno por cada comando YAML cargado
- `fccmds.menus.<nombre>` — uno por cada menú `.yml` cargado

Esto hace que aparezcan como **sugerencias en LuckPerms** cuando escribes `/lp group X permission set fccmds.`.

**Default `true`**: todos tienen acceso. Úsalos para **denegar** acceso a grupos específicos:

```bash
# Bloquear /kit-diario al grupo nebulosa:
/lp group nebulosa permission set fccmds.commands.kit-diario false

# Restringir el menú tienda a jugadores VIP:
/lp group nebulosa permission set fccmds.menus.tienda false
/lp group pulsar   permission set fccmds.menus.tienda true
```

Al hacer `/fccmds reload`, los nodos de comandos eliminados se desregistran y los nuevos se registran automáticamente.

---

## Comandos del plugin

| Comando | Permiso | Descripción |
|---|---|---|
| `/fccmds reload` | `fccmds.admin` | Recarga todos los archivos de `commands/` y `menus/` |
| `/fccmds onlinemode` | `fccmds.admin` | Muestra el estado actual de online-mode |
| `/fccmds onlinemode <true\|false>` | `fccmds.admin` | Cambia online-mode en caliente sin reiniciar |
| `/enderchest` | `fccmds.enderchest` | Abre tu propio enderchest |
| `/enderchest <jugador>` | `fccmds.enderchest.others` | Abre el enderchest de otro jugador (online u offline) |
| `/invsee <jugador>` | `fccmds.invsee` | Ve y edita el inventario completo de otro jugador |

### `/fccmds onlinemode`

Cambia el modo de autenticación **en tiempo de ejecución** sin hacer stop/start. Afecta solo a nuevas conexiones.

```
/fccmds onlinemode           → muestra el estado actual (ON / OFF)
/fccmds onlinemode false     → desactiva auth (cualquier cliente puede conectar)
/fccmds onlinemode true      → reactiva auth (solo cuentas premium)
```

> El cambio no persiste en `server.properties` — al reiniciar vuelve al valor del archivo.

---

## Permisos

| Permiso | Default | Descripción |
|---|---|---|
| `fccmds.admin` | OP | Usar `/fccmds reload` y `/fccmds onlinemode` |
| `fccmds.enderchest` | Todos | Abrir tu propio enderchest |
| `fccmds.enderchest.others` | OP | Ver/editar enderchest de otros jugadores |
| `fccmds.invsee` | OP | Ver inventario de jugadores online |
| `fccmds.invsee.offline` | OP | Ver/editar inventario de jugadores offline |
| `fccmds.build` | OP | Permite colocar y romper bloques |
| `fccmds.build.bypass` | OP | Ignora completamente la protección de construcción |
| `fccmds.commands.<nombre>` | Todos | Auto-generado por cada comando YAML cargado |
| `fccmds.menus.<nombre>` | Todos | Auto-generado por cada menú `.yml` cargado |

---

## Comando `/enderchest`

```
/enderchest                  → Abre tu propio enderchest
/enderchest <nombre>         → Abre el enderchest de otro jugador
```

| Situación | Comportamiento |
|---|---|
| Jugador **online** | Abre su enderchest en vivo. Cambios inmediatos |
| Jugador **offline** | Lee `playerdata/<uuid>.dat` vía NMS. Guarda cambios al cerrar |
| Offline que se **conecta mientras tienes el chest abierto** | Guardado cancelado para evitar conflictos. Aviso al admin |
| Jugador que **no existe / nunca jugó** | Mensaje de error |

---

## Comando `/invsee`

```
/invsee <nombre>
```

Inventario completo (36 slots + armadura + offhand) en vista de 54 slots:

| Slots en vista | Contenido |
|---|---|
| 0 – 26 | Inventario principal (filas 2, 3 y 4) |
| 27 – 35 | Hotbar (fila 1, slots 0–8) |
| 36 – 39 | Armadura (botas → casco) |
| 40 | Offhand |

| Situación | Comportamiento |
|---|---|
| Jugador **online** | Vista en vivo, cambios inmediatos |
| Jugador **offline** | Lee el `.dat`, guarda al cerrar |
| Offline se **conecta mientras lo tienes abierto** | Guardado cancelado, aviso al admin |

---

## config.yml

```yaml
# ── General ────────────────────────────────────────────────────────────────
# Muestra logs al registrar/desregistrar comandos
verbose-outputs: false

# Recarga automáticamente commands/ y menus/ cuando detecta cambios guardados
auto-reload: true

# ── Protección de construcción ─────────────────────────────────────────────
# true  = activo (bloquea a jugadores sin fccmds.build)
# false = desactivado (usa otro plugin para gestionar esto)
build-protection: true

# Mensaje en la action bar cuando se bloquea al jugador. Admite &colores.
build-protection-message: "&cNecesitas ser aprobado para construir o romper bloques."
```

---

## Compilar

Requisitos: Java 21+, Maven 3.8+

```bash
git clone https://github.com/TU_USUARIO/FoliaCustomCommands.git
cd FoliaCustomCommands
mvn package
```

El `.jar` compilado estará en `target/FoliaCustomCommands-1.0.0.jar`.
# FoliaCustomCommands

Plugin nativo para **Folia / Paper** que permite crear comandos personalizados desde archivos YAML, menús de inventario, variables por jugador, protección de construcción y más — sin necesidad de programar.

> **Compatible con**: Paper / Folia 1.21.1+  
> **Java**: 21+

---

## Tabla de contenidos

1. [Instalación](#instalación)
2. [Archivos de comandos](#archivos-de-comandos)
   - [Formato simple](#formato-simple--un-comando-por-archivo)
   - [Formato múltiple](#formato-múltiple--varios-comandos-en-un-archivo)
3. [Todas las opciones de comando](#todas-las-opciones-de-comando)
4. [Referencia de acciones](#referencia-de-acciones)
   - [Acciones básicas](#acciones-básicas)
   - [Condicionales `[IF]`](#condicionales-if)
   - [Azar `[CHANCE]`](#azar-chance)
   - [Retraso `[DELAY]`](#retraso-delay)
   - [Variables `[VAR]`](#variables-var)
   - [Input de chat `[INPUT]`](#input-de-chat-input)
   - [Menús `[MENU]`](#menús-menu)
5. [Placeholders](#placeholders)
6. [PlaceholderAPI](#placeholderapi)
7. [Args tipados y tab-complete](#args-tipados-y-tab-complete)
8. [Cooldown global](#cooldown-global)
9. [Menús de inventario (GUIs)](#menús-de-inventario-guis)
10. [Comandos del plugin](#comandos-del-plugin)
11. [Permisos](#permisos)
12. [Comando `/enderchest`](#comando-enderchest)
13. [Comando `/invsee`](#comando-invsee)
14. [config.yml](#configyml)
15. [Compilar](#compilar)
16. [Licencia](#licencia)

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

## Todas las opciones de comando

| Opción | Por defecto | Descripción |
|--------|-------------|-------------|
| `description` | `""` | Descripción visible en `/help` |
| `permission` | `""` | Nodo de permiso requerido (vacío = todos) |
| `no-permission-message` | `""` | Mensaje cuando el jugador no tiene permiso |
| `console-allowed` | `false` | ¿Puede la consola ejecutar este comando? |
| `cooldown` | `0` | Cooldown **por jugador** en segundos (0 = deshabilitado) |
| `global-cooldown` | `0` | Cooldown **global** en segundos — afecta a todos (0 = deshabilitado) |
| `cooldown-message` | `""` | Mensaje durante cooldown. Usa `{remaining}` |
| `aliases` | `[]` | Nombres alternativos del comando |
| `conditions.worlds` | `[]` | Mundos donde el comando **sí** funciona (whitelist) |
| `conditions.worlds-blacklist` | `[]` | Mundos donde el comando **no** funciona |
| `conditions.worlds-message` | `""` | Mensaje cuando falla la condición de mundo |
| `conditions.min-health` | `0` | Vida mínima requerida (en medios corazones) |
| `conditions.health-message` | `""` | Mensaje cuando falla la condición de vida |
| `arg-types` | `{}` | Tipos de argumentos para tab-complete por índice |

---

## Referencia de acciones

Las acciones son cadenas de texto en la lista `actions:`. Soportan prefijos encadenables.

### Acciones básicas

| Prefijo | Ejemplo | Descripción |
|---------|---------|-------------|
| *(sin prefijo)* o `[MESSAGE]` | `&aHola, {player}!` | Mensaje al jugador |
| `[CONSOLE]` | `[CONSOLE] give {player} diamond 1` | Ejecuta un comando como consola |
| `[BROADCAST]` | `[BROADCAST] &e{player} &7se unió!` | Mensaje a todos los jugadores |
| `[ACTIONBAR]` | `[ACTIONBAR] &a¡Bienvenido!` | Texto en la barra de acción |
| `[TITLE]` | `[TITLE] &aTítulo;&7Subtítulo` | Título en pantalla (`;` separa título y subtítulo) |
| `[TITLE:fi:stay:fo]` | `[TITLE:10:60:10] &aHola` | Título con fade-in, duración y fade-out en ticks |
| `[SOUND]` | `[SOUND] ENTITY_PLAYER_LEVELUP` | Reproduce un sonido |
| `[SOUND:vol:pitch]` | `[SOUND:1.0:0.5] BLOCK_NOTE_BLOCK_PLING` | Sonido con volumen y pitch personalizados |
| `[DELAY:ticks]` | `[DELAY:40] [MESSAGE] ¡Listo!` | Espera N ticks antes de ejecutar la acción |

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
  - "[IF var:puntos>100] [CONSOLE] lp user {player} permission set vip true"
  - "[IF var:estado=activo] [MESSAGE] &aTu cuenta está activa."
```

| Condición | Descripción |
|-----------|-------------|
| `permission:nodo` | El jugador **tiene** el permiso |
| `!permission:nodo` | El jugador **no tiene** el permiso |
| `world:nombre` | El jugador **está** en ese mundo |
| `!world:nombre` | El jugador **no está** en ese mundo |
| `health>N` | La vida es **mayor** que N |
| `health<N` | La vida es **menor** que N |
| `var:nombre=valor` | La variable es **igual** al valor |
| `var:nombre!=valor` | La variable es **distinta** |
| `var:nombre>N` | La variable numérica es **mayor** que N |
| `var:nombre<N` | La variable numérica es **menor** que N |
| `var:nombre>=N` | La variable es **mayor o igual** que N |
| `var:nombre<=N` | La variable es **menor o igual** que N |

---

### Azar `[CHANCE]`

Ejecuta una acción con probabilidad N% (0–100).

```yaml
actions:
  - "[CHANCE:10] [CONSOLE] give {player} diamond 1"
  - "[CHANCE:50] [MESSAGE] &a¡Tuviste suerte!"
```

---

### Retraso `[DELAY]`

Espera N ticks antes de ejecutar la acción. 20 ticks = 1 segundo.

```yaml
actions:
  - "[MESSAGE] &eProcesando..."
  - "[DELAY:60] [MESSAGE] &a¡Terminado! (3 segundos después)"
  - "[DELAY:100] [IF permission:admin] [CONSOLE] say {player} fue promovido"
```

---

### Variables `[VAR]`

Guarda, modifica o elimina variables por jugador. Se guardan en `plugins/FoliaCustomCommands/playerdata/<uuid>.yml` y persisten entre sesiones.

```yaml
actions:
  # Guardar un valor
  - "[VAR:SET] puntos = 0"
  - "[VAR:SET] estado = activo"

  # Sumar al valor actual (soporta expresiones: + - * /)
  - "[VAR:SET] puntos = {var:puntos} + 10"
  - "[VAR:SET] vidas = {var:vidas} - 1"

  # [VAR:ADD] es alias de SET con suma directa
  - "[VAR:ADD] visitas = {var:visitas} + 1"

  # Eliminar una variable
  - "[VAR:DEL] estado"

  # Mostrar el valor al jugador
  - "[MESSAGE] &7Tienes §e{var:puntos} §7puntos."
```

Las variables del jugador también están disponibles como placeholder `{var:nombre}` en cualquier acción.

---

### Input de chat `[INPUT]`

Pide al jugador que escriba un valor en el chat. El mensaje se captura como variable y **no aparece en el chat** del servidor.

```yaml
actions:
  - "[INPUT] destino;&e¿A qué mundo quieres ir?"
  - "[DELAY:1] [IF var:destino!=] [CONSOLE] mv tp {player} {var:destino}"
```

Formato: `[INPUT] <variable>;<mensaje al jugador>`

> La acción tras `[INPUT]` debe ir con `[DELAY]` para dar tiempo al jugador a escribir.

---

### Menús `[MENU]`

Abre un menú de inventario definido en `plugins/FoliaCustomCommands/menus/`.

```yaml
actions:
  - "[MENU] principal"
```

Ver la sección [Menús de inventario (GUIs)](#menús-de-inventario-guis) para la definición completa.

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
| `{var:nombre}` | Valor de una variable del jugador |

Los colores usan `&` (ej. `&a` = verde, `&c` = rojo, `&e` = amarillo, `&6` = naranja).

---

## PlaceholderAPI

Si tienes **PlaceholderAPI** instalado, sus placeholders funcionan automáticamente en todas las acciones y textos:

```yaml
actions:
  - "[MESSAGE] &7Tu dinero: &e%vault_balance%"
  - "[MESSAGE] &7Rango: &b%luckperms_prefix%"
  - "[IF var:puntos>{var:puntos}] [MESSAGE] ..."    # también combinables con vars
```

No requiere ninguna configuración extra — el plugin detecta PAPI automáticamente en runtime.

---

## Args tipados y tab-complete

Define los tipos de cada argumento para activar el tab-complete automático:

```yaml
# commands/kit.yml
description: "Darle un kit a un jugador"
permission: "miserver.kit"
arg-types:
  0: player    # el primer argumento completa con jugadores online
  1: text      # texto libre (sin sugerencias)
actions:
  - "[CONSOLE] kit give {arg0} diamante"
```

| Tipo | Tab-complete |
|------|-------------|
| `player` | Jugadores online que coincidan con lo escrito |
| `number` | Sin sugerencias (solo acepta números) |
| `text` | Sin sugerencias (texto libre) |

---

## Cooldown global

El `global-cooldown` afecta a **todos los jugadores** simultáneamente. Útil para comandos de evento o de servidor que no deberían spamearse.

```yaml
# commands/evento.yml
description: "Iniciar el evento del servidor"
permission: "miserver.evento"
global-cooldown: 300        # 5 minutos de espera para TODOS después de que alguien lo active
cooldown-message: "&cEl evento se activó hace poco. Espera &e{remaining}s &cmás."
actions:
  - "[BROADCAST] &6¡EVENTO INICIADO por {player}!"
  - "[CONSOLE] evento start"
```

> `cooldown` y `global-cooldown` son independientes y pueden usarse juntos.

---

## Menús de inventario (GUIs)

Los menús se definen en `plugins/FoliaCustomCommands/menus/`. Cada archivo `.yml` es un menú cuyo nombre es el nombre del archivo.

**`menus/principal.yml`**
```yaml
title: "&8&lPanel principal"
rows: 3                    # 1 a 6 filas (9 slots por fila)
items:

  13:                      # slot (0-based, izquierda a derecha, arriba a abajo)
    material: COMPASS
    name: "&aIr al spawn"
    lore:
      - "&7Haz clic para teleportarte"
      - "&7al punto de spawn"
    glow: true             # efecto de brillo sin enchant visible
    close-on-click: true   # cierra el inventario al clicar (default: true)
    actions:
      - "[CONSOLE] spawn {player}"
      - "[MESSAGE] &a¡Teletransportado!"

  22:
    material: BOOK
    name: "&bMi perfil"
    lore:
      - "&7Puntos: &e{var:puntos}"
    actions:
      - "[MENU] perfil"   # abrir otro menú

  26:
    material: BARRIER
    name: "&cCerrar"
    actions:
      - "[CLOSE]"         # cierra el inventario
```

### Opciones de ítem

| Opción | Por defecto | Descripción |
|--------|-------------|-------------|
| `material` | `STONE` | Nombre del material (ver [lista de materiales](https://jd.papermc.io/paper/1.21/org/bukkit/Material.html)) |
| `name` | `""` | Nombre del ítem (soporta `&colores` y `{var:x}`) |
| `lore` | `[]` | Lista de líneas de descripción |
| `glow` | `false` | Añade brillo sin mostrar enchantments |
| `close-on-click` | `true` | Cierra el menú al hacer clic |
| `custom-model-data` | `0` | Valor de CustomModelData para resource packs |
| `actions` | `[]` | Acciones a ejecutar al clicar |

### Abrir un menú desde un comando

```yaml
# commands/menu.yml
description: "Abre el panel principal"
permission: ""
actions:
  - "[MENU] principal"
```

---

## Comandos del plugin

| Comando | Permiso | Descripción |
|---------|---------|-------------|
| `/fccmds reload` | `fccmds.admin` | Recarga todos los archivos de `commands/` |
| `/enderchest` | `fccmds.enderchest` | Abre tu propio enderchest |
| `/enderchest <jugador>` | `fccmds.enderchest.others` | Abre el enderchest de otro jugador |
| `/invsee <jugador>` | `fccmds.invsee` | Ve y edita el inventario de otro jugador |

---

## Permisos

| Permiso | Por defecto | Descripción |
|---------|-------------|-------------|
| `fccmds.admin` | OP | Recargar con `/fccmds reload` |
| `fccmds.enderchest` | Todos | Abrir tu propio enderchest |
| `fccmds.enderchest.others` | OP | Ver/editar enderchest de otros |
| `fccmds.invsee` | OP | Ver inventario de jugadores online |
| `fccmds.invsee.offline` | OP | Ver/editar inventario de jugadores offline |

---

## Comando `/enderchest`

```
/enderchest                  → Abre tu propio enderchest
/enderchest <nombre>         → Abre el enderchest de otro jugador
```

| Situación | Qué hace |
|-----------|----------|
| Jugador **online** | Abre su enderchest en vivo. Los cambios se reflejan de inmediato. |
| Jugador **offline** | Lee su `playerdata/<uuid>.dat`. Al cerrar, **guarda los cambios** automáticamente. |
| Jugador offline que **se conecta** mientras tienes el chest abierto | Guardado cancelado para evitar conflictos. Se avisa al admin. |
| Jugador que **no existe / nunca jugó** | Mensaje de error. |

---

## Comando `/invsee`

Ver y editar el inventario completo (36 slots + hotbar + armadura + offhand) de otro jugador.

```
/invsee <nombre>
```

El inventario se muestra en un cofre de 54 slots organizado así:

| Slots en la vista | Contenido |
|-------------------|-----------|
| 0–26 | Inventario principal (filas 2, 3 y 4) |
| 27–35 | Hotbar (fila 1) |
| 36–39 | Armadura (botas, pantalones, peto, casco) |
| 40 | Offhand |

| Situación | Comportamiento |
|-----------|---------------|
| Jugador **online** | Vista en vivo, cambios inmediatos |
| Jugador **offline** | Lee el `.dat`, guarda al cerrar |
| Jugador offline se **conecta** mientras lo tienes abierto | Guardado cancelado, se avisa |

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

