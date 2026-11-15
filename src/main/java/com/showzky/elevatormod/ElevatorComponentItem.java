package com.showzky.elevatormod;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** Shows assembly guidance on the components needed to connect a cabin. */
public class ElevatorComponentItem extends BlockItem {
    private final String instructionKey;

    public ElevatorComponentItem(Block block, Item.Properties properties, String instructionKey) {
        super(block, properties);
        this.instructionKey = instructionKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(instructionKey).withStyle(ChatFormatting.GRAY));
    }
}
