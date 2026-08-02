package mc233.fun.kbbstoper.nukkit;

import cn.nukkit.Player;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.nukkit.form.FormRouter;

import java.util.UUID;

/** 在线玩家包装。界面走基岩版表单。 */
public class NukkitPlayer extends NukkitSender implements PlatformPlayer {

    public NukkitPlayer(Player handle) {
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
        if (!(other instanceof NukkitPlayer)) {
            return true;
        }
        return player().canSee(((NukkitPlayer) other).player());
    }

    @Override
    public void openBindingInput() {
        FormRouter.openBindingForm(player());
    }

    @Override
    public void openMainMenu() {
        FormRouter.openMainForm(player());
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
        if (!(o instanceof NukkitPlayer)) {
            return false;
        }
        return getUniqueId().equals(((NukkitPlayer) o).getUniqueId());
    }

    @Override
    public int hashCode() {
        return getUniqueId().hashCode();
    }
}
