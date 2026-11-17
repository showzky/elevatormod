# Future updates

Ideas that have been discussed and deliberately postponed. Each entry says what it would do and
why it was left for later, so it can be picked up without re-researching it.

## Power and safety

### Power check before a trip
Before a trip starts, estimate the energy it needs (travel time at the set speed × FE per tick)
and refuse to leave if the pulley holds less, showing "Not enough power for this trip".
Keep the speed the trip started with until it arrives, so raising the speed mid-trip cannot
make it run out.
*Why later:* the emergency descent already stops riders getting stuck. This only avoids trips
that start, run out and sink back to the floor below. Parked cabins already refuse to leave
with no power at all.

### Manual escape from a stuck cabin
Sneak-right-click the controller to park the cabin where it is, snapped to the nearest whole
block, so players can open the doors or break out.
*Why later:* with the emergency descent, a cabin should only stay stuck when there is no floor
below it.

## Riding

### Rider snap
After carrying a rider by the cabin's step, snap their feet exactly onto the floor's top when
they ended up slightly above or inside it, if that spot is free of world blocks. Prevents any
drift and "in the air" ticks at high speed. Moving Elevators snaps riders every tick, but
ignores world blocks, which can trap riders on the roof under an overhang.

### Wall push
Moving Elevators pushes entities back out of the cabin's walls sideways
(`ElevatorCollisionHandler.handleHorizontalCollision`). Ours relies on the cabin blocks acting
as normal walls, which has been enough so far.

## Doors and touchscreens

### Door buttons on touchscreens
Open and Close door buttons on the touchscreen, for when Auto Open is off.

### Sound settings
The Elevator Controller shows a locked "Sound Settings: Coming soon" panel.

## Camouflage

### Glass-to-glass culling
Hide the faces between neighbouring glass-camouflaged Elevator Blocks (parked and moving), so a
glass wall looks like one pane instead of a faint grid. FramedBlocks does this with NeoForge's
`hidesNeighborFace` / `supportsExternalFaceHiding`.

### Glowing camouflage and beacon tint
Light-emitting camouflage (glowstone, sea lantern) gives off light; stained-glass camouflage
tints beacon beams.

## Robustness

### Cabin damaged notice
Self-repair after loading is done (1.0.98-alpha). Still open: when a cabin is broken while the
world is running, show "Cabin damaged" on touchscreens instead of silently disconnecting.
