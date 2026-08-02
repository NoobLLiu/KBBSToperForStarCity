package mc233.fun.kbbstoper.bukkit.gui;

import mc233.fun.kbbstoper.bukkit.BukkitSender;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Option;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/** 箱子界面点击分发。 */
public class GUIManager implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent ev) {
        if (!(ev.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) ev.getWhoClicked();
        InventoryHolder holder = p.getOpenInventory().getTopInventory().getHolder();
        if (!(holder instanceof GUI.GUIHolder)) {
            return;
        }

        ev.setCancelled(true);
        String action = ((GUI.GUIHolder) holder).getActions().get(ev.getRawSlot());
        if (action == null) {
            return;
        }

        p.closeInventory();

        switch (action.toLowerCase()) {
            case "binding":
                sendBindingSuggestion(p);
                break;

            case "reward":
                KBBSToperCore.cli().onCommand(BukkitSender.of(p), new String[]{"reward"});
                break;

            case "top":
                KBBSToperCore.cli().onCommand(BukkitSender.of(p), new String[]{"top"});
                break;

            case "open":
                String url = "https://" + Option.WEBSITE.getString() + "/thread-"
                        + Option.BBS_URL.getString() + "-1-1.html";
                Message.CLICKPOSTICON.getStringList()
                        .forEach(line -> p.sendMessage(line.replace("%PAGE%", url)));
                break;

            default:
                p.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
        }
    }

    /**
     * 发一条可点击消息，玩家点击后聊天栏自动补全 "/bt binding "，
     * 由玩家自己补上 ID。同时挂一个临时聊天监听兜底（配置开启时）。
     */
    public static void sendBindingSuggestion(Player p) {
        if (Option.GUI_USECHATGETID.getBoolean()) {
            new IDListener(p.getUniqueId()).register();
            p.sendMessage(Message.PREFIX.getString() + Message.ENTER.getString()
                    .replace("%KEYWORD%", String.join(", ", Option.GUI_CANCELKEYWORDS.getStringList())));
        }
        TextComponent msg = new TextComponent("▶ §a点击此处绑定论坛ID §7◀");
        msg.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§7点击后自动补全 §b/bt binding ").create()
        ));
        msg.setClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND,
                "/bt binding "
        ));
        p.spigot().sendMessage(msg);
    }
}
