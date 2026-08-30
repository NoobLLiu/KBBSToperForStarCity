package mc233.fun.kbbstoper.bukkit.gui;

import mc233.fun.kbbstoper.bukkit.BukkitSender;
import mc233.fun.kbbstoper.core.GuiAction;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.MenuRouter;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Option;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * 箱子界面点击分发（v2 最终稿）。
 * 主菜单与子界面动作走 {@link GuiAction} 枚举；铁砧输入单独处理。
 */
public class GUIManager implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent ev) {
        if (!(ev.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) ev.getWhoClicked();
        InventoryHolder holder = p.getOpenInventory().getTopInventory().getHolder();

        if (!(holder instanceof GUI.Holder)) {
            return;
        }

        ev.setCancelled(true);
        GUI.Holder gui = (GUI.Holder) holder;
        GuiAction action = gui.getActions().get(ev.getRawSlot());
        if (action == null) {
            return;
        }
        dispatch(p, gui, action);
    }

    private void dispatch(Player p, GUI.Holder gui, GuiAction action) {
        switch (action) {
            case CLOSE:
                p.closeInventory();
                break;
            case OPEN_MAIN:
            case BACK:
                GUI.openMain(p);
                break;
            case BINDING:
                p.closeInventory();
                promptCommand(p, Message.GUI2_BINDING_HINT, "/bt binding ");
                break;
            case REWARD:
                p.closeInventory();
                KBBSToperCore.cli().onCommand(BukkitSender.of(p), new String[]{"reward"});
                break;
            case MY_RECORDS:
                GUI.openRecords(p, 1);
                break;
            case PROMO_POST:
                sendPromo(p);
                break;
            case TOP:
                GUI.openTop(p, 1);
                break;
            case RULES:
                GUI.openRules(p);
                break;
            case MY_STATUS:
                GUI.openStatus(p);
                break;
            case MANAGE:
                GUI.openManage(p);
                break;
            case TEST_REWARD:
                GUI.openTestReward(p);
                break;
            case TEST_NORMAL:
                p.closeInventory();
                KBBSToperCore.cli().onCommand(BukkitSender.of(p), new String[]{"testreward", "normal"});
                break;
            case TEST_PEAK:
                p.closeInventory();
                KBBSToperCore.cli().onCommand(BukkitSender.of(p), new String[]{"testreward", "peak"});
                break;
            case TEST_MAX:
                p.closeInventory();
                KBBSToperCore.cli().onCommand(BukkitSender.of(p), new String[]{"testreward", "max"});
                break;
            case LIST:
                p.closeInventory();
                KBBSToperCore.cli().onCommand(BukkitSender.of(p), new String[]{"list"});
                break;
            case CHECK:
                p.closeInventory();
                promptCommand(p, Message.GUI2_CHECK_HINT, "/bt check bbsid ");
                break;
            case DELETE:
                p.closeInventory();
                promptCommand(p, Message.GUI2_DELETE_HINT, "/bt delete ");
                break;
            case RELOAD:
                p.closeInventory();
                KBBSToperCore.cli().onCommand(BukkitSender.of(p), new String[]{"reload"});
                break;
            case DEBUG:
                GUI.openDebug(p);
                break;
            case DEBUG_CLEAR:
                p.closeInventory();
                KBBSToperCore.cli().onCommand(BukkitSender.of(p), new String[]{"debug", "clear"});
                break;
            case DEBUG_STATUS:
                p.closeInventory();
                KBBSToperCore.cli().onCommand(BukkitSender.of(p), new String[]{"debug", "status"});
                break;
            case DEBUG_SIMULATE:
                p.closeInventory();
                KBBSToperCore.cli().onCommand(BukkitSender.of(p), new String[]{"debug", "simulate"});
                break;
            case HELP:
                GUI.openHelp(p);
                break;
            case PREV_PAGE:
            case NEXT_PAGE:
                page(p, gui.getKind(), action);
                break;
            default:
                p.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
        }
    }

    private void page(Player p, String kind, GuiAction dir) {
        MenuRouter.PageState st = MenuRouter.state(p.getUniqueId());
        if ("records".equals(kind)) {
            int page = st.recordPage + (dir == GuiAction.NEXT_PAGE ? 1 : -1);
            GUI.openRecords(p, page);
        } else if ("top".equals(kind)) {
            int page = st.topPage + (dir == GuiAction.NEXT_PAGE ? 1 : -1);
            GUI.openTop(p, page);
        }
    }

    // ---------------------------------------------------------------
    // 宣传帖
    // ---------------------------------------------------------------

    /** 发一条可点击的宣传帖链接消息（JAVA 特点：点击即打开）。 */
    private void sendPromo(Player p) {
        String url = "https://" + Option.WEBSITE.getString() + "/thread-"
                + Option.BBS_URL.getString() + "-1-1.html";
        Message.CLICKPOSTICON.getStringList()
                .forEach(line -> p.sendMessage(line.replace("%PAGE%", url)));
        TextComponent msg = new TextComponent("▶ §a点击打开本服宣传帖 §7◀");
        msg.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§7点击打开宣传帖链接").create()
        ));
        msg.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        p.spigot().sendMessage(msg);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        MenuRouter.clear(event.getPlayer().getUniqueId());
    }

    /**
     * 废弃 Java 铁砧输入后, 点击绑定/检查/删除按钮改为在聊天栏提示玩家用指令完成。
     * 发送一行说明 + 一个可点击按钮(SUGGEST_COMMAND 把指令填入聊天框, 玩家补全参数后回车)。
     */
    public static void promptCommand(Player p, Message hint, String suggest) {
        p.sendMessage(Message.PREFIX.getString() + hint.getString());
        TextComponent c = new TextComponent("\u25b6 \u00a7a\u70b9\u51fb\u586b\u5165\u6307\u4ee4 \u00a77\u25c0");
        c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("\u00a77\u70b9\u51fb\u628b\u6307\u4ee4\u586b\u5165\u804a\u5929\u6846").create()));
        c.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggest));
        p.spigot().sendMessage(c);
    }
}
