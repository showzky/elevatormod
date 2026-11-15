package com.showzky.elevatormod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ElevatorRegistries {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ElevatorMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ElevatorMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElevatorMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElevatorMod.MOD_ID);

    public static final DeferredBlock<Block> PULLEY = BLOCKS.register("powered_pulley", () -> new PoweredPulleyBlock(properties().noOcclusion()));
    public static final DeferredBlock<Block> ATTACHMENT = block("rope_attachment", true);
    public static final DeferredBlock<Block> ELEVATOR_BLOCK = block("elevator_block", false);
    public static final DeferredBlock<Block> CONTROLLER = facingBlock("elevator_controller");
    public static final DeferredBlock<Block> FLOOR_CONNECTOR = facingBlock("floor_connector");
    public static final DeferredBlock<Block> CALL_BUTTON = facingBlock("call_button");
    public static final DeferredBlock<Block> DOOR = facingBlock("elevator_door");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PoweredPulleyBlockEntity>> PULLEY_ENTITY = BLOCK_ENTITIES.register("powered_pulley", () -> BlockEntityType.Builder.of(PoweredPulleyBlockEntity::new, PULLEY.get()).build(null));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.elevatormod.main")).icon(() -> PULLEY.toStack()).displayItems((parameters, output) -> {
        output.accept(PULLEY); output.accept(ATTACHMENT); output.accept(ELEVATOR_BLOCK); output.accept(CONTROLLER); output.accept(FLOOR_CONNECTOR); output.accept(CALL_BUTTON); output.accept(DOOR);
    }).build());

    private static BlockBehaviour.Properties properties() { return BlockBehaviour.Properties.of().strength(3.5F, 6.0F).requiresCorrectToolForDrops(); }
    private static DeferredBlock<Block> block(String name, boolean noOcclusion) {
        return BLOCKS.register(name, () -> new Block(noOcclusion ? properties().noOcclusion() : properties()));
    }
    private static DeferredBlock<Block> facingBlock(String name) {
        return BLOCKS.register(name, () -> new FacingElevatorBlock(properties().noOcclusion()));
    }
    private static void item(String name, DeferredBlock<Block> block) {
        ITEMS.register(name, () -> switch (name) {
            case "powered_pulley", "rope_attachment", "elevator_controller" ->
                    new ElevatorComponentItem(block.get(), new Item.Properties(), "tooltip.elevatormod." + name);
            default -> new BlockItem(block.get(), new Item.Properties());
        });
    }
    public static void register(IEventBus bus) {
        item("powered_pulley", PULLEY); item("rope_attachment", ATTACHMENT); item("elevator_block", ELEVATOR_BLOCK);
        item("elevator_controller", CONTROLLER); item("floor_connector", FLOOR_CONNECTOR); item("call_button", CALL_BUTTON); item("elevator_door", DOOR);
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); TABS.register(bus);
    }
    private ElevatorRegistries() {}
}
