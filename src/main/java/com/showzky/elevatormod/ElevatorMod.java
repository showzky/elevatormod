package com.showzky.elevatormod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@Mod(ElevatorMod.MOD_ID)
public class ElevatorMod {
    public static final String MOD_ID = "elevatormod";

    public ElevatorMod(IEventBus bus) {
        ElevatorRegistries.register(bus);
        ElevatorSoundEvents.register(bus);
        bus.addListener(ElevatorControlPayload::register);
        bus.addListener(TouchscreenNetwork::register);
        bus.addListener(this::registerCapabilities);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                ElevatorRegistries.PULLEY_ENTITY.get(), (pulley, side) -> pulley.energy());
    }
}
