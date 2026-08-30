package mc233.fun.kbbstoper.bukkit;

import mc233.fun.kbbstoper.core.platform.PlatformConfig;
import mc233.fun.kbbstoper.core.platform.PlatformConfigSection;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/** YamlConfiguration 适配。 */
public class BukkitConfig implements PlatformConfig {

    private final FileConfiguration handle;

    public BukkitConfig(FileConfiguration handle) {
        this.handle = handle;
    }

    public FileConfiguration handle() {
        return handle;
    }

    @Override
    public String getString(String path) {
        return handle.getString(path);
    }

    @Override
    public String getString(String path, String def) {
        return handle.getString(path, def);
    }

    @Override
    public int getInt(String path) {
        return handle.getInt(path);
    }

    @Override
    public int getInt(String path, int def) {
        return handle.getInt(path, def);
    }

    @Override
    public boolean getBoolean(String path) {
        return handle.getBoolean(path);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return handle.getBoolean(path, def);
    }

    @Override
    public List<String> getStringList(String path) {
        return handle.getStringList(path);
    }

    @Override
    public boolean isSection(String path) {
        return handle.isConfigurationSection(path);
    }

    @Override
    public PlatformConfigSection getSection(String path) {
        ConfigurationSection sec = handle.getConfigurationSection(path);
        return sec == null ? null : new BukkitConfigSection(sec);
    }

    @Override
    public boolean contains(String path) {
        return handle.contains(path);
    }

    @Override
    public Set<String> getKeys(boolean deep) {
        return handle.getKeys(deep);
    }

    @Override
    public Object getRaw(String path) {
        return handle.get(path);
    }

    @Override
    public void set(String path, Object value) {
        handle.set(path, value);
    }

    @Override
    public void save(File file) throws IOException {
        handle.save(file);
    }
}
