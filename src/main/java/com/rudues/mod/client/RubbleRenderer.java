package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rudues.mod.entity.FeatheredKnightEntity;
import com.rudues.mod.entity.KnightScene;
import com.rudues.mod.entity.RubbleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The roof chunk in Benedikta's talons: a rough clump of real stone blocks, tumbling slightly, shaking in the clash. */
public class RubbleRenderer extends EntityRenderer<RubbleEntity> {
    private static final BlockState[] STONES = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
            Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState()};
    private final BlockRenderDispatcher blocks;

    public RubbleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 1.2F;
    }

    @Override
    public void render(RubbleEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float age = e.tickCount + pt;
        FeatheredKnightEntity k = e.holder();
        boolean strain = k != null && k.sceneStage() == KnightScene.STRAIN;
        RandomSource r = RandomSource.create(e.getId() * 341873128712L + 132897987541L);
        ps.pushPose();
        ps.translate(0, RubbleEntity.SIZE / 2, 0);
        if (strain) {
            ps.translate(Mth.sin(age * 2.3F) * 0.035F, Mth.sin(age * 3.1F) * 0.03F, Mth.sin(age * 2.7F) * 0.035F);
        }
        float bodyYaw = k != null ? Mth.rotLerp(pt, k.yBodyRotO, k.yBodyRot) : 0.0F;
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw + 17.0F));
        ps.mulPose(Axis.XP.rotationDegrees(8.0F + (strain ? 0 : Mth.sin(age * 0.05F) * 6.0F)));
        ps.mulPose(Axis.ZP.rotationDegrees(-6.0F + (strain ? 0 : Mth.cos(age * 0.04F) * 5.0F)));
        float s = 0.72F;
        ps.scale(s, s, s);
        // a 3 x 2 x 3 clump with its corners knocked off, a few blocks proud of the rest
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    float roll = r.nextFloat();
                    boolean corner = Math.abs(x) + Math.abs(z) == 2;
                    if ((corner && y == 1 && roll < 0.75F) || (corner && y == 0 && roll < 0.3F)) {
                        continue;
                    }
                    BlockState st = STONES[r.nextInt(STONES.length)];
                    ps.pushPose();
                    ps.translate(x - 0.5 + (r.nextFloat() - 0.5) * 0.25, y - 1.0 + (r.nextFloat() - 0.5) * 0.2, z - 0.5 + (r.nextFloat() - 0.5) * 0.25);
                    ps.translate(0.5, 0.5, 0.5);
                    ps.mulPose(Axis.YP.rotationDegrees((r.nextFloat() - 0.5F) * 24.0F));
                    ps.mulPose(Axis.XP.rotationDegrees((r.nextFloat() - 0.5F) * 18.0F));
                    ps.translate(-0.5, -0.5, -0.5);
                    blocks.renderSingleBlock(st, ps, buffers, light, OverlayTexture.NO_OVERLAY);
                    ps.popPose();
                }
            }
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(RubbleEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
