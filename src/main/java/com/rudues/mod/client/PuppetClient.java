package com.rudues.mod.client;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.InfernalShadowEntity;
import com.rudues.mod.network.PuppetPacket;
import com.rudues.mod.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Stand-ins: for a scripted moment (the awakening, Pummel) a player is drawn as a copy of the Infernal Shadow's rig
 * wearing their skin, armour and sword, so they can bend at the knees and elbows and lie down. The stand-in is a
 * client-only Shadow instance that is never added to the world; it is rendered in the player's place.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class PuppetClient {
    private PuppetClient() {
    }

    private static final class Puppet {
        InfernalShadowEntity body;
        String clip;
        int left;
        float yaw;
        boolean sword;
    }

    private static final Map<Integer, Puppet> PUPPETS = new HashMap<>();

    public static boolean active(int playerId) {
        return PUPPETS.containsKey(playerId);
    }

    /** the clip a player's stand-in is playing ("" if none), and how many ticks it has left */
    public static String clipOf(int playerId) {
        Puppet pu = PUPPETS.get(playerId);
        return pu == null ? "" : pu.clip;
    }

    public static int ticksLeft(int playerId) {
        Puppet pu = PUPPETS.get(playerId);
        return pu == null ? 0 : pu.left;
    }

    public static float yawOf(int playerId) {
        Puppet pu = PUPPETS.get(playerId);
        return pu == null ? 0.0F : pu.yaw;
    }

    public static void receive(PuppetPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (m.clip.isEmpty() || m.ticks <= 0) {
            PUPPETS.remove(m.playerId);
            return;
        }
        Entity e = mc.level.getEntity(m.playerId);
        if (!(e instanceof Player player)) {
            return;
        }
        Puppet pu = PUPPETS.get(m.playerId);
        if (pu == null) {
            pu = new Puppet();
            pu.body = new InfernalShadowEntity(ModEntities.INFERNAL_SHADOW.get(), mc.level);
            pu.body.makePuppet(player.getUUID());
            PUPPETS.put(m.playerId, pu);
        }
        pu.clip = m.clip;
        pu.left = m.ticks;
        pu.yaw = m.yaw;
        pu.sword = m.sword;
        pu.body.puppetCorrupt = m.corrupt;
        pu.body.puppetClip = m.clip;
        dress(pu, player);
        pu.body.triggerAnim("main", m.clip);
    }

    /** the player's own armour, and a sword when the clip needs one (theirs, if they hold one) */
    private static void dress(Puppet pu, Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = player.getItemBySlot(slot);
            if (!ItemStack.matches(worn, pu.body.getItemBySlot(slot))) {
                pu.body.setItemSlot(slot, worn.copy());
            }
        }
        ItemStack held = player.getMainHandItem();
        ItemStack want = !pu.sword ? ItemStack.EMPTY : held.getItem() instanceof SwordItem ? held : new ItemStack(Items.NETHERITE_SWORD);
        if (!ItemStack.matches(want, pu.body.getMainHandItem())) {
            pu.body.setItemSlot(EquipmentSlot.MAINHAND, want.copy());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PUPPETS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        PUPPETS.entrySet().removeIf(en -> {
            Puppet pu = en.getValue();
            Entity e = mc.level.getEntity(en.getKey());
            if (!(e instanceof Player player) || !player.isAlive() || pu.body.level() != mc.level || --pu.left <= 0) {
                return true;
            }
            pu.body.tickCount++;          // GeckoLib times entity animations by their tick count
            if (pu.body.tickCount % 10 == 0) {
                dress(pu, player);
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PUPPETS.clear();
    }

    // after the demon-form check (which cancels): only players drawn normally are replaced
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Puppet pu = PUPPETS.get(event.getEntity().getId());
        if (pu == null || !(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        event.setCanceled(true);
        InfernalShadowEntity b = pu.body;
        float pt = event.getPartialTick();
        b.setPos(player.getX(), player.getY(), player.getZ());
        b.xo = player.xo;
        b.yo = player.yo;
        b.zo = player.zo;
        b.setYRot(pu.yaw);
        b.yRotO = pu.yaw;
        b.yBodyRot = pu.yaw;
        b.yBodyRotO = pu.yaw;
        b.yHeadRot = pu.yaw;
        b.yHeadRotO = pu.yaw;
        b.setXRot(0.0F);
        b.xRotO = 0.0F;
        EntityRenderer<? super InfernalShadowEntity> r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(b);
        r.render(b, pu.yaw, pt, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }
}
