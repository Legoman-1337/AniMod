package net.legoman.animod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.Locale;

public class AnyaTeleportConfirmScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 130;
    private static final int BUTTON_WIDTH = 92;
    private static final int BUTTON_HEIGHT = 22;

    private final long distanceBlocks;
    private boolean answered;
    private int panelX;
    private int panelY;

    public AnyaTeleportConfirmScreen(double distance) {
        super(Component.literal("Телепортация к Ане"));
        this.distanceBlocks = Math.round(distance);
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int x1 = panelX;
        int y1 = panelY;
        int x2 = panelX + PANEL_WIDTH;
        int y2 = panelY + PANEL_HEIGHT;

        // Мягкая внешняя тень
        graphics.fill(x1 - 2, y1 - 2, x2 + 2, y2 + 2, 0x40000000);
        graphics.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0x25000000);

        // Внешняя розовая рамка
        graphics.fill(x1, y1, x2, y2, 0xFFFF5596);

        // Внутренняя нежная кайма
        graphics.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0xFFFFD3E5);

        // Основной молочно-белый фон
        graphics.fill(x1 + 3, y1 + 3, x2 - 3, y2 - 3, 0xFFFFFDFE);

        // Шапка заголовка с аккуратным разделителем
        graphics.fill(x1 + 3, y1 + 3, x2 - 3, y1 + 24, 0xFFFFF0F6);
        graphics.fill(x1 + 3, y1 + 24, x2 - 3, y1 + 25, 0xFFFFC8DD);

        graphics.drawCenteredString(font, Component.literal("§8§lТелепортация к Ане"), width / 2, y1 + 8, 0xFF2B2B2B);

        // Центральная плашка расстояния
        int badgeX1 = width / 2 - 90;
        int badgeX2 = width / 2 + 90;
        int badgeY1 = y1 + 36;
        int badgeY2 = y1 + 58;

        graphics.fill(badgeX1, badgeY1, badgeX2, badgeY2, 0xFFFFB5CF);
        graphics.fill(badgeX1 + 1, badgeY1 + 1, badgeX2 - 1, badgeY2 - 1, 0xFFFFF6F9);

        String formattedDistance = NumberFormat.getIntegerInstance(new Locale("ru", "RU")).format(distanceBlocks);
        graphics.drawCenteredString(font, Component.literal("§8До Ани: §c§l" + formattedDistance + " §8блоков"), width / 2, y1 + 43, 0xFF333333);

        graphics.drawCenteredString(font, Component.literal("§7Всё равно переместиться?"), width / 2, y1 + 68, 0xFF555555);

        // Кнопки
        int buttonY = y1 + 92;
        int yesX = width / 2 - BUTTON_WIDTH - 6;
        int cancelX = width / 2 + 6;

        drawActionPinkButton(graphics, yesX, buttonY, mouseX, mouseY, "§f§lДа!");
        drawCancelButton(graphics, cancelX, buttonY, mouseX, mouseY, "§7Отмена");
    }

    private void drawActionPinkButton(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, String label) {
        boolean hovered = inside(mouseX, mouseY, x, y);
        int topHighlight = hovered ? 0xFFFFA0C8 : 0xFFFF80B2;
        int baseColor = hovered ? 0xFFFF7AAE : 0xFFFF5E9C;
        int shadowColor = hovered ? 0xFFE04583 : 0xFFD63B78;

        // Эффект объемной кнопки
        graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, shadowColor);
        graphics.fill(x + 1, y, x + BUTTON_WIDTH - 1, y + 1, topHighlight);
        graphics.fill(x + 1, y + 1, x + BUTTON_WIDTH - 1, y + BUTTON_HEIGHT - 2, baseColor);

        graphics.drawCenteredString(font, Component.literal(label), x + BUTTON_WIDTH / 2, y + 6, 0xFFFFFFFF);
    }

    private void drawCancelButton(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, String label) {
        boolean hovered = inside(mouseX, mouseY, x, y);
        int borderColor = hovered ? 0xFFFF85B5 : 0xFFD4C2CB;
        int bgColor = hovered ? 0xFFFFF0F5 : 0xFFF5EDF1;

        graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, borderColor);
        graphics.fill(x + 1, y + 1, x + BUTTON_WIDTH - 1, y + BUTTON_HEIGHT - 1, bgColor);

        String coloredLabel = hovered ? "§c§lОтмена" : label;
        graphics.drawCenteredString(font, Component.literal(coloredLabel), x + BUTTON_WIDTH / 2, y + 6, 0xFF333333);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int buttonY = panelY + 92;
            int yesX = width / 2 - BUTTON_WIDTH - 6;
            int cancelX = width / 2 + 6;
            if (inside(mouseX, mouseY, yesX, buttonY)) {
                respond(true);
                return true;
            }
            if (inside(mouseX, mouseY, cancelX, buttonY)) {
                respond(false);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + BUTTON_WIDTH
                && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            respond(false);
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            respond(true);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        respond(false);
    }

    private void respond(boolean accepted) {
        if (answered) return;
        answered = true;
        AniModClient.sendTeleportConfirmation(accepted);
        if (minecraft != null) minecraft.setScreen(null);
    }
}