package dev.stamchuk.cigarette.effect;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;

public final class WithdrawalEffects {

    private static final Set<PotionEffectType> MANAGED = Set.of(
        PotionEffectType.SLOWNESS,
        PotionEffectType.WEAKNESS,
        PotionEffectType.MINING_FATIGUE,
        PotionEffectType.NAUSEA,
        PotionEffectType.HUNGER,
        PotionEffectType.BLINDNESS,
        PotionEffectType.DARKNESS
    );

    private WithdrawalEffects() {}

    public static Set<PotionEffectType> managed() {
        return MANAGED;
    }

    public static void clear(Player player) {
        for (var type : MANAGED) {
            player.removePotionEffect(type);
        }
    }
}
