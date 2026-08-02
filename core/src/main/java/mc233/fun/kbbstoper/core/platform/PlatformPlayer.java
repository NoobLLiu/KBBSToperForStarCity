package mc233.fun.kbbstoper.core.platform;

import java.util.UUID;

/** 在线玩家抽象。 */
public interface PlatformPlayer extends PlatformSender {

    UUID getUniqueId();

    /**
     * 本玩家是否能看见目标玩家（用于隐身插件场景下过滤广播）。
     * Nukkit 与 Bukkit 都提供该能力。
     */
    boolean canSee(PlatformPlayer other);

    /**
     * 打开绑定界面。
     * Bukkit 实现发送可点击的补全消息，Nukkit 实现弹出输入表单。
     */
    void openBindingInput();

    /** 打开主界面（Bukkit 为箱子 GUI，Nukkit 为表单）。 */
    void openMainMenu();
}
