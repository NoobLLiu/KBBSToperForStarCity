package mc233.fun.kbbstoper.core.platform;

import java.util.List;
import java.util.Set;

/** 配置子节点，语义与 {@link PlatformConfig} 一致。 */
public interface PlatformConfigSection {

    Set<String> getKeys();

    String getString(String path);

    String getString(String path, String def);

    int getInt(String path);

    int getInt(String path, int def);

    boolean getBoolean(String path);

    boolean getBoolean(String path, boolean def);

    List<String> getStringList(String path);

    boolean isList(String path);

    boolean isSection(String path);

    PlatformConfigSection getSection(String path);

    boolean contains(String path);
}
