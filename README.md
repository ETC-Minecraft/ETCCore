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

## Licencia

MIT © [EmmanuelTC](https://github.com/EmmanuelTC) / [ETC-Minecraft](https://github.com/ETC-Minecraft)
