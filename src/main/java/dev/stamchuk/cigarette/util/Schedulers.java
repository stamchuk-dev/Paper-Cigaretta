package dev.stamchuk.cigarette.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public final class Schedulers {

    private Schedulers() {}

    public static ScheduledTask globalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return plugin.getServer().getGlobalRegionScheduler()
            .runAtFixedRate(plugin, scheduled -> task.run(), delayTicks, periodTicks);
    }

    public static ScheduledTask asyncTimer(Plugin plugin, Runnable task, long period, TimeUnit unit) {
        return plugin.getServer().getAsyncScheduler()
            .runAtFixedRate(plugin, scheduled -> task.run(), period, period, unit);
    }

    public static void async(Plugin plugin, Runnable task) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    public static void onEntity(Plugin plugin, Entity entity, Runnable task, Runnable retired) {
        if (entity.getScheduler().execute(plugin, task, retired, 1L)) return;
        if (retired != null) retired.run();
    }

    public static void onEntityDelayed(Plugin plugin, Entity entity, Runnable task,
                                       Runnable retired, long delayTicks) {
        var scheduled = entity.getScheduler().runDelayed(plugin, ignored -> task.run(), retired, delayTicks);
        if (scheduled == null && retired != null) retired.run();
    }

    public static void cancelAll(Plugin plugin) {
        var server = plugin.getServer();
        server.getGlobalRegionScheduler().cancelTasks(plugin);
        server.getAsyncScheduler().cancelTasks(plugin);
    }
}
