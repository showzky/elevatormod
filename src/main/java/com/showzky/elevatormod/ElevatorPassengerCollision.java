package com.showzky.elevatormod;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Vertical contact-point transport adapted from Create's ContraptionCollider.collideEntities.
 * Copyright (c) The Create Team / The Creators of Create, MIT; see THIRD_PARTY_CREATE_LICENSE.txt.
 */
public final class ElevatorPassengerCollision {
    public static void carry(Entity entity, double verticalMotion) {
        // Create resolves contact-point motion through world collision before applying it.
        Vec3 contactPointMotion = new Vec3(0, verticalMotion, 0);
        Vec3 allowedMovement = Entity.collideBoundingBox(entity, contactPointMotion,
                entity.getBoundingBox(), entity.level(), List.of());
        Vec3 entityPosition = entity.position();
        entity.setPos(entityPosition.x + allowedMovement.x, entityPosition.y + allowedMovement.y,
                entityPosition.z + allowedMovement.z);
        entity.setOnGround(true);
        entity.fallDistance = 0;
        entity.hurtMarked = true;
    }
    private ElevatorPassengerCollision() {}
}
