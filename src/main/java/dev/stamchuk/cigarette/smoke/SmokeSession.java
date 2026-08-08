package dev.stamchuk.cigarette.smoke;

import org.bukkit.Location;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class SmokeSession {

    private static final double MOVE_TOLERANCE_SQUARED = 0.04;

    private final UUID playerId;
    private final Location origin;
    private final int requiredTicks;
    private final AtomicInteger elapsedTicks = new AtomicInteger();

    public SmokeSession(UUID playerId, Location origin, int requiredTicks) {
        this.playerId = playerId;
        this.origin = origin.clone();
        this.requiredTicks = Math.max(1, requiredTicks);
    }

    public UUID playerId() {
        return playerId;
    }

    public int requiredTicks() {
        return requiredTicks;
    }

    public int elapsedTicks() {
        return elapsedTicks.get();
    }

    public int advance(int ticks) {
        return elapsedTicks.addAndGet(ticks);
    }

    public double progress() {
        return Math.clamp((double) elapsedTicks.get() / requiredTicks, 0.0, 1.0);
    }

    public boolean movedFrom(Location current) {
        if (current.getWorld() != origin.getWorld()) return true;
        return current.distanceSquared(origin) > MOVE_TOLERANCE_SQUARED;
    }
}
