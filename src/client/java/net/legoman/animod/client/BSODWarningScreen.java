package net.legoman.animod.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.legoman.animod.AniMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * АКТ 7: предупреждающая плашка перед аварийным сбросом.
 * «Критический сбой. Я перезагружаю хост. Сохрани всё открытое на ПК!» → [Я ГОТОВ].
 */
public class BSODWarningScreen extends Screen {

    private int autoConfirmTicks = -1;

    public BSODWarningScreen() {
        super(Component.literal("КРИТИЧЕСКИЙ СБОЙ"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Я ГОТОВ"), b -> {
            ClientPlayNetworking.send(AniMod.BSOD_CONFIRM_PACKET, new FriendlyByteBuf(Unpooled.buffer()));
            this.onClose();
        }).bounds(this.width / 2 - 60, this.height - 60, 120, 20).build());

        // Автотест (файл-флаг animod_autotest.txt): агент не умеет кликать —
        // через 4 секунды плашка подтверждает себя сама
        try {
            if (java.nio.file.Files.exists(java.nio.file.Path.of(
                    Minecraft.getInstance().gameDirectory.getAbsolutePath(), "config", "animod_autotest.txt"))) {
                autoConfirmTicks = 80;
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void tick() {
        if (autoConfirmTicks > 0 && --autoConfirmTicks == 0) {
            ClientPlayNetworking.send(AniMod.BSOD_CONFIRM_PACKET, new FriendlyByteBuf(Unpooled.buffer()));
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, "§4§lКРИТИЧЕСКИЙ СБОЙ ХОСТА", this.width / 2, this.height / 2 - 60, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "§fЯ перезагружаю хост.", this.width / 2, this.height / 2 - 35, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "§eСохрани всё открытое на ПК!", this.width / 2, this.height / 2 - 15, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "§7После нажатия игра сохранит мир и выключится.", this.width / 2, this.height / 2 + 10, 0x888888);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
