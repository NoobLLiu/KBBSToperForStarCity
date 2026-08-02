package mc233.fun.kbbstoper.nukkit;

import cn.nukkit.IPlayer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import mc233.fun.kbbstoper.core.platform.PlatformOfflinePlayer;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;

import java.util.Optional;
import java.util.UUID;

/** 离线玩家包装。 */
public class NukkitOfflinePlayer implements PlatformOfflinePlayer {

    private final IPlayer handle;

    public NukkitOfflinePlayer(IPlayer handle) {
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
        UUID uuid = handle.getUniqueId();
        if (uuid == null) {
            return null;
        }
        Optional<Player> p = Server.getInstance().getPlayer(uuid);
        return p.map(NukkitPlayer::new).orElse(null);
    }
}
