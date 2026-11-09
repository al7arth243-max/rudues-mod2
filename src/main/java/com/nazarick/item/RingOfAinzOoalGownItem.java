package com.nazarick.item;

import com.nazarick.world.NazarickLocationData;
import com.nazarick.world.NazarickRules;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Ring of Ainz Ooal Gown: given at the claim. Right-click to choose where in Nazarick to travel. */
public class RingOfAinzOoalGownItem extends Item {
    public RingOfAinzOoalGownItem(Properties properties) {
        super(properties);
    }

    public static boolean hasRing(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof RingOfAinzOoalGownItem) return true;
        }
        return player.getOffhandItem().getItem() instanceof RingOfAinzOoalGownItem;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (!NazarickRules.isRulerOrTeammate(serverPlayer)) {
            player.displayClientMessage(Component.literal("The ring does not answer you.").withStyle(ChatFormatting.DARK_PURPLE), true);
            return InteractionResultHolder.fail(stack);
        }
        NazarickLocationData data = NazarickLocationData.get(serverPlayer.serverLevel());
        MutableComponent menu = Component.literal("Travel within Nazarick: ").withStyle(ChatFormatting.GOLD);
        boolean any = false;
        if (data.getThroneOrigin() != null) { menu.append(option("Throne Room", "throne_room")).append(" "); any = true; }
        if (data.getQuartersOrigin() != null) { menu.append(option("King's Quarters", "kings_quarters")).append(" "); any = true; }
        if (data.getEntrance() != null) { menu.append(option("Surface", "surface")); any = true; }
        if (!any) {
            player.displayClientMessage(Component.literal("There is no Nazarick in this world to travel to.").withStyle(ChatFormatting.DARK_PURPLE), true);
            return InteractionResultHolder.fail(stack);
        }
        serverPlayer.sendSystemMessage(menu);
        return InteractionResultHolder.success(stack);
    }

    private static Component option(String label, String destination) {
        return Component.literal("[" + label + "]").withStyle(style -> style
                .withColor(ChatFormatting.LIGHT_PURPLE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/nazarick ring " + destination))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport to the " + label))));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("The guild ring. Right-click to travel within Nazarick.").withStyle(ChatFormatting.GRAY));
    }
}
