package mc233.fun.kbbstoper.core.platform;

/**
 * 命令发送者抽象。控制台与玩家都实现这个接口，
 * 命令处理器只依赖它，因此 core 里不出现任何平台类型。
 */
public interface PlatformSender {

    /** 发送一行消息，实现方负责颜色码转换。 */
    void sendMessage(String message);

    boolean hasPermission(String permission);

    /** 用于日志与提示的名称。 */
    String getName();

    /** 是否为玩家（控制台返回 false）。 */
    boolean isPlayer();

    /**
     * 转为玩家对象。
     *
     * @return 当 {@link #isPlayer()} 为 false 时返回 null
     */
    PlatformPlayer asPlayer();
}
