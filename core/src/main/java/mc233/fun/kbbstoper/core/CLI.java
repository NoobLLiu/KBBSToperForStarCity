package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.commands.BindingCommandHandler;
import mc233.fun.kbbstoper.core.commands.CheckCommandHandler;
import mc233.fun.kbbstoper.core.commands.DebugCommandHandler;
import mc233.fun.kbbstoper.core.commands.DeleteCommandHandler;
import mc233.fun.kbbstoper.core.commands.HelpCommandHandler;
import mc233.fun.kbbstoper.core.commands.ListCommandHandler;
import mc233.fun.kbbstoper.core.commands.ReloadCommandHandler;
import mc233.fun.kbbstoper.core.commands.RewardCommandHandler;
import mc233.fun.kbbstoper.core.commands.TestRewardCommandHandler;
import mc233.fun.kbbstoper.core.commands.TopCommandHandler;
import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.platform.PlatformSender;
import mc233.fun.kbbstoper.core.sql.SQLManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 命令分发。平台模块把自己的命令回调转成这里的 onCommand。 */
public class CLI {

    private final ConfigManager configManager;

    /** 绑定二次确认的临时缓存，键为玩家 UUID 字符串。 */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /** 查询冷却记录（list/top 共用）。 */
    private final Map<UUID, Long> queryrecord = new ConcurrentHashMap<>();

    /** 玩家手动触发领奖/检查冷却记录（reward 专用，独立于查询冷却）。 */
    private final Map<UUID, Long> manualrecord = new ConcurrentHashMap<>();

    private final Map<String, CommandHandler> handlers = new LinkedHashMap<>();

    private static CLI instance;

    public Map<String, String> getCache() {
        return cache;
    }

    public static CLI getInstance() {
        return instance;
    }

    public CLI(ConfigManager configManager) {
        instance = this;
        this.configManager = configManager;

        handlers.put("help", new HelpCommandHandler());
        handlers.put("binding", new BindingCommandHandler(cache));
        handlers.put("reward", new RewardCommandHandler(manualrecord));
        handlers.put("testreward", new TestRewardCommandHandler());
        handlers.put("list", new ListCommandHandler());
        handlers.put("top", new TopCommandHandler());
        handlers.put("check", new CheckCommandHandler());
        handlers.put("delete", new DeleteCommandHandler());
        handlers.put("debug", new DebugCommandHandler());
        handlers.put("reload", new ReloadCommandHandler(configManager));
    }

    /**
     * 处理一条命令。整个逻辑异步执行（涉及网络与数据库），
     * 需要回主线程的部分由各处自行切换。
     */
    public boolean onCommand(PlatformSender sender, String[] args) {
        KBBSToperCore.scheduler().runAsync(() -> {
            Util.enterTask();
            try {
                if (args.length == 0 && sender.isPlayer()) {
                    PlatformPlayer player = sender.asPlayer();
                    // 界面必须在主线程打开
                    KBBSToperCore.scheduler().runSync(player::openMainMenu);
                } else {
                    String key = args.length > 0 ? args[0].toLowerCase() : "help";
                    CommandHandler handler = handlers.get(key);
                    if (handler == null) {
                        sender.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
                    } else {
                        handler.handle(sender, args);
                    }
                }
            } finally {
                Util.exitTask();
            }
        });
        return true;
    }

    /** 同步执行一条命令，供表单回调这类已在主线程且需要立即反馈的场景使用。 */
    public void handleDirect(PlatformSender sender, String[] args) {
        String key = args.length > 0 ? args[0].toLowerCase() : "help";
        CommandHandler handler = handlers.get(key);
        if (handler == null) {
            sender.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
            return;
        }
        handler.handle(sender, args);
    }

    public List<String> onTabComplete(PlatformSender sender, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            String a = args[0].toLowerCase();
            handlers.forEach((k, v) -> {
                if (k.startsWith(a) && sender.hasPermission("bbstoper." + k)) {
                    list.add(k);
                }
            });
            return list;
        }
        if (args.length > 1) {
            CommandHandler handler = handlers.get(args[0].toLowerCase());
            if (handler != null) {
                List<String> r = handler.tabComplete(sender, args);
                return r == null ? Collections.emptyList() : r;
            }
        }
        return Collections.emptyList();
    }

    /** 剩余查询冷却（秒）。 */
    public double getQueryCooldown(UUID uuid) {
        long last = queryrecord.getOrDefault(uuid, 0L);
        int coolMs = Option.BBS_QUERYCOOLDOWN.getInt() * 1000;
        double remain = (coolMs - (System.currentTimeMillis() - last)) / 1000.0;
        return Math.max(0, remain);
    }

    public void recordQuery(UUID uuid, long timeMs) {
        queryrecord.put(uuid, timeMs);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
