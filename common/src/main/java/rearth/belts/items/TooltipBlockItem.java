package rearth.belts.items;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

public class TooltipBlockItem extends BlockItem {
    
    public TooltipBlockItem(Block block, Properties settings) {
        super(block, settings);
    }
    
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag type) {
        
        var showExtra = Minecraft.getInstance().hasControlDown();
        if (showExtra) {
            var langKey = stack.getItem().getDescriptionId();
            tooltip.accept(Component.translatable(langKey + ".tooltip").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(Component.translatable("message.belts.show_extra").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        
        super.appendHoverText(stack, context, display, tooltip, type);
    }
}
