package cn.gmzc.skincache.api;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * GMZCSkinCache 皮肤服务 API（编译用 stub）。
 * 运行时由 GMZCSkinCache 插件通过 ServicesManager 提供实际实现。
 * 此 stub 不打进插件 jar。
 */
public interface PlayerSkinService {
    /** 按 UUID 从缓存应用皮肤到头颅物品。缓存不存在或纹理无效时返回 false。 */
    boolean apply(SkullMeta meta, UUID playerId);

    /** 对在线玩家使用实时资料应用皮肤，回退到 UUID 缓存。 */
    boolean apply(SkullMeta meta, Player player);
}
