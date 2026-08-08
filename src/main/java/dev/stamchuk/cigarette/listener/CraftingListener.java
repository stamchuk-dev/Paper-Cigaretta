package dev.stamchuk.cigarette.listener;

import dev.stamchuk.cigarette.service.SmokingService;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public final class CraftingListener implements Listener {

    private final SmokingService smokingService;
    private final NamespacedKey recipeKey;

    public CraftingListener(SmokingService smokingService, NamespacedKey recipeKey) {
        this.smokingService = smokingService;
        this.recipeKey = recipeKey;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        var inventory = event.getInventory();
        if (inventory.getResult() == null) return;
        if (isOwnRecipe(event.getRecipe())) return;
        if (!containsCigarette(inventory.getMatrix())) return;
        inventory.setResult(null);
    }

    private boolean isOwnRecipe(Recipe recipe) {
        return recipe instanceof Keyed keyed && recipeKey.equals(keyed.getKey());
    }

    private boolean containsCigarette(ItemStack[] matrix) {
        for (var item : matrix) {
            if (smokingService.isCigarette(item)) return true;
        }
        return false;
    }
}
