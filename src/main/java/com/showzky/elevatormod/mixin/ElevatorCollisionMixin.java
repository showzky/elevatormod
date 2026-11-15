package com.showzky.elevatormod.mixin;
import com.showzky.elevatormod.ElevatorCabinMotion;
import java.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Entity.class)
public abstract class ElevatorCollisionMixin {
 @Inject(method="collectColliders",at=@At("RETURN"),cancellable=true)
 private static void elevatorColliders(Entity e,Level level,List<VoxelShape> collisions,AABB box,CallbackInfoReturnable<List<VoxelShape>> cir){
  var added=ElevatorCabinMotion.collisions(level,box);if(added.isEmpty())return;
  var result=new ArrayList<>(cir.getReturnValue());result.addAll(added);cir.setReturnValue(result);
 }
}
