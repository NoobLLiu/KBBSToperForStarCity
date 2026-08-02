package mc233.fun.kbbstoper.bukkit;

import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.platform.PlatformSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** 控制台等非玩家发送者。 */
public class BukkitSender implements PlatformSender {

    protected final CommandSender handle;

    public BukkitSender(CommandSender handle) {
        this.handle = handle;
    }

    /** 按实际类型包装，玩家会得到 {@link BukkitPlayer}。 */
    public static PlatformSender of(CommandSender sender) {
        if (sender instanceof Player) {
            return new BukkitPlayer((Player) sender);
        }
        return new BukkitSender(sender);
    }

    public CommandSender handle() {
        return handle;
    }

    @Override
    public void sendMessage(String message) {
        handle.sendMessage(message);
    }

    @Override
    public boolean hasPermission(String permission) {
        return handle.hasPermission(permission);
    }

    @Override
    public String getName() {
        return handle.getName();
    }

    @Override
    public boolean isPlayer() {
        return false;
    }

    @Override
    public PlatformPlayer asPlayer() {
        return null;
    }
}
