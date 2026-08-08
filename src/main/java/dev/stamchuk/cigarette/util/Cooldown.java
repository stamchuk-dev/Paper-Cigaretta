package dev.stamchuk.cigarette.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Cooldown {

    private final Map<UUID, Instant> expiry = new ConcurrentHashMap<>();
    private volatile Duration duration;

    public Cooldown(Duration duration) {
        this.duration = duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public boolean available(UUID uuid) {
        var exp = expiry.get(uuid);
        return exp == null || !Instant.now().isBefore(exp);
    }

    public boolean tryConsume(UUID uuid) {
        var now = Instant.now();
        var next = now.plus(duration);
        while (true) {
            var current = expiry.get(uuid);
            if (current != null && now.isBefore(current)) return false;
            if (current == null) {
                if (expiry.putIfAbsent(uuid, next) == null) return true;
                continue;
            }
            if (expiry.replace(uuid, current, next)) return true;
        }
    }

    public Duration remaining(UUID uuid) {
        var exp = expiry.get(uuid);
        if (exp == null) return Duration.ZERO;
        var left = Duration.between(Instant.now(), exp);
        return left.isNegative() ? Duration.ZERO : left;
    }

    public void reset(UUID uuid) {
        expiry.remove(uuid);
    }
}
