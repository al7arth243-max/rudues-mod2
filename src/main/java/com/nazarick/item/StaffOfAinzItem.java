package com.nazarick.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Staff of Ainz Ooal Gown. Crafted with seven empty gem slots; the gems appear during the throne claim.
 * While held (plus a 3 second grace), Nazarick's undead and guardians leave the bearer alone, until someone rules.
 */
public class StaffOfAinzItem extends Item {
    public static final int MAX_GEMS = 7;
    private static final String GEMS = "Gems";

    public StaffOfAinzItem(Properties properties) {
        super(properties);
    }

    public static int getGems(ItemStack stack) {
        return stack.hasTag() ? Mth.clamp(stack.getTag().getInt(GEMS), 0, MAX_GEMS) : 0;
    }

    public static void setGems(ItemStack stack, int gems) {
        stack.getOrCreateTag().putInt(GEMS, Mth.clamp(gems, 0, MAX_GEMS));
    }

    public static boolean isStaff(ItemStack stack) {
        return stack.getItem() instanceof StaffOfAinzItem;
    }

    public static boolean isHolding(Player player) {
        return isStaff(player.getMainHandItem()) || isStaff(player.getOffhandItem());
    }

    /** The staff in a hand, or anywhere in the inventory; empty stack if none. */
    public static ItemStack findStaff(Player player) {
        if (isStaff(player.getMainHandItem())) return player.getMainHandItem();
        if (isStaff(player.getOffhandItem())) return player.getOffhandItem();
        for (ItemStack stack : player.getInventory().items) {
            if (isStaff(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Hotbar, main inventory or offhand. */
    public static boolean hasStaffAnywhere(Player player) {
        return !findStaff(player).isEmpty();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getGems(stack) >= MAX_GEMS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int gems = getGems(stack);
        tooltip.add(Component.literal("Gems: " + gems + "/" + MAX_GEMS).withStyle(gems >= MAX_GEMS ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
        if (gems == 0) {
            tooltip.add(Component.literal("Its slots are empty. Bear it to the Throne of Nazarick.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
