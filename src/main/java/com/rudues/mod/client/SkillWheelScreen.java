package com.rudues.mod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rudues.mod.ability.AbilityType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Hold R, point at a move and release R (or click) to select it. Then press the cast button
 * (Controls > Rudues Mod > Cast Selected Ability) to use it.
 */
public class SkillWheelScreen extends Screen {
    private static final int INNER = 40;
    private static final int OUTER = 120;

    private final List<AbilityType> abilities = AbilityType.forForm(ClientPowerState.isHero());
    private int hovered = -1;

    public SkillWheelScreen() {
        super(Component.translatable("screen.rudues.skill_wheel"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double segStart(int i) {
        int n = abilities.size();
        return -90.0 - 180.0 / n + (360.0 / n) * i;
    }

    private int segmentAt(double mx, double my) {
        double dx = mx - width / 2.0;
        double dy = my - height / 2.0;
        if (Math.sqrt(dx * dx + dy * dy) < INNER) {
            return -1;
        }
        double deg = Math.toDegrees(Math.atan2(dy, dx));
        double size = 360.0 / abilities.size();
        for (int i = 0; i < abilities.size(); i++) {
            double rel = ((deg - segStart(i)) % 360.0 + 360.0) % 360.0;
            if (rel < size) {
                return i;
            }
        }
        return -1;
    }

    private int hoverNow() {
        Minecraft mc = Minecraft.getInstance();
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
        return segmentAt(mx, my);
    }

    private void select(int i) {
        if (i >= 0 && i < abilities.size()) {
            ClientPowerState.setSelected(abilities.get(i));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public void tick() {
        // releasing the wheel key selects whatever is highlighted
        if (!ClientKeys.isHeld(ClientKeys.OPEN_WHEEL)) {
            select(hoverNow());
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int seg = segmentAt(mx, my);
            if (seg >= 0) {
                select(seg);
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x66000000);
        int cx = width / 2;
        int cy = height / 2;
        hovered = segmentAt(mouseX, mouseY);
        int n = abilities.size();
        double size = 360.0 / n;
        AbilityType selected = ClientPowerState.getSelected();

        for (int i = 0; i < n; i++) {
            boolean isHover = i == hovered;
            boolean isSelected = abilities.get(i) == selected;
            int color = isHover ? 0xD0A040E0 : isSelected ? 0xC07A2FC0 : 0xA03A1560;
            drawSector(g, cx, cy, segStart(i) + 1.5, segStart(i) + size - 1.5, INNER, isHover ? OUTER + 8 : OUTER, color);
        }

        for (int i = 0; i < n; i++) {
            AbilityType t = abilities.get(i);
            double mid = Math.toRadians(segStart(i) + size / 2.0);
            int lx = cx + (int) (Math.cos(mid) * (INNER + OUTER) / 2.0);
            int ly = cy + (int) (Math.sin(mid) * (INNER + OUTER) / 2.0);
            g.drawCenteredString(font, t.displayName(), lx, ly - 4, t == selected ? 0xFFE8FF : 0xFFFFFF);
            if (t == selected) {
                g.drawCenteredString(font, Component.translatable("gui.rudues.selected"), lx, ly + 6, 0xE8C0FF);
            }
        }

        g.drawCenteredString(font, Component.literal("R"), cx, cy - 4, 0xFFFFFF);

        Component bottom = hovered >= 0 ? abilities.get(hovered).description()
                : Component.translatable("gui.rudues.wheel_hint");
        g.drawCenteredString(font, bottom, cx, cy + OUTER + 24, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.rudues.cast_hint",
                ClientKeys.CAST_SELECTED.getTranslatedKeyMessage()), cx, cy + OUTER + 38, 0xC8A0E8);
    }

    private static void drawSector(GuiGraphics g, int cx, int cy, double startDeg, double endDeg,
                                   int inner, int outer, int argb) {
        Matrix4f m = g.pose().last().pose();
        float a = (argb >>> 24 & 255) / 255f;
        float r = (argb >>> 16 & 255) / 255f;
        float gr = (argb >>> 8 & 255) / 255f;
        float b = (argb & 255) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int steps = 28;
        for (int s = 0; s <= steps; s++) {
            double ang = Math.toRadians(startDeg + (endDeg - startDeg) * s / steps);
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            buf.vertex(m, cx + cos * outer, cy + sin * outer, 0).color(r, gr, b, a).endVertex();
            buf.vertex(m, cx + cos * inner, cy + sin * inner, 0).color(r, gr, b, a).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
