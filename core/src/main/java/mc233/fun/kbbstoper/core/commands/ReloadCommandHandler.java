package mc233.fun.kbbstoper.core.commands;

import mc233.fun.kbbstoper.core.CommandHandler;
import mc233.fun.kbbstoper.core.ConfigManager;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.Util;
import mc233.fun.kbbstoper.core.platform.PlatformSender;
import mc233.fun.kbbstoper.core.sql.SQLManager;

/** /bt reload。 */
public class ReloadCommandHandler implements CommandHandler {

    private final ConfigManager configManager;

    public ReloadCommandHandler(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public void handle(PlatformSender sender, String[] args) {
        if (!sender.hasPermission("bbstoper.reload")) {
            sender.sendMessage(Message.PREFIX.getString() + Message.NOPERMISSION.getString());
            return;
        }
        Option.load(KBBSToperCore.platform());
        configManager.reloadConfig();
        Message.load(configManager);
        SQLManager.initializeSQLer();
        SQLManager.startTimingReconnect();
        Util.startAutoReward();
        // 平台自己的界面配置（例如 Bukkit 的 gui.yml）由平台钩子重载
        ReloadHook.Holder.get().onReload();
        sender.sendMessage(Message.PREFIX.getString() + Message.RELOAD.getString());
    }
}
