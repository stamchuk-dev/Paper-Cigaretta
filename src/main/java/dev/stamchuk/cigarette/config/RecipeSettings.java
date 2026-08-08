package dev.stamchuk.cigarette.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.function.Consumer;

public record RecipeSettings(
    boolean enabled,
    Material paperIngredient,
    Material fillerIngredient,
    int resultAmount
) {

    public RecipeSettings {
        resultAmount = Math.clamp(resultAmount, 1, 64);
        if (paperIngredient == null) paperIngredient = Material.PAPER;
        if (fillerIngredient == null) fillerIngredient = Material.GUNPOWDER;
    }

    public static RecipeSettings load(FileConfiguration cfg, Consumer<String> warn) {
        return new RecipeSettings(
            cfg.getBoolean("recipe.enabled", true),
            ingredient(cfg.getString("recipe.paper", "PAPER"), Material.PAPER, warn),
            ingredient(cfg.getString("recipe.filler", "GUNPOWDER"), Material.GUNPOWDER, warn),
            cfg.getInt("recipe.result-amount", 6)
        );
    }

    private static Material ingredient(String name, Material fallback, Consumer<String> warn) {
        if (name == null) return fallback;
        var material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            warn.accept("invalid recipe ingredient '" + name + "', using " + fallback.name());
            return fallback;
        }
        return material;
    }
}
