package com.rudues.mod.item;

import com.rudues.mod.ability.PowerData;
import com.rudues.mod.trial.TrialPortal;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Fire Fiend no longer grants the power by itself: it is the key to the Infernal Trial. Throw it into the
 * middle of a ring of 12 crying obsidian (built like an End portal frame) to open the way.
 */
public class FireFiendItem extends Item {
    public FireFiendItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable(PowerData.has(player)
                    ? "message.rudues.already_powered" : "message.rudues.fire_fiend_hint").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return InteractionResultHolder.pass(stack);
    }

    /** While thrown on the ground: if it lies inside a crying obsidian frame, it opens the portal and is consumed. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof ServerLevel level && entity.tickCount % 4 == 0 && entity.onGround()
                && TrialPortal.tryActivate(level, entity.blockPosition())) {
            ItemStack left = stack.copy();
            left.shrink(1);
            if (left.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(left);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.rudues.fire_fiend.tooltip1"));
        tooltip.add(Component.translatable("item.rudues.fire_fiend.tooltip2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.rudues.fire_fiend.tooltip3").withStyle(ChatFormatting.GRAY));
    }
}
