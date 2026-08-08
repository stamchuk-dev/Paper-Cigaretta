package dev.stamchuk.cigarette.effect;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public record WithdrawalEffect(PotionEffectType type, int amplifier) {

    public WithdrawalEffect {
        if (type == null) throw new IllegalArgumentException("effect type must not be null");
        amplifier = Math.clamp(amplifier, 0, 255);
    }

    public static List<WithdrawalEffect> parse(ConfigurationSection section, Consumer<String> warn) {
        if (section == null) return List.of();
        var parsed = new ArrayList<WithdrawalEffect>();
        for (var key : section.getKeys(false)) {
            var type = resolve(key);
            if (type == null) {
                warn.accept("unknown potion effect '" + key + "' in withdrawal config, skipped");
                continue;
            }
            if (!WithdrawalEffects.managed().contains(type)) {
                warn.accept("potion effect '" + key + "' is not managed by withdrawal, skipped");
                continue;
            }
            parsed.add(new WithdrawalEffect(type, section.getInt(key, 0)));
        }
        return List.copyOf(parsed);
    }

    private static PotionEffectType resolve(String name) {
        var key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        if (key == null) return null;
        return Registry.EFFECT.get(key);
    }
}
