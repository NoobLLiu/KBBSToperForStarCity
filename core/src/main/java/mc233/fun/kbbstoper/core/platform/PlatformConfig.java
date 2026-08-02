package mc233.fun.kbbstoper.core.platform;

import java.util.List;

/**
 * YAML 配置读取抽象。
 * Bukkit 用 YamlConfiguration，Nukkit 用 cn.nukkit.utils.Config，
 * 两者路径语法都是点号分隔，所以 core 可以直接用同一套键名。
 */
public interface PlatformConfig {

    String getString(String path);

    String getString(String path, String def);

    int getInt(String path);

    int getInt(String path, int def);

    boolean getBoolean(String path);

    boolean getBoolean(String path, boolean def);

    /** 不存在时返回空列表，不返回 null。 */
    List<String> getStringList(String path);

    boolean isSection(String path);

    /**
     * 取子节点。
     *
     * @return 不存在时返回 null
     */
    PlatformConfigSection getSection(String path);

    /** 键是否存在。 */
    boolean contains(String path);
}
