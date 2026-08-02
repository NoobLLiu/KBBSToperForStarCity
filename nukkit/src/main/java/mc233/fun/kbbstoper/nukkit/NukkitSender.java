package mc233.fun.kbbstoper.nukkit;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.platform.PlatformSender;

/** 控制台等非玩家发送者。 */
public class NukkitSender implements PlatformSender {

    protected final CommandSender handle;

    public NukkitSender(CommandSender handle) {
        this.handle = handle;
    }

    /** 按实际类型包装，玩家会得到 {@link NukkitPlayer}。 */
    public static PlatformSender of(CommandSender sender) {
        if (sender instanceof Player) {
            return new NukkitPlayer((Player) sender);
        }
        return new NukkitSender(sender);
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
