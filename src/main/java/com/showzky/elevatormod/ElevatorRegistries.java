package com.showzky.elevatormod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ElevatorRegistries {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ElevatorMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ElevatorMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElevatorMod.MOD_ID);
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ElevatorMod.MOD_ID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<ElevatorControlMenu>> OVERVIEW_MENU = MENUS.register("overview", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(ElevatorControlMenu::new));
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElevatorMod.MOD_ID);

    public static final DeferredBlock<Block> PULLEY = BLOCKS.register("powered_pulley", () -> new PoweredPulleyBlock(properties().noOcclusion()));
    public static final DeferredBlock<Block> ATTACHMENT = block("rope_attachment", true);
    public static final DeferredBlock<Block> ELEVATOR_BLOCK = BLOCKS.register("elevator_block", () -> new ElevatorBlock(properties()));
    public static final DeferredBlock<Block> CONTROLLER = facingBlock("elevator_controller");
    public static final DeferredBlock<Block> FLOOR_CONNECTOR = BLOCKS.register("floor_connector", () -> new FloorConnectorBlock(properties().noOcclusion()));
    public static final DeferredBlock<Block> CALL_BUTTON = BLOCKS.register("call_button", () -> new ElevatorCallButtonBlock(properties().noOcclusion().lightLevel(state -> state.getValue(ElevatorCallButtonBlock.POWERED) ? 5 : 0)));
    public static final DeferredBlock<Block> LIGHT = BLOCKS.register("elevator_light", () -> new ElevatorLightBlock(properties().noOcclusion().lightLevel(state -> state.getValue(ElevatorLightBlock.LIT) ? 15 : 0)));
    public static final DeferredBlock<Block> DOOR = BLOCKS.register("elevator_door", () -> new ElevatorDoorBlock(properties().noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<ElevatorTouchscreenBlock> TOUCHSCREEN_1X1 = touchscreen(TouchscreenSize.SMALL);
    public static final DeferredBlock<ElevatorTouchscreenBlock> TOUCHSCREEN_1X2 = touchscreen(TouchscreenSize.TALL);
    public static final DeferredBlock<ElevatorTouchscreenBlock> TOUCHSCREEN_2X2 = touchscreen(TouchscreenSize.LARGE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElevatorBlockEntity>> ELEVATOR_ENTITY = BLOCK_ENTITIES.register("elevator_block", () -> BlockEntityType.Builder.of(ElevatorBlockEntity::new, ELEVATOR_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PoweredPulleyBlockEntity>> PULLEY_ENTITY = BLOCK_ENTITIES.register("powered_pulley", () -> BlockEntityType.Builder.of(PoweredPulleyBlockEntity::new, PULLEY.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FloorConnectorBlockEntity>> CONNECTOR_ENTITY = BLOCK_ENTITIES.register("floor_connector", () -> BlockEntityType.Builder.of(FloorConnectorBlockEntity::new, FLOOR_CONNECTOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElevatorDoorBlockEntity>> DOOR_ENTITY = BLOCK_ENTITIES.register("elevator_door", () -> BlockEntityType.Builder.of(ElevatorDoorBlockEntity::new, DOOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElevatorTouchscreenBlockEntity>> TOUCHSCREEN_ENTITY = BLOCK_ENTITIES.register("elevator_touchscreen", () -> BlockEntityType.Builder.of(ElevatorTouchscreenBlockEntity::new, TOUCHSCREEN_1X1.get(), TOUCHSCREEN_1X2.get(), TOUCHSCREEN_2X2.get()).build(null));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.elevatormod.main")).icon(() -> PULLEY.toStack()).displayItems((parameters, output) -> {
        output.accept(PULLEY); output.accept(ATTACHMENT); output.accept(ELEVATOR_BLOCK); output.accept(CONTROLLER); output.accept(FLOOR_CONNECTOR); output.accept(CALL_BUTTON); output.accept(DOOR); output.accept(LIGHT);
        output.accept(TOUCHSCREEN_1X1); output.accept(TOUCHSCREEN_1X2); output.accept(TOUCHSCREEN_2X2);
    }).build());

    private static BlockBehaviour.Properties properties() { return BlockBehaviour.Properties.of().strength(3.5F, 6.0F).requiresCorrectToolForDrops(); }
    private static DeferredBlock<Block> block(String name, boolean noOcclusion) {
        return BLOCKS.register(name, () -> new Block(noOcclusion ? properties().noOcclusion() : properties()));
    }
    private static DeferredBlock<ElevatorTouchscreenBlock> touchscreen(TouchscreenSize size) {
        return BLOCKS.register(size.registryName(), () -> new ElevatorTouchscreenBlock(size, properties().noOcclusion()
                .pushReaction(PushReaction.BLOCK).lightLevel(state -> 3)));
    }
    private static DeferredBlock<Block> facingBlock(String name) {
        return BLOCKS.register(name, () -> new FacingElevatorBlock(properties().noOcclusion()));
    }
    private static void item(String name, DeferredBlock<? extends Block> block) {
        ITEMS.register(name, () -> switch (name) {
            case "powered_pulley", "rope_attachment", "elevator_controller",
                 "elevator_touchscreen_1x1", "elevator_touchscreen_1x2", "elevator_touchscreen_2x2" ->
                    new ElevatorComponentItem(block.get(), new Item.Properties(), "tooltip.elevatormod." + name);
            default -> new BlockItem(block.get(), new Item.Properties());
        });
    }
    public static void register(IEventBus bus) {
        item("powered_pulley", PULLEY); item("rope_attachment", ATTACHMENT); item("elevator_block", ELEVATOR_BLOCK);
        item("elevator_controller", CONTROLLER); item("floor_connector", FLOOR_CONNECTOR); item("call_button", CALL_BUTTON); item("elevator_door", DOOR); item("elevator_light", LIGHT);
        item(TouchscreenSize.SMALL.registryName(), TOUCHSCREEN_1X1); item(TouchscreenSize.TALL.registryName(), TOUCHSCREEN_1X2); item(TouchscreenSize.LARGE.registryName(), TOUCHSCREEN_2X2);
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); MENUS.register(bus); TABS.register(bus);
    }
    private ElevatorRegistries() {}
}
