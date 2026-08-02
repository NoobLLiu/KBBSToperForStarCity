package mc233.fun.kbbstoper.nukkit;

import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;
import mc233.fun.kbbstoper.core.platform.PlatformConfig;
import mc233.fun.kbbstoper.core.platform.PlatformConfigSection;

import java.util.Collections;
import java.util.List;

public class NukkitConfigAdapter implements PlatformConfig {

    private final Config handle;

    public NukkitConfigAdapter(Config handle) {
        this.handle = handle;
    }

    public Config handle() {
        return handle;
    }

    @Override
    public String getString(String path) {
        // Nukkit 取不到时返回空串，这里统一成 null，与 core 的判空逻辑对齐
        if (!handle.exists(path)) {
            return null;
        }
        String s = handle.getString(path);
        return (s == null || s.isEmpty()) ? null : s;
    }

    @Override
    public String getString(String path, String def) {
        String s = getString(path);
        return s == null ? def : s;
    }

    @Override
    public int getInt(String path) {
        return handle.getInt(path);
    }

    @Override
    public int getInt(String path, int def) {
        return handle.exists(path) ? handle.getInt(path) : def;
    }

    @Override
    public boolean getBoolean(String path) {
        return handle.getBoolean(path);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return handle.exists(path) ? handle.getBoolean(path) : def;
    }

    @Override
    public List<String> getStringList(String path) {
        List<String> l = handle.getStringList(path);
        return l == null ? Collections.emptyList() : l;
    }

    @Override
    public boolean isSection(String path) {
        return handle.isSection(path);
    }

    @Override
    public PlatformConfigSection getSection(String path) {
        if (!handle.isSection(path)) {
            return null;
        }
        ConfigSection sec = handle.getSection(path);
        return sec == null ? null : new NukkitConfigSectionAdapter(sec);
    }

    @Override
    public boolean contains(String path) {
        return handle.exists(path);
    }
}
