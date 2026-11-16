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
