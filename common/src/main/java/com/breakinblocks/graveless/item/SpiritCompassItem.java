package com.breakinblocks.graveless.item;

import com.breakinblocks.graveless.event.GraveMenuHandlers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class SpiritCompassItem extends Item {
    public SpiritCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            GraveMenuHandlers.openFor(serverPlayer);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(itemStack, context, tooltip, tooltipFlag);
        tooltip.add(Component.translatable("item.graveless.spirit_compass.tooltip"));
    }
}
