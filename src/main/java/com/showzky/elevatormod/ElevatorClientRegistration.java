package com.showzky.elevatormod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ElevatorMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ElevatorClientRegistration {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ElevatorRegistries.PULLEY_ENTITY.get(), ElevatorRopeRenderer::new);
        event.registerBlockEntityRenderer(ElevatorRegistries.DOOR_ENTITY.get(), ElevatorDoorRenderer::new);
        event.registerBlockEntityRenderer(ElevatorRegistries.TOUCHSCREEN_ENTITY.get(), TouchscreenRenderer::new);
    }
    @SubscribeEvent
    public static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        // The Elevator Controller (kind 2) has its own settings screen; the pulley and connector share one.
        event.<ElevatorControlMenu, net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<ElevatorControlMenu>>register(
                ElevatorRegistries.OVERVIEW_MENU.get(),
                (menu, inventory, title) -> menu.kind == 2 ? new ElevatorControllerScreen(menu, inventory, title) : new ElevatorControlScreen(menu, inventory, title));
    }
    @SubscribeEvent
    public static void registerModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(ElevatorRopeRenderer.DRUM);
        event.register(ElevatorDoorRenderer.LEFT_PANEL);
        event.register(ElevatorDoorRenderer.RIGHT_PANEL);
    }
    @SubscribeEvent
    public static void bakeCamouflage(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
        var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID,"elevator_block");
        event.getModels().replaceAll((key,model) -> key.id().equals(id) && !key.getVariant().equals("inventory") ? new CamouflageBakedModel(model) : model);
    }
    @SubscribeEvent
    public static void colorCamouflage(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state,level,pos,tint) -> {
            if(level!=null && pos!=null && level.getBlockEntity(pos) instanceof ElevatorBlockEntity be && be.camouflage()!=null)
                return event.getBlockColors().getColor(be.camouflage(),level,pos,tint);
            return -1;
        },ElevatorRegistries.ELEVATOR_BLOCK.get());
    }
    @SubscribeEvent
    public static void registerCamouflageEffects(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerBlock(new net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions() {
            @Override public boolean addDestroyEffects(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.client.particle.ParticleEngine manager) {
                if (level.getBlockEntity(pos) instanceof ElevatorBlockEntity be && be.camouflage()!=null) { manager.destroy(pos,be.camouflage()); return true; }
                return false;
            }
            @Override public boolean addHitEffects(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level, net.minecraft.world.phys.HitResult hit, net.minecraft.client.particle.ParticleEngine manager) {
                if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) || !(level instanceof net.minecraft.client.multiplayer.ClientLevel client)) return false;
                var pos=blockHit.getBlockPos();
                if (!(level.getBlockEntity(pos) instanceof ElevatorBlockEntity be) || be.camouflage()==null) return false;
                var point=hit.getLocation().add(net.minecraft.world.phys.Vec3.atLowerCornerOf(blockHit.getDirection().getNormal()).scale(0.1));
                manager.add(new net.minecraft.client.particle.TerrainParticle(client,point.x,point.y,point.z,0,0,0,be.camouflage(),pos).setPower(0.2F).scale(0.6F));
                return true;
            }
        },ElevatorRegistries.ELEVATOR_BLOCK.get());
    }
    private ElevatorClientRegistration() {}
}
