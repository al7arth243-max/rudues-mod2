package com.rudues.mod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** The purple Hero of Hell gauge above the hearts, plus the currently selected ability. */
public class HeroGaugeOverlay implements IGuiOverlay {
    private static final int BAR_W = 81;
    private static final int BAR_H = 5;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (!ClientPowerState.hasPowers) {
            return;
        }
        Minecraft mc = gui.getMinecraft();
        Font font = mc.font;
        int x = width / 2 - 91;
        int y = height - gui.leftHeight + 2;

        boolean hero = ClientPowerState.isHero();
        boolean full = !hero && ClientPowerState.gauge >= 100;
        float frac = Math.max(0.0F, Math.min(1.0F, ClientPowerState.fraction()));
        float pulse = (float) (0.5 + 0.5 * Math.sin((mc.level == null ? 0 : mc.level.getGameTime() + partialTick) * 0.4));

        int fillColor;
        if (hero || full) {
            int v = (int) (170 + 85 * pulse);
            fillColor = 0xFF000000 | (v << 16) | (90 << 8) | 0xFF;
        } else {
            fillColor = 0xFFB040FF;
        }

        g.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, 0xFF14041F);
        g.fill(x, y, x + BAR_W, y + BAR_H, 0xFF3A1157);
        g.fill(x, y, x + (int) (BAR_W * frac), y + BAR_H, fillColor);
        g.fill(x, y, x + (int) (BAR_W * frac), y + 1, 0x60FFFFFF);

        Component selected = Component.literal("> ").append(ClientPowerState.getSelected().displayName())
                .append(" [").append(ClientKeys.CAST_SELECTED.getTranslatedKeyMessage()).append("]");
        g.drawString(font, selected, x, y - 10, 0xE8C0FF, true);
        if (hero) {
            int secs = ClientPowerState.heroTicks / 20;
            g.drawString(font, Component.translatable("gui.rudues.hero_of_hell").append(" " + secs + "s"),
                    x, y - 20, 0xFF70FF, true);
        }
        gui.leftHeight += 10;
    }
}
