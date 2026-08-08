package dev.stamchuk.cigarette.model;

import java.util.UUID;

public record PlayerSmokingData(UUID uuid, int totalSmoked, long lastSmokeTime) {

    public PlayerSmokingData smoked() {
        return new PlayerSmokingData(uuid, totalSmoked + 1, System.currentTimeMillis());
    }

    public static PlayerSmokingData empty(UUID uuid) {
        return new PlayerSmokingData(uuid, 0, 0);
    }
}
