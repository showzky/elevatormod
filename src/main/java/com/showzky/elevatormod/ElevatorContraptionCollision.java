package com.showzky.elevatormod;

import com.showzky.elevatormod.createcollision.CollisionList;
import com.showzky.elevatormod.createcollision.ContinuousOBBCollider;
import com.showzky.elevatormod.createcollision.OrientedBB;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Collision pass adapted from Create's ContraptionCollider.collideEntities and
 * ContraptionHandler.tick. The cabin only translates vertically, so world and
 * local axes coincide. See META-INF/CREATE_LICENSE.txt for the MIT license.
 */
public final class ElevatorContraptionCollision {
    public static void resolve(ElevatorCabinMotion cabin, Level level, double previousY, double currentY) {
        List<VoxelShape> shapes = cabin.shapes(currentY);
        CollisionList boxes = new CollisionList();
        CollisionList.Populate populate = new CollisionList.Populate(boxes);
        Set<Entity> candidates = new HashSet<>();
        for (VoxelShape shape : shapes) {
            if (shape.isEmpty()) continue;
            shape.forAllBoxes(populate);
            AABB region = shape.bounds().expandTowards(0, previousY - currentY, 0).inflate(0.25);
            candidates.addAll(level.getEntities((Entity) null, region, e -> e.isAlive() && !e.isSpectator()));
        }
        double motionY = currentY - previousY;
        CollisionList dense = new CollisionList();
        for (Entity entity : candidates) {
            // Only the controlling side may move a player: the server never does, and a client
            // only moves its own player.
            if (entity instanceof Player player && (!level.isClientSide || !player.isLocalPlayer())) continue;
            Vec3 relativeMotion = entity.getDeltaMovement().subtract(0, motionY, 0);
            var result = ContinuousOBBCollider.collideMany(boxes, dense,
                    new OrientedBB(entity.getBoundingBox().inflate(1.0E-7)), relativeMotion,
                    entity.maxUpStep(), true);
            Vec3 response = result.collisionResponse;
            if (!response.equals(Vec3.ZERO)) {
                AABB destination = entity.getBoundingBox().move(response).deflate(1.0E-7);
                if (level.noBlockCollision(entity, destination)) {
                    Vec3 position = entity.position();
                    entity.setPos(position.x + response.x, position.y + response.y, position.z + response.z);
                    Vec3 velocity = entity.getDeltaMovement();
                    if (response.x != 0 && velocity.x * response.x < 0) velocity = velocity.multiply(0, 1, 1);
                    if (response.y != 0 && velocity.y * response.y < 0) velocity = velocity.multiply(1, 0, 1).add(0, motionY, 0);
                    if (response.z != 0 && velocity.z * response.z < 0) velocity = velocity.multiply(1, 1, 0);
                    entity.setDeltaMovement(velocity);
                    entity.hurtMarked = true;
                }
            }
            if (result.surfaceCollision && result.normal.y > 0) {
                entity.fallDistance = 0;
                entity.setOnGround(true);
            }
        }
    }
    private ElevatorContraptionCollision() {}
}
