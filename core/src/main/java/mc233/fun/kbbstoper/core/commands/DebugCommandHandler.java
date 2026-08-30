package mc233.fun.kbbstoper.core.commands;

import mc233.fun.kbbstoper.core.CommandHandler;
import mc233.fun.kbbstoper.core.Crawler;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.Poster;
import mc233.fun.kbbstoper.core.Reward;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.platform.PlatformSender;
import mc233.fun.kbbstoper.core.sql.SQLManager;

import java.util.Arrays;
import java.util.List;

/** /bt debug &lt;clear|status|simulate|open&gt; —— OP 调试指令。 */
public class DebugCommandHandler implements CommandHandler {

    /** 调试追踪总开关：开启后检测/发奖每一步都输出到控制台与 OP 玩家。 */
    public static boolean debugTrace = false;

    @Override
    public void handle(PlatformSender sender, String[] args) {
        if (!sender.hasPermission("bbstoper.debug")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.NOPERMISSION.getString());
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_DEBUG.getString());
            return;
        }
        String sub = args[1].toLowerCase();
        // open 允许控制台与玩家执行，不要求在线玩家
        if ("open".equals(sub)) {
            debugTrace = !debugTrace;
            sender.sendMessage(Message.PREFIX.getString()
                    + KBBSToperCore.platform().colorize(
                            debugTrace ? "&a调试追踪已开启: 检测/发奖过程将输出到控制台与 OP 玩家"
                                    : "&7调试追踪已关闭"));
            return;
        }
        if (!sender.isPlayer()) {
            sender.sendMessage(Message.PREFIX.getString() + Message.PLAYERCMD.getString());
            return;
        }
        PlatformPlayer player = sender.asPlayer();
        switch (sub) {
            case "clear":
                clear(player, sender);
                break;
            case "status":
                status(player, sender);
                break;
            case "simulate":
                simulate(player, sender);
                break;
            default:
                sender.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
                sender.sendMessage(Message.PREFIX.getString() + Message.HELP_DEBUG.getString());
        }
    }

    /**
     * 调试追踪日志：仅在 {@link #debugTrace} 开启时输出。
     * 写到控制台，并转发给所有在线且持有 bbstoper.debug 权限的玩家。
     */
    public static void trace(String msg) {
        if (!debugTrace || msg == null) {
            return;
        }
        KBBSToperCore.logger().info("[debug] " + msg);
        String colored = KBBSToperCore.platform().colorize("&7[debug] " + msg);
        for (PlatformPlayer p : KBBSToperCore.platform().getOnlinePlayers()) {
            if (p.hasPermission("bbstoper.debug")) {
                p.sendMessage(colored);
            }
        }
    }

    /** clear: 清空自身顶帖状态(重置奖励等级/已领次数/上次领奖日/HP上限, 并清除顶帖记录)。 */
    private void clear(PlatformPlayer player, PlatformSender sender) {
        String uuid = player.getUniqueId().toString();
        Poster poster = SQLManager.getSQLer().getPoster(uuid);
        if (poster == null) {
            sender.sendMessage(Message.PREFIX.getString() + Message.NOTBOUND.getString());
            return;
        }
        poster.setRewardbefore("");
        poster.setRewardtime(0);
        poster.setRewardlevel(0);
        poster.setMaxhp(Reward.hpBase());
        SQLManager.getSQLer().updatePoster(poster);
        SQLManager.getSQLer().clearTopStates(poster.getBbsname());

        // 管理员调整后主动刷新: 向 MGactivity 同步生命上限并把成长倍率归位 1.0
        Reward.refreshRewardState(poster, true);

        sender.sendMessage(Message.PREFIX.getString() + Message.DEBUG_CLEAR.getString());
    }

    /** status: 查看自身顶帖状态。 */
    private void status(PlatformPlayer player, PlatformSender sender) {
        String uuid = player.getUniqueId().toString();
        Poster poster = SQLManager.getSQLer().getPoster(uuid);
        if (poster == null) {
            sender.sendMessage(Message.PREFIX.getString() + Message.NOTBOUND.getString());
            return;
        }
        String msg = Message.DEBUG_STATUS.getString()
                .replace("%BBSNAME%", poster.getBbsname() == null ? "" : poster.getBbsname())
                .replace("%REWARDBEFORE%", poster.getRewardbefore() == null ? "" : poster.getRewardbefore())
                .replace("%REWARDTIME%", String.valueOf(poster.getRewardtime()))
                .replace("%MAXHP%", String.valueOf(poster.getMaxhp()));
        // 追加诊断信息: 便于排查"配置 hp-base 仍是旧值(30)"导致的血量不同步
        msg += " &7[基准 hp-base=" + Option.REWARD_VAL_HP_BASE.getInt()
                + ", 上限 hp-cap=" + Option.REWARD_VAL_HP_CAP.getInt() + "]";
        sender.sendMessage(Message.PREFIX.getString() + msg);
    }

    /** simulate: 手动模拟一次"检测到你顶帖", 走完整发奖逻辑(不抓取网络)。 */
    private void simulate(PlatformPlayer player, PlatformSender sender) {
        String uuid = player.getUniqueId().toString();
        Poster poster = SQLManager.getSQLer().getPoster(uuid);
        if (poster == null) {
            sender.sendMessage(Message.PREFIX.getString() + Message.NOTBOUND.getString());
            return;
        }
        Crawler fake = new Crawler(false);
        fake.simulateTopPost(player);
        sender.sendMessage(Message.PREFIX.getString() + Message.DEBUG_SIMULATE.getString());
    }

    @Override
    public List<String> tabComplete(PlatformSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("clear", "status", "simulate", "open");
        }
        return null;
    }
}
