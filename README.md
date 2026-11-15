# Elevator Mod

A NeoForge mod for Minecraft 1.21.1. The project uses Java 21 and the Gradle wrapper.

## Development

Open this repository in an IDE with Java 21 support, or open it in a GitHub Codespace. Import the Gradle project and allow it to download its dependencies.

- Build: `./gradlew build` (Windows: `gradlew.bat build`)
- Launch the development client: `./gradlew runClient`
- Launch the development server: `./gradlew runServer`
- Generate resources: `./gradlew runData`

On this Windows machine, Java 21 is available in the Gradle toolchain cache but is not on PATH. In PowerShell, set it for your current terminal before running Gradle:

```powershell
$env:JAVA_HOME = Join-Path $env:USERPROFILE '.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Built mod jars are written to `build/libs/`. The mod ID is `elevatormod`.

The initial build configuration is based on the official [NeoForge 1.21.1 ModDevGradle MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle); its license is in `TEMPLATE_LICENSE.txt`.


## Phase 1 assembly

Place a Powered Pulley above a clear vertical shaft, with a Rope Attachment below it. Connect the attachment face-to-face to a structure made from elevator blocks, exactly one controller, and optionally floor connectors or elevator doors. Right-click the pulley with an empty hand to assemble. Ordinary blocks and call buttons are not included in the cabin scan. The scan requires adjacent chunks to be loaded and is limited to 4,096 connected components for server safety. If a connected structure becomes invalid, the pulley disconnects after its next validation.

The pulley accepts FE from any side. Its 100,000 FE storage capacity and 10,000 FE/t input limit are provisional; Phase 1 does not consume energy. Movement, speed control, floor selection, calls, camouflage, and animations are not implemented yet. The rope is rendered visually and does not create blocks.
