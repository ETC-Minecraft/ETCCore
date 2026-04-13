# Ejemplos de FoliaCustomCommands

Copia los archivos de `commands/` en `plugins/FoliaCustomCommands/commands/`  
y los de `menus/` en `plugins/FoliaCustomCommands/menus/`.

---

## Índice de ejemplos

### Comandos

| Archivo | Qué demuestra | Comandos incluidos |
|---|---|---|
| `01-acciones-basicas.yml` | Todos los tipos de acción de forma individual | `saludo`, `info`, `ir-spawn`, `curar`, `anuncio`, `puntos-bar`, `bienvenida-titulo`, `titulo-largo`, `ding`, `ding-personalizado`, `kit-inicio` |
| `02-condiciones.yml` | `[IF]` con permiso, mundo, salud, hambre, gamemode y variables | `volar`, `portal-pvp`, `usar-pocion`, `escape-pvp`, `comer`, `modo-creativo`, `ver-estado`, `canjear-puntos`, `check-rango`, `check-mision` |
| `03-variables.yml` | `[VAR:SET/ADD/DEL]`, expresiones, flags, placeholders `{var:x}` | `iniciar-rango`, `mi-rango`, `contar-muertes`, `gastar-puntos`, `doblar-puntos`, `reset-datos`, `modo-sigilo`, `avanzar-mision`, `aplicar-rango-luckperms` |
| `04-cooldowns-chance-delay.yml` | `cooldown:`, `global-cooldown:`, `[CHANCE]`, `[DELAY]` | `kit-diario`, `me-accion`, `evento-global`, `votekick`, `cofre-diario`, `frase-aleatoria`, `loteria`, `countdown`, `tp-spawn`, `bienvenida-progresiva` |
| `05-input-argtypes.yml` | `[INPUT]` captura de chat, `arg-types:` y `{arg:N}` | `set-apodo`, `reportar`, `ticket`, `registro`, `ver-perfil`, `dar-puntos`, `tp-a`, `mensaje-privado` |
| `99-sistema-avanzado.yml` | Todo combinado: misiones, puntos, rangos, menús, input, cooldowns | `misiones`, `aceptar-mision`, `avance-mision`, `completar-mision`, `rankup`, `perfil`, `admin-mision`, `evento-global` |

### Menús

| Archivo | Descripción |
|---|---|
| `panel.yml` | Panel de navegación principal con botones a tienda, perfil, reglas, soporte |
| `tienda.yml` | Tienda de puntos: compra diamantes, tótem y rango VIP gastando `{var:puntos}` |
| `misiones.yml` | Selector de misiones del sistema avanzado |

---

## Flujo del sistema avanzado

```
/misiones  →  menú misiones.yml
                ├─ Click "Bosque"  →  /aceptar-mision bosque
                ├─ Click "Mina"    →  /aceptar-mision mina
                └─ Click "Mar"     →  /aceptar-mision mar

/avance-mision  (repetir N veces)
  → Al llegar al límite llama a /completar-mision automáticamente
    └─ Da 50 pts + recompensa CHANCE 25%/50%
       └─ Si completó 5+ misiones → BROADCAST

/rankup
  → Comprueba rango actual y puntos
  → Pide confirmación por [INPUT] en chat
  → Ejecuta "lp user {player} parent set rango" en consola

/perfil  →  muestra todo: rango, puntos, misiones, misión activa

/misiones  →  [MENU] panel  →  [MENU] tienda
                                 └─ compra items gastando {var:puntos}
```

---

## Qué función aparece en cada ejemplo

| Función | Dónde verla |
|---|---|
| `[MESSAGE]` | `01` línea `saludo` |
| `[CONSOLE]` | `01` línea `curar`, `03` línea `aplicar-rango-luckperms` |
| `[BROADCAST]` | `01` línea `anuncio` |
| `[TITLE]` | `01` líneas `bienvenida-titulo` y `titulo-largo` |
| `[ACTIONBAR]` | `01` línea `puntos-bar`, `04` línea `tp-spawn` |
| `[SOUND]` | `01` líneas `ding` y `ding-personalizado` |
| `[PLAYER]` | `01` línea `ir-spawn` |
| `[IF] permission:` | `02` línea `volar` |
| `[IF] world:` | `02` línea `portal-pvp` |
| `[IF] health<` | `02` línea `usar-pocion` |
| `[IF] food<` | `02` línea `comer` |
| `[IF] gamemode:` | `02` línea `modo-creativo` |
| `[IF] var:` | `02` líneas `ver-estado` → `check-mision`, `03` todo |
| `[VAR:SET]` | `03` línea `iniciar-rango` |
| `[VAR:ADD]` | `03` línea `contar-muertes` |
| `[VAR:DEL]` | `03` línea `reset-datos` |
| `{var:x}` | `03` todas las líneas |
| `cooldown:` | `04` líneas `kit-diario`, `me-accion` |
| `global-cooldown:` | `04` línea `evento-global` |
| `[CHANCE]` | `04` líneas `cofre-diario`, `frase-aleatoria` |
| `[DELAY]` | `04` líneas `countdown`, `tp-spawn` |
| `[INPUT]` | `05` y `99` línea `rankup` |
| `arg-types:` | `05` y `99` línea `aceptar-mision` |
| `{arg:N}` | `05` y `99` |
| `[MENU]` | menús `panel.yml`, `tienda.yml`, `misiones.yml` |
| `[CLOSE]` | `panel.yml` slot 22, `tienda.yml` |
| `custom-model-data:` | ver README principal del plugin |
| `glow:` | `panel.yml` slot 3, `tienda.yml` productos |
