package com.showzky.elevatormod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Sound events shared by the server and client. */
public final class ElevatorSoundEvents {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ElevatorMod.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> ARRIVAL = register("elevator_arrival");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOVING = register("elevator_moving");
    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID, name)));
    }
    public static void register(IEventBus bus) { SOUNDS.register(bus); }
    private ElevatorSoundEvents() {}
}
