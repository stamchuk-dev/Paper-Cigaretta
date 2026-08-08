package dev.stamchuk.cigarette.repository;

import dev.stamchuk.cigarette.model.PlayerSmokingData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Level;

public final class DataRepository {

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public DataRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playerdata.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized PlayerSmokingData find(UUID uuid) {
        var path = "players." + uuid;
        if (!data.contains(path)) return null;
        return new PlayerSmokingData(
            uuid,
            Math.max(0, data.getInt(path + ".total-smoked", 0)),
            Math.max(0L, data.getLong(path + ".last-smoke-time", 0L))
        );
    }

    public synchronized void save(PlayerSmokingData psd) {
        writeEntry(psd);
        flush();
    }

    public synchronized void saveAll(Collection<PlayerSmokingData> allData) {
        if (allData.isEmpty()) return;
        for (var psd : allData) {
            writeEntry(psd);
        }
        flush();
    }

    public synchronized void delete(UUID uuid) {
        data.set("players." + uuid, null);
        flush();
    }

    private void writeEntry(PlayerSmokingData psd) {
        var path = "players." + psd.uuid();
        data.set(path + ".total-smoked", psd.totalSmoked());
        data.set(path + ".last-smoke-time", psd.lastSmokeTime());
    }

    private void flush() {
        var parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().severe("failed to create data folder for playerdata.yml");
            return;
        }
        try {
            data.save(file);
        } catch (IOException failure) {
            plugin.getLogger().log(Level.SEVERE, "failed to save playerdata.yml", failure);
        }
    }
}
