package net.legoman.animod.mixin;

import net.legoman.animod.economy.CoinManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Экономика (канон): торговля с жителем даёт +1 монету за сделку
 * (кап 10 монет в день — в CoinManager.addTradeCoin).
 */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerMixin {

    @Inject(method = "notifyTrade", at = @At("TAIL"))
    private void animod$tradeCoins(MerchantOffer offer, CallbackInfo ci) {
        if ((Object) this instanceof Villager villager
                && !villager.level().isClientSide
                && villager.getTradingPlayer() instanceof ServerPlayer player) {
            CoinManager.addTradeCoin(player);
        }
    }
}
