# ETCCore

Plugin nativo para **Folia / Paper** que permite crear comandos personalizados desde archivos YAML, menús GUI, variables por jugador, protección de construcción, sistema de mute y más — sin necesidad de programar.

> **Compatible con**: Paper / Folia 1.21.1+  
> **Java**: 21+

📖 **Documentación completa:** [etc-minecraft.github.io/ETCCore-Docs](https://etc-minecraft.github.io/ETCCore-Docs/)

---

## Instalación

1. Descarga o compila el `.jar` (ver [Compilar](#compilar))
2. Colócalo en la carpeta `plugins/` de tu servidor
3. Inicia el servidor — se generarán automáticamente las carpetas `commands/`, `menus/` y los archivos de configuración con ejemplos

---

## Compilar

Requisitos: Java 21+, Maven 3.8+

```bash
git clone https://github.com/ETC-Minecraft/ETCCore.git
cd ETCCore
mvn package
```

El `.jar` compilado estará en `target/ETCCore-1.0.0.jar`.

---

## Permisos

ETCCore usa varios tipos de permisos. Algunos son fijos y vienen declarados en `plugin.yml`; otros se generan dinámicamente a partir de tus comandos YAML y menús.

### 1. Permisos fijos del plugin

Estos son los nodos principales incluidos por defecto:

| Permiso | Uso |
|---|---|
| `etccore.admin` | Administrar ETCCore, recargar y recibir avisos del update checker |
| `etccore.staff` | Marca al jugador como staff para placeholders |
| `etccore.vanish` | Usar `/vanish` y ver vanished en TAB |
| `etccore.enderchest` | Abrir tu propio enderchest |
| `etccore.enderchest.others` | Ver el enderchest de otros jugadores |
| `etccore.invsee` | Ver inventarios de jugadores online |
| `etccore.invsee.offline` | Ver inventarios offline |
| `etccore.build` | Permite colocar y romper bloques |
| `etccore.build.bypass` | Ignora protección de construcción e ítems |
| `etccore.items` | Permite tirar y recoger ítems |
| `etccore.chat` | Permite hablar en el chat |
| `etccore.mute` | Permite usar `/mute` y `/unmute` |
| `etccore.cmdblock.bypass` | Ignora `blocked-commands.yml` |

### 2. Permisos dinámicos de comandos YAML

Cada comando cargado desde `plugins/ETCCore/commands/*.yml` registra automáticamente:

```text
etccore.commands.<nombre>
```

Ejemplos:

```text
etccore.commands.kit
etccore.commands.spawn
etccore.commands.tierra
```

Estos permisos se crean con valor por defecto `true`, así que todos los jugadores los tienen salvo que los niegues explícitamente en LuckPerms.

Ejemplo:

```text
/lp group default permission set etccore.commands.kit false
```

### 3. Permisos dinámicos de menús

Cada menú cargado desde `plugins/ETCCore/menus/*.yml` registra automáticamente:

```text
etccore.menus.<nombre>
```

Ejemplos:

```text
etccore.menus.panel
etccore.menus.tienda
etccore.menus.misiones
```

Igual que los comandos dinámicos, nacen con valor por defecto `true` salvo negación explícita.

### 4. Permisos por comando bloqueado

El sistema de `blocked-commands.yml` usa nodos del tipo:

```text
etccore.allow.<comando>
```

Ejemplos:

```text
etccore.allow.lobby
etccore.allow.spawn
etccore.allow.home
etccore.allow.sethome
etccore.allow.res
```

Sirven para permitir comandos concretos cuando el sistema de bloqueo está activo.

### 5. Permisos personalizados en comandos YAML

Cada comando YAML puede tener su propio permiso libre con la clave:

```yml
permission: mi.permiso.personalizado
```

Ejemplo:

```yml
name: tierra
permission: etccore.rank.terricola
no-permission-message: "&cNo tienes permiso para usar este comando."
```

En ese caso, ETCCore revisa exactamente ese nodo y no te obliga a usar un prefijo fijo.

### 6. Permisos como filtro en join-rules

Las reglas de entrada soportan filtros por permiso, además de grupos:

```yml
join-rules:
	sin-fly:
		enabled: true
		only-permissions: ["grupo.default"]
		excluded-permissions: ["etccore.admin"]
		always-actions:
			- "[FLY:OFF]"
		first-join-actions: []
```

Esto no crea permisos nuevos; solo usa nodos que ya existan en LuckPerms o en otros plugins.

### 7. Ejemplos rápidos en LuckPerms

Dar construcción e ítems a un grupo:

```text
/lp group terricola permission set etccore.build true
/lp group terricola permission set etccore.items true
```

Dar acceso a un comando bloqueado:

```text
/lp group terricola permission set etccore.allow.lobby true
```

Negar un comando YAML dinámico a default:

```text
/lp group default permission set etccore.commands.tierra false
```

Permitir abrir un menú solo a VIP:

```text
/lp group vip permission set etccore.menus.tienda true
```

### Resumen rápido

Las familias de permisos de ETCCore son:

```text
etccore.admin
etccore.staff
etccore.vanish
etccore.enderchest
etccore.enderchest.others
etccore.invsee
etccore.invsee.offline
etccore.build
etccore.build.bypass
etccore.items
etccore.chat
etccore.mute
etccore.cmdblock.bypass
etccore.commands.<nombre>
etccore.menus.<nombre>
etccore.allow.<comando>
<permiso-personalizado-definido-en-yaml>
```

---

## Licencia

MIT © [EmmanuelTC](https://github.com/EmmanuelTC) / [ETC-Minecraft](https://github.com/ETC-Minecraft)
