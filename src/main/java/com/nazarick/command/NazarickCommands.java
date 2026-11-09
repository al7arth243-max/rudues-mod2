package com.nazarick.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nazarick.NazarickMod;
import com.nazarick.ceremony.ThroneCeremony;
import com.nazarick.item.RingOfAinzOoalGownItem;
import com.nazarick.registry.ModItems;
import com.nazarick.world.NazarickLocationData;
import com.nazarick.world.NazarickRules;
import com.nazarick.world.RulerData;
import com.nazarick.world.TombLayout;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * /nazarick place great_tomb|throne_room|kings_quarters   (op)  build Nazarick at your feet
 * /nazarick ruler info|reset|transfer <player>            (op)  manage the single ruler of this world
 * /nazarick claim confirm|cancel                                 answer the throne's prompt (clickable in chat)
 * /nazarick ring throne_room|kings_quarters|surface              travel with the Ring of Ainz Ooal Gown
 */
public final class NazarickCommands {
    private NazarickCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nazarick")
                .then(Commands.literal("place").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("great_tomb").executes(ctx -> placeTomb(ctx.getSource())))
                        .then(Commands.literal("throne_room").executes(ctx -> placeRoom(ctx.getSource(), "throne_room")))
                        .then(Commands.literal("kings_quarters").executes(ctx -> placeRoom(ctx.getSource(), "kings_quarters"))))
                .then(Commands.literal("ruler").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("info").executes(ctx -> rulerInfo(ctx.getSource())))
                        .then(Commands.literal("reset").executes(ctx -> rulerReset(ctx.getSource())))
                        .then(Commands.literal("transfer")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> rulerTransfer(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("claim")
                        .then(Commands.literal("confirm").executes(ctx -> {
                            ThroneCeremony.confirm(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("cancel").executes(ctx -> {
                            ThroneCeremony.cancelPending(ctx.getSource().getPlayerOrException());
                            return 1;
                        })))
                .then(Commands.literal("ring")
                        .then(Commands.literal("throne_room").executes(ctx -> ring(ctx.getSource(), "throne_room")))
                        .then(Commands.literal("kings_quarters").executes(ctx -> ring(ctx.getSource(), "kings_quarters")))
                        .then(Commands.literal("surface").executes(ctx -> ring(ctx.getSource(), "surface")))));
    }

    // ------------------------------------------------------------------ place
    private static int placeTomb(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos s = BlockPos.containing(source.getPosition());
        if (s.getY() - TombLayout.DEPTH < level.getMinBuildHeight() + 2) {
            source.sendFailure(Component.literal("Too low: the tomb goes " + TombLayout.DEPTH + " blocks down. Stand higher."));
            return 0;
        }
        TombLayout.placeAll(level, s);
        source.sendSuccess(() -> Component.literal("The Great Tomb of Nazarick rises at " + s.toShortString()
                + ". The mausoleum is centred on your position."), true);
        return 1;
    }

    private static int placeRoom(CommandSourceStack source, String room) {
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        Optional<StructureTemplate> template = level.getStructureManager().get(new ResourceLocation(NazarickMod.MODID, room));
        if (template.isEmpty()) {
            source.sendFailure(Component.literal("Missing structure template nazarick:" + room));
            return 0;
        }
        template.get().placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_CLIENTS);
        TombLayout.record(level, NazarickMod.MODID + ":" + room, origin);
        source.sendSuccess(() -> Component.literal("Placed nazarick:" + room + " at " + origin.toShortString()), true);
        return 1;
    }

    // ------------------------------------------------------------------ ruler
    private static int rulerInfo(CommandSourceStack source) {
        RulerData data = RulerData.get(source.getServer());
        source.sendSuccess(() -> data.hasRuler()
                ? Component.literal("Ruler of Nazarick: " + data.getRulerName() + " (" + data.getRuler() + ")").withStyle(ChatFormatting.GOLD)
                : Component.literal("Nazarick has no ruler. The raid path is open.").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int rulerReset(CommandSourceStack source) {
        RulerData.get(source.getServer()).clear();
        source.sendSuccess(() -> Component.literal("Nazarick's ruler has been cleared. The throne can be claimed again and the raid path is open."), true);
        return 1;
    }

    private static int rulerTransfer(CommandSourceStack source, ServerPlayer target) {
        RulerData.get(source.getServer()).setRuler(target.getUUID(), target.getGameProfile().getName());
        if (!RingOfAinzOoalGownItem.hasRing(target)) {
            ItemStack ring = new ItemStack(ModItems.RING_OF_AINZ_OOAL_GOWN.get());
            if (!target.getInventory().add(ring)) target.drop(ring, false);
        }
        target.sendSystemMessage(Component.literal("You are now the ruler of the Great Tomb of Nazarick.").withStyle(ChatFormatting.GOLD));
        source.sendSuccess(() -> Component.literal("Rule of Nazarick transferred to " + target.getGameProfile().getName()
                + ". Staff access and servant ownership follow the ruler; loyalty stays with the servants."), true);
        return 1;
    }

    // ------------------------------------------------------------------ ring travel
    private static int ring(CommandSourceStack source, String destination) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!RingOfAinzOoalGownItem.hasRing(player) || !NazarickRules.isRulerOrTeammate(player)) {
            player.displayClientMessage(Component.literal("The ring does not answer you.").withStyle(ChatFormatting.DARK_PURPLE), true);
            return 0;
        }
        ServerLevel level = player.serverLevel();
        NazarickLocationData data = NazarickLocationData.get(level);
        BlockPos target = switch (destination) {
            case "throne_room" -> data.getThroneOrigin() == null ? null : data.getThroneOrigin().offset(31, 1, 92);
            case "kings_quarters" -> data.getQuartersOrigin() == null ? null : data.getQuartersOrigin().offset(17, 1, 8);
            default -> data.getEntrance();
        };
        if (target == null) {
            player.displayClientMessage(Component.literal("That part of Nazarick does not exist in this world.").withStyle(ChatFormatting.DARK_PURPLE), true);
            return 0;
        }
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.2);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.teleportTo(level, target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 0.0F, 0.0F);
        level.playSound(null, target, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);
        return 1;
    }
}
