package net.legoman.animod.economy;

import net.legoman.animod.AniSaveData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * Магазин прокачек (Часть 2 диздока). Уровни 1-3, цены фиксированные.
 * Эффекты, работающие уже сейчас: Острота клинка (+30% урона/ур),
 * Ловкость (+10% скорости/ур), Оковы Сбоя (проклятие, -20% скорости).
 * Орб-слэшер и Счастливчик вступают в силу на боссфайте (Акт 9).
 */
public class Upgrades {

    public static final int[][] COSTS = {
            {100, 250, 500},  // Острота клинка
            {100, 200, 400},  // Орб-слэшер
            {100, 180, 300},  // Ловкость
            {120, 250, 450}   // Счастливчик
    };
    public static final int CURSE_REMOVAL_COST = 200;

    public static final String[] NAMES = {"Острота клинка", "Орб-слэшер", "Ловкость", "Счастливчик"};
    public static final String[] DESCS = {
            "+30% урона оружием за уровень",
            "+30% к хитбоксу парирования орбов (Акт 9)",
            "+10% к скорости бега за уровень",
            "+10% шанс выбить 15-20 HP в рулетке (Акт 9)"
    };

    // Фиксированные UUID модификаторов: transient (не сохраняются в NBT игрока),
    // применяются заново при каждом входе в мир — дубли исключены.
    private static final UUID BLADE_UUID = UUID.fromString("5f9a1c30-0001-4a11-9e2a-b0a1c0de0001");
    private static final UUID AGILITY_UUID = UUID.fromString("5f9a1c30-0002-4a11-9e2a-b0a1c0de0002");
    private static final UUID SHACKLES_UUID = UUID.fromString("5f9a1c30-0003-4a11-9e2a-b0a1c0de0003");

    public static int level(AniSaveData data, int index) {
        return switch (index) {
            case 0 -> data.upBlade;
            case 1 -> data.upSlasher;
            case 2 -> data.upAgility;
            default -> data.upLucky;
        };
    }

    public static void setLevel(AniSaveData data, int index, int level) {
        switch (index) {
            case 0 -> data.upBlade = level;
            case 1 -> data.upSlasher = level;
            case 2 -> data.upAgility = level;
            default -> data.upLucky = level;
        }
    }

    /** Пересобирает атрибуты игрока под текущие прокачки и проклятия. */
    public static void applyAttributes(ServerPlayer player, AniSaveData data) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(BLADE_UUID);
            if (data.upBlade > 0) {
                attack.addTransientModifier(new AttributeModifier(BLADE_UUID, "animod_up_blade",
                        0.30 * data.upBlade, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(AGILITY_UUID);
            speed.removeModifier(SHACKLES_UUID);
            if (data.upAgility > 0) {
                speed.addTransientModifier(new AttributeModifier(AGILITY_UUID, "animod_up_agility",
                        0.10 * data.upAgility, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            if (data.activeCurse == 2) { // Оковы Сбоя
                speed.addTransientModifier(new AttributeModifier(SHACKLES_UUID, "animod_curse_shackles",
                        -0.20, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
    }
}
