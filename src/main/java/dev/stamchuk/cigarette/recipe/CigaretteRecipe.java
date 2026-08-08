package dev.stamchuk.cigarette.recipe;

import dev.stamchuk.cigarette.service.SmokingService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.Plugin;

public final class CigaretteRecipe {

    private final Plugin plugin;
    private final SmokingService smokingService;
    private final NamespacedKey key;
    private boolean registered;

    public CigaretteRecipe(Plugin plugin, SmokingService smokingService) {
        this.plugin = plugin;
        this.smokingService = smokingService;
        this.key = new NamespacedKey(plugin, "cigarette");
    }

    public NamespacedKey key() {
        return key;
    }

    public void register() {
        unregister();

        var settings = smokingService.config().recipe();
        if (!settings.enabled()) return;

        var recipe = new ShapedRecipe(key, smokingService.createCigarette(settings.resultAmount()));
        recipe.shape("#.#", "#.#", "#.#");
        recipe.setIngredient('#', plain(settings.paperIngredient()));
        recipe.setIngredient('.', plain(settings.fillerIngredient()));
        recipe.setCategory(CraftingBookCategory.MISC);

        if (!plugin.getServer().addRecipe(recipe, true)) {
            plugin.getLogger().warning("failed to register cigarette crafting recipe");
            return;
        }
        registered = true;
    }

    public void unregister() {
        if (!registered) return;
        plugin.getServer().removeRecipe(key, true);
        registered = false;
    }

    private RecipeChoice plain(Material material) {
        return new RecipeChoice.ExactChoice(ItemStack.of(material));
    }
}
