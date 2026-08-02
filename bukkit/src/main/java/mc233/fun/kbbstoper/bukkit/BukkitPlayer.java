package mc233.fun.kbbstoper.bukkit;

import mc233.fun.kbbstoper.bukkit.gui.GUI;
import mc233.fun.kbbstoper.bukkit.gui.GUIManager;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/** 在线玩家包装。 */
public class BukkitPlayer extends BukkitSender implements PlatformPlayer {

    public BukkitPlayer(Player handle) {
        super(handle);
    }

    public Player player() {
        return (Player) handle;
    }

    @Override
    public UUID getUniqueId() {
        return player().getUniqueId();
    }

    @Override
    public boolean canSee(PlatformPlayer other) {
        if (!(other instanceof BukkitPlayer)) {
            return true;
        }
        return player().canSee(((BukkitPlayer) other).player());
    }

    @Override
    public void openBindingInput() {
        // Bukkit 端沿用可点击的聊天补全 + 临时聊天监听
        GUIManager.sendBindingSuggestion(player());
    }

    @Override
    public void openMainMenu() {
        new GUI(player(), KBBSToperCore.configManager());
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public PlatformPlayer asPlayer() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BukkitPlayer)) {
            return false;
        }
        return getUniqueId().equals(((BukkitPlayer) o).getUniqueId());
    }

    @Override
    public int hashCode() {
        return getUniqueId().hashCode();
    }
}
