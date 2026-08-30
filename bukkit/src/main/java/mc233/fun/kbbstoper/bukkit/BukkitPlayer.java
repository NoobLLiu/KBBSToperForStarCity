package mc233.fun.kbbstoper.bukkit;

import mc233.fun.kbbstoper.bukkit.gui.BedrockForm;
import mc233.fun.kbbstoper.bukkit.gui.GUI;
import mc233.fun.kbbstoper.bukkit.gui.GUIManager;
import mc233.fun.kbbstoper.core.Message;
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
        // Java 版已废弃铁砧输入, 改为在聊天栏提示用指令绑定
        GUIManager.promptCommand(player(), Message.GUI2_BINDING_HINT, "/bt binding ");
    }

    @Override
    public void openMainMenu() {
        // 经 Geyser 接入的基岩版玩家收到原生表单；其余走 Java 箱子界面
        if (BedrockForm.isBedrock(player())) {
            BedrockForm.openMain(player());
        } else {
            GUI.openMain(player());
        }
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
