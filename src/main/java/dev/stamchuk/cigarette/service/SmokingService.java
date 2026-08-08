package dev.stamchuk.cigarette.service;

import dev.stamchuk.cigarette.config.CigaretteConfig;
import dev.stamchuk.cigarette.model.AddictionLevel;
import dev.stamchuk.cigarette.model.PlayerSmokingData;
import dev.stamchuk.cigarette.repository.DataRepository;
import dev.stamchuk.cigarette.util.Msg;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SmokingService {

    private static final NamespacedKey ITEM_MODEL = new NamespacedKey("cigarette", "cigarette");
    private static final String ITEM_ID = "cigarette";

    private final DataRepository repository;
    private final NamespacedKey itemIdKey;
    private final Map<UUID, PlayerSmokingData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDamageTime = new ConcurrentHashMap<>();
    private volatile CigaretteConfig config;

    public SmokingService(Plugin plugin, DataRepository repository, CigaretteConfig config) {
        this.repository = repository;
        this.config = config;
        this.itemIdKey = new NamespacedKey(plugin, "item_id");
    }

    public CigaretteConfig config() {
        return config;
    }

    public void updateConfig(CigaretteConfig config) {
        this.config = config;
    }

    public void loadPlayer(UUID uuid) {
        var stored = repository.find(uuid);
        cache.putIfAbsent(uuid, stored != null ? stored : PlayerSmokingData.empty(uuid));
    }

    public boolean isLoaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public PlayerSmokingData get(UUID uuid) {
        return cache.get(uuid);
    }

    public boolean recordSmoke(UUID uuid) {
        var updated = cache.computeIfPresent(uuid, (ignored, old) -> old.smoked());
        if (updated == null) return false;
        lastDamageTime.remove(uuid);
        return true;
    }

    public void savePlayer(UUID uuid) {
        var data = cache.remove(uuid);
        lastDamageTime.remove(uuid);
        if (data != null) repository.save(data);
    }

    public void saveAll() {
        repository.saveAll(cache.values());
    }

    public void resetAddiction(UUID uuid) {
        var empty = PlayerSmokingData.empty(uuid);
        cache.put(uuid, empty);
        lastDamageTime.remove(uuid);
        repository.save(empty);
    }

    public int effectiveSmoked(UUID uuid) {
        var data = cache.get(uuid);
        if (data == null) return 0;
        var decayMinutes = config.decayMinutesPerCigarette();
        if (decayMinutes <= 0 || data.lastSmokeTime() == 0) return data.totalSmoked();
        var decayed = minutesSince(data.lastSmokeTime()) / decayMinutes;
        return (int) Math.max(0, data.totalSmoked() - decayed);
    }

    public AddictionLevel getAddictionLevel(UUID uuid) {
        var smoked = effectiveSmoked(uuid);
        var cfg = config;
        if (smoked >= cfg.minSmoked(AddictionLevel.CRITICAL)) return AddictionLevel.CRITICAL;
        if (smoked >= cfg.minSmoked(AddictionLevel.HEAVY)) return AddictionLevel.HEAVY;
        if (smoked >= cfg.minSmoked(AddictionLevel.MEDIUM)) return AddictionLevel.MEDIUM;
        if (smoked >= cfg.minSmoked(AddictionLevel.LIGHT)) return AddictionLevel.LIGHT;
        return AddictionLevel.NONE;
    }

    public int getWithdrawalMinutes(AddictionLevel level) {
        return config.withdrawalMinutes(level);
    }

    public boolean isInWithdrawal(UUID uuid) {
        var data = cache.get(uuid);
        if (data == null || data.lastSmokeTime() == 0) return false;
        var level = getAddictionLevel(uuid);
        if (level == AddictionLevel.NONE) return false;
        return minutesSince(data.lastSmokeTime()) >= getWithdrawalMinutes(level);
    }

    private static long minutesSince(long epochMillis) {
        return Math.max(0L, System.currentTimeMillis() - epochMillis) / 60_000L;
    }

    public boolean consumeDamageInterval(UUID uuid) {
        var intervalMillis = config.criticalDamageIntervalSeconds() * 1000L;
        var now = System.currentTimeMillis();
        var previous = lastDamageTime.putIfAbsent(uuid, now);
        if (previous == null) return false;
        if (now - previous < intervalMillis) return false;
        return lastDamageTime.replace(uuid, previous, now);
    }

    public void forget(UUID uuid) {
        cache.remove(uuid);
        lastDamageTime.remove(uuid);
    }

    public boolean isCigarette(ItemStack item) {
        if (item == null || item.getType() != config.itemMaterial()) return false;
        if (!item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        if (ITEM_ID.equals(meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING))) return true;
        if (meta.hasItemModel()) return ITEM_MODEL.equals(meta.getItemModel());
        return meta.hasCustomModelData() && meta.getCustomModelData() == config.customModelData();
    }

    public ItemStack createCigarette(int amount) {
        var cfg = config;
        var item = ItemStack.of(cfg.itemMaterial(), amount);
        item.editMeta(meta -> {
            meta.itemName(Msg.of(cfg.itemName()));
            meta.setItemModel(ITEM_MODEL);
            meta.setCustomModelData(cfg.customModelData());
            meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, ITEM_ID);
        });
        applyConsumable(item);
        return item;
    }

    @SuppressWarnings("UnstableApiUsage")
    public void applyConsumable(ItemStack item) {
        var smoking = config.smoking();
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
            .consumeSeconds(smoking.durationSeconds())
            .animation(smoking.animation())
            .hasConsumeParticles(false)
            .sound(Key.key("minecraft", "block.fire.ambient"))
            .build());
    }

    @SuppressWarnings("UnstableApiUsage")
    public boolean needsConsumable(ItemStack item) {
        var existing = item.getData(DataComponentTypes.CONSUMABLE);
        return existing == null || existing.consumeSeconds() != config.smoking().durationSeconds();
    }

    public boolean upgradeLegacy(ItemStack item) {
        if (item == null || item.getType() != config.itemMaterial() || !item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        var tagged = ITEM_ID.equals(meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING));
        var byModel = meta.hasItemModel() && ITEM_MODEL.equals(meta.getItemModel());
        var byModelData = meta.hasCustomModelData() && meta.getCustomModelData() == config.customModelData();
        if (!tagged && !byModel && !byModelData) return false;

        var changed = false;
        if (!tagged) {
            item.editMeta(updated -> {
                updated.setItemModel(ITEM_MODEL);
                updated.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, ITEM_ID);
            });
            changed = true;
        }
        if (needsConsumable(item)) {
            applyConsumable(item);
            changed = true;
        }
        return changed;
    }
}
