package dev.stamchuk.cigarette.smoke;

import dev.stamchuk.cigarette.service.SmokingService;
import dev.stamchuk.cigarette.util.Msg;
import dev.stamchuk.cigarette.util.Schedulers;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SmokeSessionManager {

    private static final long PERIOD_TICKS = 2L;
    private static final int BAR_SEGMENTS = 10;

    private final Plugin plugin;
    private final SmokingService smokingService;
    private final Map<UUID, SmokeSession> sessions = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ScheduledTask task;

    public SmokeSessionManager(Plugin plugin, SmokingService smokingService) {
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
        sessions.clear();
    }

    public boolean begin(Player player, int requiredTicks) {
        if (!running.get()) return false;
        var session = new SmokeSession(player.getUniqueId(), player.getLocation(), requiredTicks);
        return sessions.putIfAbsent(player.getUniqueId(), session) == null;
    }

    public boolean isEmpty() {
        return sessions.isEmpty();
    }

    public boolean isSmoking(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void cancel(UUID playerId) {
        sessions.remove(playerId);
    }

    public void interrupt(Player player, String reason) {
        if (sessions.remove(player.getUniqueId()) == null) return;
        player.clearActiveItem();
        player.sendActionBar(Msg.of(reason));
    }

    private void tick() {
        if (!running.get() || sessions.isEmpty()) return;
        for (var entry : sessions.entrySet()) {
            var playerId = entry.getKey();
            var player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                sessions.remove(playerId);
                continue;
            }
            var session = entry.getValue();
            Schedulers.onEntity(plugin, player,
                () -> tickSession(player, session), () -> sessions.remove(playerId));
        }
    }

    private void tickSession(Player player, SmokeSession session) {
        if (!player.isOnline() || !sessions.containsKey(session.playerId())) return;

        if (!player.hasActiveItem() || !smokingService.isCigarette(player.getInventory().getItemInMainHand())) {
            interrupt(player, "<gray>затяжка прервана");
            return;
        }

        var settings = smokingService.config().smoking();
        if (settings.interruptOnMove() && session.movedFrom(player.getLocation())) {
            interrupt(player, "<gradient:#ff6b6b:#ee5a24>✗</gradient> <gray>нельзя курить на ходу");
            return;
        }

        session.advance((int) PERIOD_TICKS);
        spawnDrag(player);
        if (settings.showProgress()) sendProgress(player, session);
    }

    private void spawnDrag(Player player) {
        var mouth = player.getEyeLocation();
        mouth.add(mouth.getDirection().multiply(0.35)).subtract(0, 0.12, 0);
        player.getWorld().spawnParticle(Particle.SMOKE, mouth, 3, 0.03, 0.03, 0.03, 0.005);
    }

    private void sendProgress(Player player, SmokeSession session) {
        var filled = (int) Math.round(session.progress() * BAR_SEGMENTS);
        var bar = new StringBuilder("<dark_gray>[<gradient:#ff6b6b:#ee5a24>");
        bar.append("|".repeat(filled));
        bar.append("</gradient><dark_gray>");
        bar.append(".".repeat(BAR_SEGMENTS - filled));
        bar.append("]");
        player.sendActionBar(Msg.of(bar.toString()));
    }
}
