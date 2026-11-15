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


## 1.0.24-alpha physical movement test build

Implemented a pulley-owned moving cabin with floating-point vertical position, acceleration/deceleration, FE pause/resume, destination requests from discovered facing connectors, block-state and block-entity serialization, explicit restoration and a Minecraft collision hook. Source blocks transfer once at assembly; they are not repeatedly replaced during travel. The pulley renderer draws the cabin, moving rope and rotating drum. No external mod code or assets were copied in this change.

Replaced dummy chest menus with ElevatorControlMenu (no inventory slots), ElevatorControlScreen, custom control packets and original small pixel-art backgrounds. Pulley controls assemble/disassemble and speed; connector names save in FloorConnectorBlockEntity; controller buttons request actual destinations. The moving controller is located by server-side ray validation on empty-hand interaction. V2 door assets remain unchanged.

Verification: Gradle build succeeded. A NeoForge GameTest ran in the dedicated-server runtime and passed: cabin assembly, removal of source blocks, upward travel, carriage of one armor stand, power interruption holding position, power restoration, mid-trip NBT round trip, downward travel and restoration of platform/attachment. Test source: ElevatorMovementTests.java. Final log: build/movement-test-final.log. This is NOT a multiplayer player test, client rendering test or an actual server restart/crash-recovery test. Existing deprecation warnings remain.

Testing: install build/libs/elevatormod-1.0.24-alpha.jar in Minecraft 1.21.1 with NeoForge 21.1.265. Use a disposable test world. Construct a connected cabin with one controller, an attachment and at least one Elevator Block. Close cabin doors. Install a cabin connector pointing outward and stationary connectors facing it in the immediately adjacent column. Right-click stationary connectors to name floors. Right-click the pulley to assemble; supply FE. Shift-right-click pulley for configuration. Empty-hand right-click the rendered cabin controller to choose a floor. Stop at a floor before disassembly.

Limits requiring follow-up: human-player network movement and client visual smoothness are unverified; collision handling has not been validated for ceilings, complex passengers, intersecting shafts or large cabins. Cabin doors are captured closed and are not interactable/automatic while assembled. Pulley destruction by explosions, creative breaking or commands can lose the stored cabin; normal survival mining is blocked while assembled. There is no crash-safe ownership journal: assembly/restoration spanning chunk saves may duplicate or lose blocks if interrupted. Do not use in a valuable world. FE use is currently provisional at 20 FE/moving tick; speed range is 0.5-4 m/s. Naming GUI, moving controller ray selection and rendering still need client verification. Pulley owner chunk must remain loaded. Menu slider initial display is not yet restored from saved speed. No claim of full request completion.

## 1.0.71-alpha touchscreens and animated doors

Touchscreens (1x1, 1x2, 2x2): one block class (ElevatorTouchscreenBlock), block entity, renderer (TouchscreenRenderer + TouchscreenDisplayPainter), layout (TouchscreenLayout) and network path for all sizes. Wall-mounted, placed from the bottom-left cell; larger sizes are real multiblocks routed to the master cell; breaking any cell removes the display and drops one item. Live content (current floor, destination, direction, status, floor names, floor buttons, call button, connection state, request feedback) is drawn with native world rendering in layers in front of the glass. Modes: Destination, Call, Information; shift-right-click opens TouchscreenSettingsScreen. Linking reuses the Call Button rules, now extracted to ElevatorLandingLocator (call button behaviour unchanged); screens use each pulley's ElevatorFloorDirectory, refreshed once per second instead of scanning the world height every tick. Touches are recomputed on the server from the player's eye and view; the control and destination come from the server's own layout and floor list. Screens mounted on a cabin travel with it (ElevatorAssembly adds them as complete leaf structures), keep settings in part data, render at the moving position and accept touches through TouchscreenCabinInteraction / TouchscreenTouchPayload.

Doors: the supplied Elevator_Doors_Fresh model and texture replace the 36 per-frame models. ElevatorDoorRenderer draws the two full-height panels once per doorway from the master cell, following the supplied keyframes (ElevatorDoorMotion); the server frame steps follow the same 15-tick clip. Selection shape is now always the closed panel plane; collision follows the panels and is empty when fully open. Doors placed by older builds get their block entity created client-side on the next display tick.

Verification: Gradle build passed. GameTest server (dedicated-server runtime): all 16 tests passed (6 existing, 10 new in ElevatorTouchscreenTests / ElevatorDoorTests), log build/touchscreen-door-test.log. The development client started and loaded all models and textures without errors (build/touchscreen-client.log). Not verified in-game by a player: visual appearance, text readability, door animation smoothness, client-side touch targeting on moving cabins, multiplayer with real clients.
