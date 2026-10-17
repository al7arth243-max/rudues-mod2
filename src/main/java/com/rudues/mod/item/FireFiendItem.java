package com.rudues.mod.item;

import com.rudues.mod.registry.ModParticles;
import com.rudues.mod.ability.PowerData;
import com.rudues.mod.ability.HeroForm;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FireFiendItem extends Item {
    public FireFiendItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (PowerData.has(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.rudues.already_powered"), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            PowerData.grant(player);
            PowerData.applyEffects(player);
            if (player instanceof ServerPlayer sp) {
                HeroForm.sync(sp);
            }
            player.displayClientMessage(Component.translatable("message.rudues.power_gained"), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ModParticles.PURPLE_EMBER.get(), player.getX(), player.getY() + 1.0, player.getZ(),
                        80, 0.5, 0.9, 0.5, 0.05);
                sl.sendParticles(ModParticles.PURPLE_EMBER.get(), player.getX(), player.getY() + 1.0, player.getZ(),
                        60, 0.6, 1.0, 0.6, 0.1);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.rudues.fire_fiend.tooltip1"));
        tooltip.add(Component.translatable("item.rudues.fire_fiend.tooltip2"));
        tooltip.add(Component.translatable("item.rudues.fire_fiend.tooltip3"));
    }
}
