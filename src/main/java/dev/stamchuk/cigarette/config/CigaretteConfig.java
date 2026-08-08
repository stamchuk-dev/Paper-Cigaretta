package dev.stamchuk.cigarette.config;

import dev.stamchuk.cigarette.effect.WithdrawalEffect;
import dev.stamchuk.cigarette.model.AddictionLevel;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public record CigaretteConfig(
    Material itemMaterial,
    int customModelData,
    String itemName,
    int cooldownSeconds,
    int strengthDuration,
    int strengthAmplifier,
    int regenDuration,
    int regenAmplifier,
    String smokeSound,
    float soundVolume,
    float soundPitch,
    Map<AddictionLevel, Integer> minSmoked,
    Map<AddictionLevel, Integer> withdrawalMinutes,
    Map<AddictionLevel, List<WithdrawalEffect>> withdrawalEffects,
    int criticalDamageIntervalSeconds,
    double criticalDamageAmount,
    int effectRefreshTicks,
    int actionbarIntervalSeconds,
    int decayMinutesPerCigarette,
    SmokingSettings smoking,
    RecipeSettings recipe
) {

    public CigaretteConfig {
        minSmoked = Map.copyOf(minSmoked);
        withdrawalMinutes = Map.copyOf(withdrawalMinutes);
        withdrawalEffects = Map.copyOf(withdrawalEffects);
        cooldownSeconds = Math.clamp(cooldownSeconds, 0, 3600);
        criticalDamageIntervalSeconds = Math.clamp(criticalDamageIntervalSeconds, 1, 3600);
        criticalDamageAmount = Math.clamp(criticalDamageAmount, 0.0, 20.0);
        effectRefreshTicks = Math.clamp(effectRefreshTicks, 20, 200);
        actionbarIntervalSeconds = Math.clamp(actionbarIntervalSeconds, 1, 60);
        decayMinutesPerCigarette = Math.max(0, decayMinutesPerCigarette);
        soundVolume = (float) Math.clamp(soundVolume, 0.0, 4.0);
        soundPitch = (float) Math.clamp(soundPitch, 0.5, 2.0);
    }

    public int minSmoked(AddictionLevel level) {
        return minSmoked.getOrDefault(level, Integer.MAX_VALUE);
    }

    public int withdrawalMinutes(AddictionLevel level) {
        return withdrawalMinutes.getOrDefault(level, 0);
    }

    public List<WithdrawalEffect> withdrawalEffects(AddictionLevel level) {
        return withdrawalEffects.getOrDefault(level, List.of());
    }

    public static CigaretteConfig load(FileConfiguration cfg, Consumer<String> warn) {
        var thresholds = new java.util.EnumMap<AddictionLevel, Integer>(AddictionLevel.class);
        var minutes = new java.util.EnumMap<AddictionLevel, Integer>(AddictionLevel.class);
        var effects = new java.util.EnumMap<AddictionLevel, List<WithdrawalEffect>>(AddictionLevel.class);

        for (var level : AddictionLevel.values()) {
            if (level == AddictionLevel.NONE) continue;
            var path = "addiction." + level.name().toLowerCase(Locale.ROOT);
            thresholds.put(level, Math.max(1, cfg.getInt(path + ".min-smoked", defaultMinSmoked(level))));
            minutes.put(level, Math.max(1, cfg.getInt(path + ".withdrawal-minutes", defaultMinutes(level))));
            effects.put(level, WithdrawalEffect.parse(cfg.getConfigurationSection(path + ".effects"), warn));
        }

        return new CigaretteConfig(
            safeMaterial(cfg.getString("cigarette.material", "PAPER"), warn),
            cfg.getInt("cigarette.custom-model-data", 1001),
            cfg.getString("cigarette.name", "<gradient:#ff6b6b:#ee5a24>сигарета"),
            cfg.getInt("cooldown-seconds", 5),
            cfg.getInt("effects.strength.duration", 200),
            cfg.getInt("effects.strength.amplifier", 0),
            cfg.getInt("effects.regeneration.duration", 200),
            cfg.getInt("effects.regeneration.amplifier", 0),
            cfg.getString("sound.name", "entity.generic.eat"),
            (float) cfg.getDouble("sound.volume", 0.8),
            (float) cfg.getDouble("sound.pitch", 0.7),
            thresholds,
            minutes,
            effects,
            cfg.getInt("addiction.critical.damage-interval-seconds", 37),
            cfg.getDouble("addiction.critical.damage-amount", 1.0),
            cfg.getInt("withdrawal.effect-refresh-ticks", 40),
            cfg.getInt("withdrawal.actionbar-interval-seconds", 4),
            cfg.getInt("addiction.decay-minutes-per-cigarette", 0),
            SmokingSettings.load(cfg, warn),
            RecipeSettings.load(cfg, warn)
        );
    }

    private static int defaultMinSmoked(AddictionLevel level) {
        return switch (level) {
            case LIGHT -> 11;
            case MEDIUM -> 31;
            case HEAVY -> 61;
            case CRITICAL -> 101;
            case NONE -> 0;
        };
    }

    private static int defaultMinutes(AddictionLevel level) {
        return switch (level) {
            case LIGHT -> 32;
            case MEDIUM -> 27;
            case HEAVY -> 22;
            case CRITICAL -> 18;
            case NONE -> 0;
        };
    }

    private static Material safeMaterial(String name, Consumer<String> warn) {
        if (name == null) return Material.PAPER;
        var material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            warn.accept("invalid material '" + name + "', using PAPER");
            return Material.PAPER;
        }
        return material;
    }
}
