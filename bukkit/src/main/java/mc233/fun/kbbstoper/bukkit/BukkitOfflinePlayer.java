package mc233.fun.kbbstoper.bukkit;

import mc233.fun.kbbstoper.core.platform.PlatformOfflinePlayer;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/** 离线玩家包装。 */
public class BukkitOfflinePlayer implements PlatformOfflinePlayer {

    private final OfflinePlayer handle;

    public BukkitOfflinePlayer(OfflinePlayer handle) {
        this.handle = handle;
    }

    @Override
    public UUID getUniqueId() {
        return handle.getUniqueId();
    }

    @Override
    public String getName() {
        return handle.getName();
    }

    @Override
    public boolean isOnline() {
        return handle.isOnline();
    }

    @Override
    public PlatformPlayer getOnlinePlayer() {
        Player p = handle.getPlayer();
        return p == null ? null : new BukkitPlayer(p);
    }
}
