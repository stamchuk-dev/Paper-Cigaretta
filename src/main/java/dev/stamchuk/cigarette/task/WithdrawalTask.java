package dev.stamchuk.cigarette.task;

import dev.stamchuk.cigarette.effect.WithdrawalEffect;
import dev.stamchuk.cigarette.model.AddictionLevel;
import dev.stamchuk.cigarette.service.SmokingService;
import dev.stamchuk.cigarette.util.Msg;
import dev.stamchuk.cigarette.util.Schedulers;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WithdrawalTask {

    private static final long PERIOD_TICKS = 20L;

    private final Plugin plugin;
    private final SmokingService smokingService;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ScheduledTask task;
    private long ticks;

    public WithdrawalTask(Plugin plugin, SmokingService smokingService) {
        this.plugin = plugin;
        this.smokingService = smokingService;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        task = Schedulers.globalTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    public void stop() {
        running.set(false);
        var current = task;
        task = null;
        if (current != null) current.cancel();
    }

    private void tick() {
        if (!running.get()) return;
        ticks += PERIOD_TICKS;
        var config = smokingService.config();
        var announce = ticks % (config.actionbarIntervalSeconds() * PERIOD_TICKS) == 0;

        for (var player : plugin.getServer().getOnlinePlayers()) {
            var uuid = player.getUniqueId();
            var level = smokingService.getAddictionLevel(uuid);
            if (level == AddictionLevel.NONE || !smokingService.isInWithdrawal(uuid)) continue;

            var effects = config.withdrawalEffects(level);
            Schedulers.onEntity(plugin, player, () -> apply(player, level, effects, announce), null);
        }
    }

    private void apply(Player player, AddictionLevel level, List<WithdrawalEffect> effects, boolean announce) {
        if (!player.isOnline()) return;
        var config = smokingService.config();
        for (var effect : effects) {
            player.addPotionEffect(new PotionEffect(
                effect.type(), config.effectRefreshTicks(), effect.amplifier(), false, false, true
            ));
        }
        if (announce) sendActionBar(player, level);
        if (level == AddictionLevel.CRITICAL && smokingService.consumeDamageInterval(player.getUniqueId())) {
            player.damage(config.criticalDamageAmount());
        }
    }

    private void sendActionBar(Player player, AddictionLevel level) {
        var template = switch (level) {
            case LIGHT -> "<gray>хочется покурить...";
            case MEDIUM -> "<yellow>сильно хочется покурить!";
            case HEAVY -> "<red>ты не можешь без сигарет!";
            case CRITICAL -> "<dark_red><bold>⚠ критическая ломка ⚠";
            case NONE -> "";
        };
        if (!template.isEmpty()) player.sendActionBar(Msg.of(template));
    }
}
