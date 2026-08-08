package dev.stamchuk.cigarette.config;

import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.function.Consumer;

@SuppressWarnings("UnstableApiUsage")
public record SmokingSettings(
    float durationSeconds,
    ItemUseAnimation animation,
    boolean interruptOnMove,
    boolean showProgress
) {

    private static final ItemUseAnimation DEFAULT_ANIMATION = ItemUseAnimation.TOOT_HORN;

    public SmokingSettings {
        durationSeconds = (float) Math.clamp(durationSeconds, 0.5, 30.0);
        if (animation == null) animation = DEFAULT_ANIMATION;
    }

    public int durationTicks() {
        return Math.max(1, Math.round(durationSeconds * 20.0f));
    }

    public static SmokingSettings load(FileConfiguration cfg, Consumer<String> warn) {
        return new SmokingSettings(
            (float) cfg.getDouble("smoking.duration-seconds", 3.0),
            animation(cfg.getString("smoking.animation", DEFAULT_ANIMATION.name()), warn),
            cfg.getBoolean("smoking.interrupt-on-move", true),
            cfg.getBoolean("smoking.show-progress", true)
        );
    }

    private static ItemUseAnimation animation(String name, Consumer<String> warn) {
        if (name == null) return DEFAULT_ANIMATION;
        try {
            return ItemUseAnimation.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            warn.accept("unknown smoking.animation '" + name + "', using " + DEFAULT_ANIMATION.name());
            return DEFAULT_ANIMATION;
        }
    }
}
