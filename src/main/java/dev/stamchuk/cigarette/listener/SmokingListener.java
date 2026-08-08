package dev.stamchuk.cigarette.listener;

import dev.stamchuk.cigarette.effect.WithdrawalEffects;
import dev.stamchuk.cigarette.service.SmokingService;
import dev.stamchuk.cigarette.smoke.SmokeSessionManager;
import dev.stamchuk.cigarette.util.Cooldown;
import dev.stamchuk.cigarette.util.Msg;
import dev.stamchuk.cigarette.util.Schedulers;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class SmokingListener implements Listener {

    private final Plugin plugin;
    private final SmokingService smokingService;
    private final SmokeSessionManager sessions;
    private final Cooldown cooldown;

    public SmokingListener(Plugin plugin, SmokingService smokingService,
                           SmokeSessionManager sessions, Cooldown cooldown) {
        this.plugin = plugin;
        this.smokingService = smokingService;
        this.sessions = sessions;
        this.cooldown = cooldown;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            smokingService.forget(event.getUniqueId());
            return;
        }
        smokingService.loadPlayer(event.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoginDenied(PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.ALLOWED) return;
        smokingService.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (!smokingService.isLoaded(player.getUniqueId())) {
            Schedulers.async(plugin, () -> smokingService.loadPlayer(player.getUniqueId()));
        }
        upgradeHeldItems(player);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        var action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        var player = event.getPlayer();
        var item = player.getInventory().getItemInMainHand();
        if (!smokingService.isCigarette(item)) return;

        var block = event.getClickedBlock();
        if (block != null && block.getType().isInteractable() && !player.isSneaking()) return;

        var uuid = player.getUniqueId();
        if (!player.hasPermission("cigarette.use")) {
            deny(event, player, "<gradient:#ff6b6b:#ee5a24>✗</gradient> <gray>нет прав");
            return;
        }
        if (!smokingService.isLoaded(uuid)) {
            deny(event, player, "<gray>данные ещё загружаются...");
            return;
        }
        if (!cooldown.available(uuid)) {
            deny(event, player, "<dark_gray>» <gray>подожди <white>"
                + cooldown.remaining(uuid).toSeconds() + "</white>с");
            return;
        }
        if (sessions.isSmoking(uuid)) return;

        if (smokingService.needsConsumable(item)) {
            smokingService.applyConsumable(item);
        }
        sessions.begin(player, smokingService.config().smoking().durationTicks());
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        var player = event.getPlayer();
        if (!smokingService.isCigarette(event.getItem())) return;

        event.setCancelled(true);
        var uuid = player.getUniqueId();
        sessions.cancel(uuid);

        if (!smokingService.isLoaded(uuid) || !cooldown.tryConsume(uuid)) return;
        if (!smokingService.recordSmoke(uuid)) {
            cooldown.reset(uuid);
            return;
        }

        var hand = event.getHand() != null ? event.getHand() : EquipmentSlot.HAND;
        var used = player.getInventory().getItem(hand);
        if (smokingService.isCigarette(used)) {
            used.setAmount(used.getAmount() - 1);
        }
        finishSmoking(player);
    }

    @EventHandler
    public void onStopUsing(PlayerStopUsingItemEvent event) {
        var player = event.getPlayer();
        if (!sessions.isSmoking(player.getUniqueId())) return;
        if (!smokingService.isCigarette(event.getItem())) return;
        sessions.interrupt(player, "<gray>затяжка прервана");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (sessions.isEmpty() || !event.hasChangedPosition()) return;
        var player = event.getPlayer();
        if (!sessions.isSmoking(player.getUniqueId())) return;
        if (!smokingService.config().smoking().interruptOnMove()) return;
        sessions.interrupt(player, "<gradient:#ff6b6b:#ee5a24>✗</gradient> <gray>нельзя курить на ходу");
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        var stack = event.getItem().getItemStack();
        if (!smokingService.upgradeLegacy(stack)) return;
        event.getItem().setItemStack(stack);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        sessions.cancel(uuid);
        cooldown.reset(uuid);
        Schedulers.async(plugin, () -> smokingService.savePlayer(uuid));
    }

    private void deny(PlayerInteractEvent event, Player player, String message) {
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        player.sendActionBar(Msg.of(message));
    }

    private void finishSmoking(Player player) {
        var config = smokingService.config();

        player.getWorld().playSound(
            player.getLocation(), config.smokeSound(), config.soundVolume(), config.soundPitch()
        );
        spawnParticles(player, Particle.SMOKE, 15, 0.3, 0.2);

        Schedulers.onEntityDelayed(plugin, player,
            () -> spawnParticles(player, Particle.CAMPFIRE_SIGNAL_SMOKE, 12, 0.6, 0.1), null, 12L);

        player.addPotionEffect(new PotionEffect(
            PotionEffectType.STRENGTH, config.strengthDuration(), config.strengthAmplifier(), false, true, true
        ));
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.REGENERATION, config.regenDuration(), config.regenAmplifier(), false, true, true
        ));

        WithdrawalEffects.clear(player);
        player.sendActionBar(Msg.of("<gradient:#00b894:#00cec9>✦</gradient> <gray>выдох..."));
    }

    private void spawnParticles(Player player, Particle particle, int count, double forward, double down) {
        var loc = player.getEyeLocation();
        loc.add(loc.getDirection().multiply(forward)).subtract(0, down, 0);
        player.getWorld().spawnParticle(particle, loc, count, 0.12, 0.12, 0.12, 0.02);
    }

    private void upgradeHeldItems(Player player) {
        var inventory = player.getInventory();
        for (var slot = 0; slot < inventory.getSize(); slot++) {
            var item = inventory.getItem(slot);
            if (smokingService.upgradeLegacy(item)) inventory.setItem(slot, item);
        }
    }
}
