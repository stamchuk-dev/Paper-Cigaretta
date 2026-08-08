package dev.stamchuk.cigarette.model;

public enum AddictionLevel {

    NONE, LIGHT, MEDIUM, HEAVY, CRITICAL;

    public String displayName() {
        return switch (this) {
            case NONE -> "нет";
            case LIGHT -> "лёгкая";
            case MEDIUM -> "средняя";
            case HEAVY -> "сильная";
            case CRITICAL -> "критическая";
        };
    }
}
