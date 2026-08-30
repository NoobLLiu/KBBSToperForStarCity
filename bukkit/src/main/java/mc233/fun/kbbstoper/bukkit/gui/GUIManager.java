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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
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

        if (holder instanceof AnvilInput) {
            handleAnvilClick(ev, p, (AnvilInput) holder);
            return;
        }
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
                GUI.openBindingAnvil(p);
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
                GUI.openCheckAnvil(p);
                break;
            case DELETE:
                p.closeInventory();
                GUI.openDeleteAnvil(p);
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
    // 铁砧输入
    // ---------------------------------------------------------------

    private void handleAnvilClick(InventoryClickEvent ev, Player p, AnvilInput anvil) {
        int raw = ev.getRawSlot();
        if (raw == 2) {
            ev.setCancelled(true);
            // 优先用 PrepareAnvilEvent 解析并缓存的改名文本(最可靠); 拿不到再退回结果物品名
            String text = anvil.getEntered();
            if ((text == null || text.isBlank()) && ev.getCurrentItem() != null
                    && ev.getCurrentItem().getType() != org.bukkit.Material.AIR
                    && ev.getCurrentItem().hasItemMeta()
                    && ev.getCurrentItem().getItemMeta().hasDisplayName()) {
                text = ev.getCurrentItem().getItemMeta().getDisplayName();
            }
            if (text != null && !text.isBlank()) {
                final String t = text.trim();
                // 延迟到下一 tick 执行 confirm：避免在点击处理过程中关闭/重开库存，
                // 导致客户端误以为输入界面被重新打开（"假重开"），点输出槽没反应。
                Bukkit.getScheduler().runTask(
                        mc233.fun.kbbstoper.bukkit.KBBSToperBukkit.getInstance(),
                        () -> {
                            if (p.isOnline()
                                    && p.getOpenInventory().getTopInventory().getHolder() == anvil) {
                                anvil.confirm(p, t);
                            }
                        });
            }
            return;
        }
        // 改名槽 / 输入槽允许放取物品，保证改名栏可用；空掉时补回占位物品
        ev.setCancelled(false);
        if (raw == 0 || raw == 1) {
            Bukkit.getScheduler().runTask(
                    mc233.fun.kbbstoper.bukkit.KBBSToperBukkit.getInstance(), anvil::ensureGuide);
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

    /**
     * 铁砧准备结果：把改名费用清零并给出结果物品。
     * 不清零的话玩家经验不足时客户端结果槽为空，点了没反应、无法提交。
     */
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof AnvilInput) {
            ((AnvilInput) holder).onPrepare(event);
        }
    }

    /** 铁砧关闭时还原临时补齐的经验，保证输入过程不消耗玩家经验。 */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof AnvilInput) {
            ((AnvilInput) holder).restoreXp();
            ((AnvilInput) holder).cleanupLeaked();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        MenuRouter.clear(event.getPlayer().getUniqueId());
    }
}
