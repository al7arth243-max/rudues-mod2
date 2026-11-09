package com.nazarick.entity;

import com.nazarick.item.StaffOfAinzItem;
import com.nazarick.registry.ModItems;
import com.nazarick.world.NazarickRules;
import java.util.List;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Sebas Tian, head butler. Hands behind his back; bows with a hand on his chest.
 * The ruler can sneak + right-click him to request a replacement Staff of Ainz Ooal Gown.
 */
public class SebasEntity extends NazarickNpcEntity {
    private static final List<String> LINES = List.of(
            "Welcome back. Your quarters have been prepared.",
            "The maids have completed the day's duties without fault.",
            "If you have need of me, I am at your service.",
            "A butler's work is never finished. Is there something I may do for you?",
            "I shall keep watch while you rest.");

    public SebasEntity(EntityType<? extends SebasEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected List<String> greetingLines() {
        return LINES;
    }

    @Override
    protected SoundEvent greetSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && NazarickRules.isRuler(player)) {
            if (!this.level().isClientSide) {
                if (StaffOfAinzItem.hasStaffAnywhere(player)) {
                    this.say(player, "You already have a staff, my lord.");
                } else {
                    ItemStack staff = new ItemStack(ModItems.STAFF_OF_AINZ_OOAL_GOWN.get());
                    StaffOfAinzItem.setGems(staff, StaffOfAinzItem.MAX_GEMS);
                    if (!player.getInventory().add(staff)) player.drop(staff, false);
                    this.bow(player);
                    this.say(player, "Allow me. A replacement Staff of Ainz Ooal Gown, my lord.");
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }
}
