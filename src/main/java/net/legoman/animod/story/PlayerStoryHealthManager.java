package net.legoman.animod.story;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

public class PlayerStoryHealthManager {
    private static final UUID STORY_HP_UUID = UUID.fromString("e3d548b2-76fa-4921-9689-5321ab91024a");
    private static final String MODIFIER_NAME = "AnyaStoryMaxHpModifier";

    /**
     * Применяет ограничение максимального здоровья (от 2.0 до 20.0).
     * 20.0 = 10 сердец (норма), 14.0 = 7 сердец, 10.0 = 5 сердец.
     */
    public static void applyHealthCap(ServerPlayer player, double targetMaxHp) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;

        double clamped = Math.max(2.0, Math.min(20.0, targetMaxHp));
        double difference = clamped - 20.0;

        AttributeModifier existing = attr.getModifier(STORY_HP_UUID);

        // Если модификатор уже стоит с точно таким же значением — не дергаем атрибуты лишний раз
        if (existing != null && Math.abs(existing.getAmount() - difference) < 0.001) {
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
            return;
        }

        if (existing != null) {
            attr.removeModifier(STORY_HP_UUID);
        }

        if (Math.abs(difference) > 0.001) {
            AttributeModifier modifier = new AttributeModifier(
                    STORY_HP_UUID,
                    MODIFIER_NAME,
                    difference,
                    AttributeModifier.Operation.ADDITION
            );
            attr.addPermanentModifier(modifier);
        }

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void resetHealthCap(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null && attr.getModifier(STORY_HP_UUID) != null) {
            attr.removeModifier(STORY_HP_UUID);
        }
    }
}