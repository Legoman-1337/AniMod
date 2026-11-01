package net.legoman.animod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * UI монет (по референсу Anime Adventures):
 *  - постоянный счётчик: бронзовая монета + число, слева над хотбаром;
 *  - при получении монет — всплывающая анимация "+N" с монетой (~5 секунд),
 *    монета «крутится» (осцилляция ширины — как разворот диска).
 */
public class CoinHud {

    public static final ResourceLocation COIN_TEX =
            new ResourceLocation("animod", "textures/gui/coin_bronze.png");
    private static final long POPUP_MS = 5000L;

    private static int coins = 0;
    private static final List<long[]> popups = new ArrayList<>(); // [amount, startMs]

    public static void setCoins(int value) {
        coins = value;
    }

    public static void addCoins(int amount, int total) {
        coins = total;
        popups.add(new long[]{amount, System.currentTimeMillis()});
    }

    public static void render(GuiGraphics graphics, Minecraft mc) {
        if (mc.player == null || mc.options.hideGui) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int baseY = h - 58; // над хотбаром, слева (референс 4)

        // Постоянный счётчик
        drawCoin(graphics, 8, baseY, 9, (float) (System.currentTimeMillis() % 2000L) / 2000.0F, 0.85F);
        graphics.drawString(mc.font, "§6" + coins, 24, baseY + 2, 0xFFAA00, true);

        // Всплывающие "+N"
        long now = System.currentTimeMillis();
        Iterator<long[]> it = popups.iterator();
        int idx = 0;
        while (it.hasNext()) {
            long[] p = it.next();
            float t = (now - p[1]) / (float) POPUP_MS;
            if (t >= 1.0F) { it.remove(); continue; }
            int y = baseY - 16 - idx * 14 - (int) (t * 18);
            int alphaPhase = t < 0.8F ? 0xFF : (int) (0xFF * (1.0F - (t - 0.8F) / 0.2F));
            int x = 8 + (int) (Math.sin((now + p[1]) * 0.004) * 1.5);
            drawCoin(graphics, x, y, 9, (now - p[1]) / 900.0F, alphaPhase / 255.0F);
            String text = "§e+" + p[0];
            graphics.drawString(mc.font, text, x + 12, y + 2,
                    (alphaPhase << 24) | 0xFFE000, true);
            idx++;
        }
    }

    /** Монета с «вращением»: осцилляция ширины спрайта. */
    private static void drawCoin(GuiGraphics graphics, int x, int y, int size, float spinT, float alpha) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + size / 2.0, y + size / 2.0, 0);
        float squash = Mth.clamp(Math.abs(Mth.sin(spinT * (float) Math.PI)), 0.22F, 1.0F);
        pose.scale(squash, 1.0F, 1.0F);
        var mc = Minecraft.getInstance();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(COIN_TEX, -size / 2, -size / 2, size, size, 0, 0, 32, 32, 32, 32);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        pose.popPose();
    }
}
