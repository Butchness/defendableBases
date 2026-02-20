package com.defendablebases;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FortressTooltipBlockItem extends BlockItem {
    public FortressTooltipBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Block block = getBlock();
        if (block == ModBlocks.FORTRESS_CENTER.get()) return Component.literal("Fortress Center");
        if (block == ModBlocks.FORTRESS_WOOD.get()) return Component.literal("Fortress Wood Node");
        if (block == ModBlocks.FORTRESS_IRON.get()) return Component.literal("Fortress Iron Node");
        if (block == ModBlocks.FORTRESS_DIAMOND.get()) return Component.literal("Fortress Diamond Node");
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        Block block = getBlock();

        if (block == ModBlocks.FORTRESS_CENTER.get()) {
            tooltip.add(Component.literal("Core of a fortress network. Claims territory and manages linked nodes.").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Energy: " + FortressCenterBlockEntity.CENTER_MAX_ENERGY).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.literal("Territory Radius: " + Config.fortressTerritoryRadius + " blocks").withStyle(ChatFormatting.AQUA));
            return;
        }

        FortressTier tier = tierForBlock(block);
        if (tier == null) return;

        tooltip.add(Component.literal("Provides break protection and stores repair resources.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Inventory Slots: " + tier.slotCount).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Energy: " + tier.maxHealth).withStyle(ChatFormatting.AQUA));

        TerritoryRules.FortressKind kind = TerritoryRules.getKindFromBlock(block);
        int radius = TerritoryRules.getBreakRadiusBlocksForKind(kind);
        tooltip.add(Component.literal("Break Protection Radius: " + radius + " blocks").withStyle(ChatFormatting.AQUA));
    }

    private static FortressTier tierForBlock(Block block) {
        if (block == ModBlocks.FORTRESS_WOOD.get()) return FortressTier.WOOD;
        if (block == ModBlocks.FORTRESS_IRON.get()) return FortressTier.IRON;
        if (block == ModBlocks.FORTRESS_DIAMOND.get()) return FortressTier.DIAMOND;
        return null;
    }
}
