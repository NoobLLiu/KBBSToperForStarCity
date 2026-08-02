package mc233.fun.kbbstoper.bukkit;

import mc233.fun.kbbstoper.core.platform.PlatformConfigSection;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Set;

/** ConfigurationSection 适配。 */
public class BukkitConfigSection implements PlatformConfigSection {

    private final ConfigurationSection handle;

    public BukkitConfigSection(ConfigurationSection handle) {
        this.handle = handle;
    }

    public ConfigurationSection handle() {
        return handle;
    }

    @Override
    public Set<String> getKeys() {
        return handle.getKeys(false);
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
    public boolean isList(String path) {
        return handle.isList(path);
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
}
