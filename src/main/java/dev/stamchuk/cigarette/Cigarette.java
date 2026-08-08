package dev.stamchuk.cigarette;

import dev.stamchuk.cigarette.command.CigaretteCommand;
import dev.stamchuk.cigarette.config.CigaretteConfig;
import dev.stamchuk.cigarette.listener.CraftingListener;
import dev.stamchuk.cigarette.listener.SmokingListener;
import dev.stamchuk.cigarette.recipe.CigaretteRecipe;
import dev.stamchuk.cigarette.repository.DataRepository;
import dev.stamchuk.cigarette.service.SmokingService;
import dev.stamchuk.cigarette.smoke.SmokeSessionManager;
import dev.stamchuk.cigarette.task.WithdrawalTask;
import dev.stamchuk.cigarette.util.Cooldown;
import dev.stamchuk.cigarette.util.Schedulers;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class Cigarette extends JavaPlugin {

    private static final long AUTOSAVE_MINUTES = 5L;

    private DataRepository repository;
    private SmokingService smokingService;
    private Cooldown smokeCooldown;
    private SmokeSessionManager smokeSessions;
    private CigaretteRecipe recipe;
    private WithdrawalTask withdrawalTask;
    private ScheduledTask autoSaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = CigaretteConfig.load(getConfig(), message -> getLogger().warning(message));

        repository = new DataRepository(this);
        smokingService = new SmokingService(this, repository, config);
        smokeCooldown = new Cooldown(Duration.ofSeconds(config.cooldownSeconds()));

        for (var player : getServer().getOnlinePlayers()) {
            smokingService.loadPlayer(player.getUniqueId());
        }

        smokeSessions = new SmokeSessionManager(this, smokingService);
        smokeSessions.start();

        withdrawalTask = new WithdrawalTask(this, smokingService);
        withdrawalTask.start();

        recipe = new CigaretteRecipe(this, smokingService);
        recipe.register();

        autoSaveTask = Schedulers.asyncTimer(this, this::autoSave, AUTOSAVE_MINUTES, TimeUnit.MINUTES);

        registerCommands();
        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(
            new SmokingListener(this, smokingService, smokeSessions, smokeCooldown), this
        );
        pluginManager.registerEvents(new CraftingListener(smokingService, recipe.key()), this);
    }

    @Override
    public void onDisable() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        if (withdrawalTask != null) {
            withdrawalTask.stop();
            withdrawalTask = null;
        }
        if (smokeSessions != null) {
            smokeSessions.stop();
            smokeSessions = null;
        }
        if (recipe != null) {
            recipe.unregister();
            recipe = null;
        }
        HandlerList.unregisterAll(this);
        Schedulers.cancelAll(this);
        if (smokingService != null) smokingService.saveAll();
    }

    private void autoSave() {
        try {
            smokingService.saveAll();
        } catch (RuntimeException failure) {
            getLogger().warning("autosave failed: " + failure.getMessage());
        }
    }

    private void reload() {
        reloadConfig();
        var config = CigaretteConfig.load(getConfig(), message -> getLogger().warning(message));
        smokingService.updateConfig(config);
        smokeCooldown.setDuration(Duration.ofSeconds(config.cooldownSeconds()));
        recipe.register();
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
            new CigaretteCommand(smokingService, this::reload).register(event.registrar())
        );
    }
}
