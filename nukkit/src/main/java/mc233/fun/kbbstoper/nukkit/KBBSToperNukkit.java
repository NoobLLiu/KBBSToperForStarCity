package mc233.fun.kbbstoper.nukkit;

import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.plugin.PluginBase;
import mc233.fun.kbbstoper.core.BindingSession;
import mc233.fun.kbbstoper.core.CLI;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Reminder;
import mc233.fun.kbbstoper.nukkit.form.FormListener;

public class KBBSToperNukkit extends PluginBase implements Listener {

    private static KBBSToperNukkit instance;
    private NukkitPlatform platform;

    public static KBBSToperNukkit getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        platform = new NukkitPlatform(this);

        // 基岩端用表单收集输入，绑定流程没有需要注销的监听器
        BindingSession.Holder.set(BindingSession.NOOP);

        KBBSToperCore.init(platform);

        Server.getInstance().getPluginManager().registerEvents(this, this);
        Server.getInstance().getPluginManager().registerEvents(new FormListener(), this);

        getLogger().info(Message.ENABLE.getString());
        getLogger().info("------");
        getLogger().info("修改作者 小浩");
        getLogger().info("项目地址 https://github.com/SnowCherryServer/KBBSToper");
        getLogger().info("-----");
    }

    @Override
    public void onDisable() {
        Server.getInstance().getScheduler().cancelTask(this);
        Thread thread = new Thread(() -> {
            KBBSToperCore.shutdown();
            instance = null;
        });
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CLI cli = KBBSToperCore.cli();
        if (cli == null) {
            return true;
        }
        return cli.onCommand(NukkitSender.of(sender), args);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Reminder.onJoin(new NukkitPlayer(event.getPlayer()));
    }
}
