package mc233.fun.kbbstoper.core.platform;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

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

    /** 取所有键路径(深层), 用于配置迁移。 */
    Set<String> getKeys(boolean deep);

    /** 取原始值(可能为 null / Integer / String / Boolean / List)。 */
    Object getRaw(String path);

    /** 设置键值。 */
    void set(String path, Object value);

    /** 写回磁盘。 */
    void save(File file) throws IOException;
}
