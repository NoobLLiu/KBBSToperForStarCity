package mc233.fun.kbbstoper.bukkit.gui;

import mc233.fun.kbbstoper.bukkit.BukkitSender;
import mc233.fun.kbbstoper.core.CLI;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.Util;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 临时聊天监听，用来收集玩家输入的论坛 ID。
 * 只有 Bukkit 端需要——Nukkit 端用表单输入框，没有这套 hack。
 */
public class IDListener extends RegisteredListener implements Listener, EventExecutor {

    public static final Object lock = new Object();
    public static final Map<UUID, IDListener> map = new HashMap<>();

    private final UUID uid;
    private boolean state;

    @Override
    public void execute(Listener listener, Event event) throws EventException {
        callEvent(event);
    }

    @Override
    public void callEvent(Event event) throws EventException {
        if (event instanceof AsyncPlayerChatEvent) {
            onPlayerChat((AsyncPlayerChatEvent) event);
        }
    }

    public IDListener(UUID uuid) {
        super(null, null, EventPriority.HIGH,
                mc233.fun.kbbstoper.bukkit.KBBSToperBukkit.getInstance(), false);
        this.uid = uuid;
        this.state = false;
    }

    public static void unregister(UUID uniqueId) {
        synchronized (lock) {
            Optional.ofNullable(map.get(uniqueId)).ifPresent(IDListener::unregister);
        }
    }

    public void unregister() {
        synchronized (lock) {
            AsyncPlayerChatEvent.getHandlerList().unregister((RegisteredListener) this);
            if (!map.remove(uid, this)) {
                KBBSToperCore.logger().warning(Message.FAILEDUNINSTALLMO.getString());
            }
        }
    }

    public void register() {
        for (RegisteredListener lis : AsyncPlayerChatEvent.getHandlerList().getRegisteredListeners()) {
            if (lis == this) {
                return;
            }
        }
        synchronized (lock) {
            IDListener old = map.put(uid, this);
            if (old != null && old != this) {
                old.unregister();
            }
        }
        // 两分钟后无论如何都注销，防止监听器泄漏
        new BukkitRunnable() {
            @Override
            public void run() {
                Util.enterTask();
                try {
                    unregister(uid);
                } finally {
                    Util.exitTask();
                }
            }
        }.runTaskLater(mc233.fun.kbbstoper.bukkit.KBBSToperBukkit.getInstance(), 2 * 60 * 20);
        AsyncPlayerChatEvent.getHandlerList().register(this);
    }

    public UUID getUid() {
        return this.uid;
    }

    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().getUniqueId().equals(uid)) {
            return;
        }
        Player player = event.getPlayer();
        String msg = event.getMessage();
        event.setCancelled(true);

        List<String> cancelkeywords = Option.GUI_CANCELKEYWORDS.getStringList();
        if (cancelkeywords.contains(msg)) {
            unregister();
            CLI cli = KBBSToperCore.cli();
            if (cli != null) {
                cli.getCache().remove(player.getUniqueId().toString());
            }
            player.sendMessage(Message.PREFIX.getString() + Message.CANCELED.getString());
            return;
        }

        List<String> list = new ArrayList<>(Arrays.asList(msg.split("\\s+")));
        list.add(0, "binding");
        String[] args = list.toArray(new String[0]);
        CLI cli = KBBSToperCore.cli();
        if (cli != null) {
            cli.onCommand(BukkitSender.of(player), args);
        }

        if (state) {
            // 第二次进入说明二次确认已完成
            unregister();
        } else {
            state = true;
        }
    }
}
