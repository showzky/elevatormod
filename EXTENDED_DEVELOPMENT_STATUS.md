# Extended development status

This is an incomplete foundation update, not the requested complete extended phase.

## Changes in 1.0.6-alpha

- ElevatorAssembly.java: successful scans return an immutable set of cabin block positions. Scanning does not cross the contact face of a Floor Connector in either direction.
- ElevatorStructureSnapshot.java (new): captures relative positions, full block states including orientation, and block entity NBT without removing world blocks.
- PoweredPulleyBlock.java: passes the complete scan result to the pulley.
- PoweredPulleyBlockEntity.java: saves the captured snapshot on connection; clears it on invalidation. Full snapshots are kept server-side rather than sent in routine rendering updates.
- gradle.properties: version incremented to 1.0.6-alpha.

Models, textures, recipes and registrations were not changed.

## Verification

Read existing Java implementation, Gradle configuration, metadata and README; parsed all 43 resource JSON files. Gradle build succeeded with Java 21. No automated gameplay tests exist. No in-world, restart, dedicated-server or multiplayer test of these changes was performed. Existing deprecation warnings remain.

## Remaining work

Physical movement, moving collision, passenger transport, network interpolation, transactional world-block removal/restoration and crash recovery are not implemented. The snapshot is preparatory storage only; it is not a recovery transaction or a guarantee against duplication. Existing connected pulleys receive a snapshot only when newly connected. Configurable limits and energy, pulley GUI, floor discovery and naming, moving controller interaction, call queues, sliding doors, camouflage and pulley animation remain unimplemented.

## Operating this build

Install build/libs/elevatormod-1.0.6-alpha.jar in a Minecraft 1.21.1 NeoForge instance, replacing the previous elevator mod jar. Build a face-connected cabin with Elevator Blocks, one controller and one Rope Attachment on top. Place the pulley directly above the attachment with a clear air path. Right-click the pulley empty-handed. Connector contact faces are excluded from structural joins; attach cabin connectors through their back or sides. Successful assembly connects the rope; it does not move the cabin. Assembly does not consume FE.

For a local build, set JAVA_HOME to a Java 21 JDK and run gradlew.bat build (or ./gradlew build on Linux). Increment mod_version before a new build per AGENTS.md.

## 1.0.7-alpha continuation

Added ElevatorFloorDiscovery.java and connected its reports to PoweredPulleyBlockEntity.reportFloors and pulley interaction. Added translated floor scan messages; incremented gradle.properties.

Right-click an assembled pulley with an empty hand to scan again. Install a cabin Floor Connector with its contact face pointing outward. Put stationary connectors in the immediately adjacent block column, facing the cabin connector, at different heights. Reports list matching connector heights and calculated attachment stopping heights. Repeating the scan picks up added or removed connectors. Incorrect-facing and different-column connectors are excluded. Destinations above the pulley or outside world height are excluded. Unloaded columns are reported rather than force-loaded. Multiple matches at the same stopping height are deduplicated.

This is only discovery: it does not check the whole travel path for collision, associate a shaft exclusively with one elevator, store custom floor names, select destinations or move the cabin. The requested extended phase remains incomplete.

Validation: Gradle build passed using Java 21; no gameplay, multiplayer, reload or automated discovery tests performed. Test jar: build/libs/elevatormod-1.0.7-alpha.jar.
