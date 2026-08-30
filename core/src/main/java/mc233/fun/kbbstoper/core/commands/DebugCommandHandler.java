package mc233.fun.kbbstoper.core.commands;

import mc233.fun.kbbstoper.core.CommandHandler;
import mc233.fun.kbbstoper.core.Crawler;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.Poster;
import mc233.fun.kbbstoper.core.Reward;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.platform.PlatformSender;
import mc233.fun.kbbstoper.core.sql.SQLManager;

import java.util.Arrays;
import java.util.List;

/** /bt debug &lt;clear|status|simulate&gt; —— OP 调试指令。 */
public class DebugCommandHandler implements CommandHandler {

    @Override
    public void handle(PlatformSender sender, String[] args) {
        if (!sender.hasPermission("bbstoper.debug")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.NOPERMISSION.getString());
            return;
        }
        if (!sender.isPlayer()) {
            sender.sendMessage(Message.PREFIX.getString() + Message.PLAYERCMD.getString());
            return;
        }
        PlatformPlayer player = sender.asPlayer();
        if (args.length < 2) {
            sender.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
            sender.sendMessage(Message.PREFIX.getString() + Message.HELP_DEBUG.getString());
            return;
        }
        switch (args[1].toLowerCase()) {
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

    /** clear: 清空自身顶帖状态(重置已领次数/上次领奖日/HP上限, 并清除顶帖记录)。 */
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
        poster.setMaxhp(Option.REWARD_VAL_HP_BASE.getInt());
        SQLManager.getSQLer().updatePoster(poster);
        SQLManager.getSQLer().clearTopStates(poster.getBbsname());

        // 管理员调整后主动刷新: 向 MGactivity 同步生命上限并把当日成长/经验倍率归位 1.0
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
        msg += " 奖励等级: " + poster.getRewardlevel() + "/" + Reward.MAX_REWARD_LEVEL;
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
            return Arrays.asList("clear", "status", "simulate");
        }
        return null;
    }
}
