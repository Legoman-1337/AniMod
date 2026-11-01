package net.legoman.animod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.legoman.animod.AniMod;
import net.legoman.animod.AnyaEntity;
import net.legoman.animod.economy.Upgrades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Магазин в стиле референса (Anime Adventures):
 * крупный золотой заголовок с монетками справа, слева панель с живой моделью Ани
 * (имя + черта по карме), справа строки апгрейдов: иконка в слоте, пипсы уровней,
 * жёлтый ценник с монеткой. Внизу большая зелёная «ПРОДОЛЖИТЬ».
 * Тема (фон/рамка/акцент + подзаголовок) меняется от кармы (6 бандов).
 */
public class ShopScreen extends Screen {

    private record Entry(int id, String name, String desc, ItemStack icon) {}

    private static final Entry[] ENTRIES = {
            new Entry(0, "Острота клинка", "+30% урона оружием / ур.", new ItemStack(Items.IRON_SWORD)),
            new Entry(1, "Орб-слэшер", "+30% пробития щита (босс)", new ItemStack(Items.SLIME_BALL)),
            new Entry(2, "Ловкость", "+10% скорости бега / ур.", new ItemStack(Items.FEATHER)),
            new Entry(3, "Счастливчик", "high-roll рулетки HP (босс)", new ItemStack(Items.EMERALD)),
            new Entry(4, "Снятие проклятия", "очищает текущее проклятие", new ItemStack(Items.MILK_BUCKET))
    };

    // Темы по карме: [фон, рамка, акцент, фон строки]
    private static final int[][] THEMES = {
            {0xF0140408, 0xFF6E1010, 0xFFFF5555, 0xE03A0A0A},
            {0xE0160A10, 0xFF8A3A50, 0xFFFF90B8, 0xE0331420},
            {0xE0140E06, 0xFF9A6A20, 0xFFFFCC55, 0xE02E240E},
            {0xE00E1018, 0xFF3A4A6E, 0xFF88AAFF, 0xE0141C2E},
            {0xE0180A12, 0xFFB04070, 0xFFFF8AC0, 0xE02E1220},
            {0xF0160410, 0xFFFF2A6D, 0xFFFF5C9C, 0xE03A0A1E}
    };
    private static final String[] SUBTITLES = {
            "С-СОБИРАЙ... МОНЕТЫ... ДЛЯ Н&&Е...",
            "Хм. Можешь купить. Не то чтобы мне было дело.",
            "Б-буду рада, если купишь... только потому, что тебе нужно!",
            "Трать монеты на улучшения и приятные бонусы!",
            "Я всё приготовила для тебя, заходи чаще",
            "Купи всё. Ты никуда не уйдёшь, пока не купишь всё."
    };
    private static final String[] TRAITS = {
            "Хоррор", "Обиженная", "Цундере", "Нейтральная", "Любящая", "Яндере"
    };

    private static ShopScreen active = null;

    private int coins;
    private final int[] levels = new int[4];
    private int curseId;
    private int karma = 50;
    private boolean discount = false;
    private AnyaEntity previewAnya = null;
    private final ChipButton[] chips = new ChipButton[ENTRIES.length];

    // Кэш раскладки: init() ставит кнопки, render() рисует панели по тем же координатам
    private int leftX, leftY, leftW, leftH;
    private int rowX, rowW, rowH, rowGap;

    public ShopScreen(int coins, int[] levels, int curseId, int karma, boolean discount) {
        super(Component.literal("МАГАЗИН АНИ"));
        this.coins = coins;
        System.arraycopy(levels, 0, this.levels, 0, 4);
        this.curseId = curseId;
        this.karma = karma;
        this.discount = discount;
    }

    public static void open(int coins, int[] levels, int curseId, int karma, boolean discount) {
        active = new ShopScreen(coins, levels, curseId, karma, discount);
        Minecraft.getInstance().setScreen(active);
    }

    public static void updateData(int coins, int[] levels, int curseId, int karma, boolean discount) {
        if (active == null) return;
        active.coins = coins;
        System.arraycopy(levels, 0, active.levels, 0, 4);
        active.curseId = curseId;
        active.karma = karma;
        active.discount = discount;
    }

    private int band() {
        if (karma < 20) return 0;
        if (karma < 30) return 1;
        if (karma < 50) return 2;
        if (karma < 75) return 3;
        if (karma < 90) return 4;
        return 5;
    }

    /** Единая раскладка для init() (кнопки) и render() (панели). */
    private void layout() {
        int w = this.width, h = this.height;
        int m = 16;
        int top = discount ? 74 : 66;
        leftX = m;
        leftY = top;
        leftW = Math.max(140, w / 4 + 20);
        leftH = h - top - 78;

        rowX = leftX + leftW + 12;
        rowW = w - rowX - m;
        rowH = (leftH - 4 * 6) / 5;
        rowGap = 6;
    }

    @Override
    protected void init() {
        try {
            previewAnya = new AnyaEntity(AnyaEntity.ANYA, Minecraft.getInstance().level);
        } catch (Exception e) {
            previewAnya = null;
            AniMod.LOGGER.error("[Shop] Не удалось создать модель Ани: ", e);
        }
        layout();

        // Ценники-чипы добавляются в дерево виджетов ОДИН раз в init(): иначе клик
        // по ним не ловится (Screen.mouseClicked их не видит) и купить нельзя
        for (int i = 0; i < ENTRIES.length; i++) {
            final int idx = ENTRIES[i].id();
            int ry = rowY(i);
            ChipButton chip = new ChipButton(rowX + rowW - 74, ry + rowH / 2 - 11, 66, 22, "", b -> buy(idx));
            chips[idx] = chip;
            addRenderableWidget(chip);
        }

        addRenderableWidget(new GreenButton(this.width / 2 - 110, this.height - 40, 220, 26, "ПРОДОЛЖИТЬ", b -> onClose()));
    }

    private int rowY(int i) {
        return leftY + i * (rowH + rowGap);
    }

    private void buy(int id) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(id);
        ClientPlayNetworking.send(AniMod.SHOP_BUY_PACKET, buf);
    }

    private int disc(int base) {
        return discount ? Math.max(1, (int) Math.ceil(base * 0.8)) : base;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int w = this.width, h = this.height;
        layout();
        int[] th = THEMES[band()];

        // ===== ЗАГОЛОВОК =====
        g.fill(0, 0, w, 4, th[1]);                       // акцентная полоса сверху
        g.fill(0, 4, w, 54, 0x88000000);
        var pose = g.pose();
        pose.pushPose();
        pose.scale(1.5F, 1.5F, 1.0F);
        int tw = this.font.width("МАГАЗИН ");
        g.drawString(this.font, "МАГАЗИН ", 18, 9, 0xFFF2F2F8, true);
        g.drawString(this.font, "АНИ", 18 + (int) (tw * 1.5F), 9, th[2], true);
        pose.popPose();
        // золотая линия-подчёркивание заголовка
        g.fill(17, 49, 17 + (int) ((tw + this.font.width("АНИ") + 2) * 1.5F), 51, th[2]);
        g.drawString(this.font, SUBTITLES[band()], 20, 56, 0xB8B8C0, false);

        // Счётчик монет в правом верхнем углу
        String coinsStr = String.valueOf(coins);
        int cw = this.font.width(coinsStr) + 34;
        g.fill(w - cw - 14, 8, w - 14, 32, 0x88000000);
        g.fill(w - cw - 14, 8, w - 14, 9, th[2]);
        g.blit(CoinHud.COIN_TEX, w - cw - 6, 12, 16, 16, 0, 0, 32, 32, 32, 32);
        g.drawString(this.font, coinsStr, w - cw + 14, 16, 0xFFD700, true);

        if (discount) {
            long t = System.currentTimeMillis();
            float pulse = 0.6F + 0.4F * (float) Math.abs(Math.sin(t * 0.005));
            int a = (int) (150 + 80 * pulse);
            g.fill(0, 56, w, 70, (a << 24) | 0x007A4E00);
            g.drawString(this.font, "-20% НА ВСЁ! УСПЕЙ, ПОКА ДЕЙСТВУЕТ...", 22, 60, 0xFFFF55, true);
        }

        // ===== ЛЕВАЯ ПАНЕЛЬ: МОДЕЛЬ АНИ =====
        panel(g, leftX, leftY, leftW, leftH, th);
        if (previewAnya != null) {
            try {
                int cx = leftX + leftW / 2;
                int feetY = leftY + leftH - 44;
                float scl = (leftH - 70) / 95.0F; // Аня ~4 блока
                float sway = (float) Math.sin(System.currentTimeMillis() * 0.0008) * 25.0F;
                renderEntity(g, previewAnya, cx, feetY, scl, 180.0F + sway);
            } catch (Exception e) {
                AniMod.LOGGER.error("[Shop] Рендер модели Ани упал: ", e);
            }
        }
        // Табличка с именем и чертой
        int plateH = 32;
        g.fill(leftX + 1, leftY + leftH - plateH - 1, leftX + leftW - 1, leftY + leftH - 1, 0x99101018);
        g.fill(leftX + 1, leftY + leftH - plateH - 1, leftX + leftW - 1, leftY + leftH - plateH, th[2]);
        g.drawCenteredString(this.font, "§dАня §7— §f" + TRAITS[band()],
                leftX + leftW / 2, leftY + leftH - plateH + 4, 0xFFFFFF);
        g.blit(CoinHud.COIN_TEX, leftX + leftW / 2 - String.valueOf(coins).length() * 3 - 10,
                leftY + leftH - 16, 10, 10, 0, 0, 32, 32, 32, 32);
        g.drawCenteredString(this.font, "§e" + coins, leftX + leftW / 2 + 6, leftY + leftH - 14, 0xFFFFFF);

        // ===== ПРАВАЯ ЧАСТЬ: СТРОКИ АПГРЕЙДОВ =====
        for (int i = 0; i < ENTRIES.length; i++) {
            Entry e = ENTRIES[i];
            int ry = rowY(i);

            // фон строки
            g.fill(rowX, ry, rowX + rowW, ry + rowH, th[3]);
            g.fill(rowX, ry, rowX + rowW, ry + 1, th[1]);
            g.fill(rowX, ry + rowH - 1, rowX + rowW, ry + rowH, th[1]);

            // иконка в слоте
            int slot = rowH - 10;
            g.fill(rowX + 6, ry + 5, rowX + 6 + slot, ry + 5 + slot, 0xFF101018);
            g.fill(rowX + 6, ry + 5, rowX + 6 + slot, ry + 6, th[1]);
            g.fill(rowX + 6, ry + 4 + slot, rowX + 7 + slot, ry + 5 + slot, th[1]);
            g.fill(rowX + 5 + slot, ry + 5, rowX + 6 + slot, ry + 5 + slot, th[1]);
            g.fill(rowX + 6, ry + 5, rowX + 7, ry + 5 + slot, th[1]);
            g.renderItem(e.icon(), rowX + 7, ry + 6);

            // название + описание
            String lvl = e.id() < 4 ? "  §7[" + levels[e.id()] + "/3]" : "";
            g.drawString(this.font, e.name() + lvl, rowX + slot + 16, ry + 6, th[2], true);
            g.drawString(this.font, e.desc(), rowX + slot + 16, ry + 18, 0xC8C8D0, false);

            // пипсы уровней (для прокачек)
            if (e.id() < 4) {
                for (int p = 0; p < 3; p++) {
                    int px = rowX + slot + 16 + p * 9;
                    int py = ry + rowH - 10;
                    g.fill(px, py, px + 6, py + 6, p < levels[e.id()] ? th[2] : 0x40FFFFFF);
                }
            }

            // ценник-чип (кнопка из init)
            String price;
            if (e.id() == 4) price = curseId == 0 ? "—" : String.valueOf(disc(200));
            else price = levels[e.id()] >= 3 ? "MAX" : String.valueOf(disc(Upgrades.COSTS[e.id()][levels[e.id()]]));
            ChipButton chip = chips[e.id()];
            if (chip != null) {
                chip.setMessage(Component.literal(price));
                chip.active = !(e.id() == 4 && curseId == 0) && !(e.id() < 4 && levels[e.id()] >= 3);
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Панель: тёмный фон + рамка цвета темы. */
    private void panel(GuiGraphics g, int x, int y, int w, int h, int[] th) {
        g.fill(x, y, x + w, y + h, th[0]);
        g.fill(x, y, x + w, y + 1, th[1]);
        g.fill(x, y + h - 1, x + w, y + h, th[1]);
        g.fill(x, y, x + 1, y + h, th[1]);
        g.fill(x + w - 1, y, x + w, y + h, th[1]);
    }

    /** Живой рендер сущности в GUI — точный ванильный паттерн InventoryScreen 1.20.1:
     * translate → scaling(s, s, -s) → rotateZ(π) → Lighting.setupForEntityInInventory.
     * Прежняя версия делала ту же инверсию, но БЕЗ специального освещения —
     * нормали переворачивались и модель была чёрным силуэтом. */
    private void renderEntity(GuiGraphics g, LivingEntity entity, float cx, float feetY, float scale, float yaw) {
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.yBodyRot = yaw;
        entity.yBodyRotO = yaw;
        entity.yHeadRot = yaw;
        entity.yHeadRotO = yaw;

        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, feetY, 50.0F);
        ps.mulPoseMatrix(new org.joml.Matrix4f().scaling(scale, scale, -scale));
        ps.mulPose(new org.joml.Quaternionf().rotateZ((float) Math.PI));

        com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, ps, buffers, 0xF00000);
        buffers.endBatch();
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        dispatcher.setRenderShadow(true);
        ps.popPose();
    }

    /** Жёлтый ценник-чип с монеткой, как в референсе. */
    private static class ChipButton extends Button {
        ChipButton(int x, int y, int w, int h, String label, OnPress press) {
            super(x, y, w, h, Component.literal(label), press, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int base = this.active ? (this.isHovered() ? 0xFFFFD24D : 0xFFE7A81C) : 0xFF3A3A42;
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, base);
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 2,
                    this.active ? 0xFFFFE08A : 0xFF55555E);
            g.fill(this.getX(), this.getY() + this.height - 2, this.getX() + this.width, this.getY() + this.height,
                    this.active ? 0xFF9A6A10 : 0xFF202028);
            g.blit(CoinHud.COIN_TEX, this.getX() + 4, this.getY() + this.height / 2 - 6, 12, 12, 0, 0, 32, 32, 32, 32);
            int textColor = this.active ? 0xFF3A2200 : 0xFF777777;
            g.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                    this.getX() + (this.width + 8) / 2, this.getY() + (this.height - 8) / 2, textColor);
        }
    }

    /** Большая зелёная кнопка «Продолжить». */
    private static class GreenButton extends Button {
        GreenButton(int x, int y, int w, int h, String label, OnPress press) {
            super(x, y, w, h, Component.literal(label), press, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int base = this.isHovered() ? 0xFF3ED63E : 0xFF2FB52F;
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, base);
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 2, 0xFF7CFF7C);
            g.fill(this.getX(), this.getY() + this.height - 2, this.getX() + this.width, this.getY() + this.height, 0xFF1A7A1A);
            g.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                    this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, 0xFF083A08);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        active = null;
        super.onClose();
    }
}
