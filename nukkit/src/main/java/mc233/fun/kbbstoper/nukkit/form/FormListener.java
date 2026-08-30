package mc233.fun.kbbstoper.nukkit.form;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.form.response.FormResponseCustom;
import cn.nukkit.form.response.FormResponseSimple;
import mc233.fun.kbbstoper.core.CLI;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.nukkit.NukkitSender;

import java.util.List;

/** 表单回应处理（v2 最终稿，多级表单）。 */
public class FormListener implements Listener {

    @EventHandler
    public void onFormResponded(PlayerFormRespondedEvent event) {
        Player player = event.getPlayer();
        FormRouter.PendingForm pending = FormRouter.consume(player.getUniqueId(), event.getFormID());
        if (pending == null) {
            // 不是本插件发出的表单
            return;
        }
        if (event.wasClosed() || event.getResponse() == null) {
            return;
        }

        switch (pending.type) {
            case FormRouter.PendingForm.TYPE_MAIN:
                handleMain(player, pending, event);
                break;
            case FormRouter.PendingForm.TYPE_BINDING:
                handleBinding(player, event);
                break;
            case FormRouter.PendingForm.TYPE_PAGED:
                handlePaged(player, pending, event);
                break;
            case FormRouter.PendingForm.TYPE_MENU:
                handleMenu(player, pending, event);
                break;
            case FormRouter.PendingForm.TYPE_INPUT:
                handleInput(player, pending, event);
                break;
            default:
                // 只读表单，无需处理
                break;
        }
    }

    // ---------------------------------------------------------------
    // 主界面
    // ---------------------------------------------------------------

    private void handleMain(Player player, FormRouter.PendingForm pending, PlayerFormRespondedEvent event) {
        if (!(event.getResponse() instanceof FormResponseSimple)) {
            return;
        }
        int clicked = ((FormResponseSimple) event.getResponse()).getClickedButtonId();
        List<FormAction> actions = pending.buttons;
        if (actions == null || clicked < 0 || clicked >= actions.size()) {
            return;
        }

        CLI cli = KBBSToperCore.cli();
        if (cli == null) {
            return;
        }

        switch (actions.get(clicked)) {
            case BINDING:
                FormRouter.openBindingForm(player);
                break;
            case REWARD:
                cli.onCommand(NukkitSender.of(player), new String[]{"reward"});
                break;
            case MY_RECORDS:
                FormRouter.openRecordsForm(player, 1);
                break;
            case MY_STATUS:
                FormRouter.openStatusForm(player);
                break;
            case TOP:
                FormRouter.openTopForm(player, 1);
                break;
            case POST:
                FormRouter.openPostForm(player);
                break;
            case RULES:
                FormRouter.openRulesForm(player);
                break;
            case MANAGE:
                FormRouter.openManageForm(player);
                break;
            default:
                player.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
        }
    }

    // ---------------------------------------------------------------
    // 绑定输入
    // ---------------------------------------------------------------

    private void handleBinding(Player player, PlayerFormRespondedEvent event) {
        if (!(event.getResponse() instanceof FormResponseCustom)) {
            return;
        }
        // 0 号元素是说明标签，输入框是 1 号
        String input = ((FormResponseCustom) event.getResponse()).getInputResponse(1);
        if (input == null || input.trim().isEmpty()) {
            player.sendMessage(Message.PREFIX.getString()
                    + Message.FORM_BINDING_EMPTY.getString("&c论坛ID不能为空。"));
            return;
        }
        String id = input.trim();
        CLI cli = KBBSToperCore.cli();
        if (cli == null) {
            return;
        }
        // 走与命令完全相同的路径，因此二次确认、冷却、重复绑定检查全部生效。
        cli.onCommand(NukkitSender.of(player), new String[]{"binding", id});

        // 提交后重新弹一次输入框，方便玩家完成二次确认（第二次明确提示"再输入一遍用户名称确认"）
        KBBSToperCore.scheduler().runLater(() -> {
            if (player.isOnline() && needConfirm(player)) {
                FormRouter.openBindingConfirmForm(player);
            }
        }, 20);
    }

    /** 缓存里还留着待确认的 ID，说明这次是第一次提交。 */
    private boolean needConfirm(Player player) {
        CLI cli = KBBSToperCore.cli();
        if (cli == null) {
            return false;
        }
        return cli.getCache().get(player.getUniqueId().toString()) != null;
    }

    // ---------------------------------------------------------------
    // 分页列表（记录 / 排行）
    // ---------------------------------------------------------------

    private void handlePaged(Player player, FormRouter.PendingForm pending, PlayerFormRespondedEvent event) {
        if (!(event.getResponse() instanceof FormResponseSimple)) {
            return;
        }
        int clicked = ((FormResponseSimple) event.getResponse()).getClickedButtonId();
        List<FormAction> actions = pending.buttons;
        if (actions == null || clicked < 0 || clicked >= actions.size()) {
            return;
        }
        FormAction action = actions.get(clicked);
        String kind = pending.pagedKind;
        switch (action) {
            case PREV_PAGE:
                if ("top".equals(kind)) {
                    FormRouter.openTopForm(player, pending.page - 1);
                } else {
                    FormRouter.openRecordsForm(player, pending.page - 1);
                }
                break;
            case NEXT_PAGE:
                if ("top".equals(kind)) {
                    FormRouter.openTopForm(player, pending.page + 1);
                } else {
                    FormRouter.openRecordsForm(player, pending.page + 1);
                }
                break;
            case BACK:
                FormRouter.openMainForm(player);
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------
    // 菜单表单（管理 / 测试 / 调试）
    // ---------------------------------------------------------------

    private void handleMenu(Player player, FormRouter.PendingForm pending, PlayerFormRespondedEvent event) {
        if (!(event.getResponse() instanceof FormResponseSimple)) {
            return;
        }
        int clicked = ((FormResponseSimple) event.getResponse()).getClickedButtonId();
        List<FormAction> actions = pending.buttons;
        if (actions == null || clicked < 0 || clicked >= actions.size()) {
            return;
        }

        CLI cli = KBBSToperCore.cli();
        if (cli == null) {
            return;
        }

        switch (actions.get(clicked)) {
            case TEST_REWARD:
                FormRouter.openTestForm(player);
                break;
            case TEST_NORMAL:
                cli.onCommand(NukkitSender.of(player), new String[]{"testreward", "normal"});
                break;
            case TEST_INCENTIVE:
                cli.onCommand(NukkitSender.of(player), new String[]{"testreward", "incentive"});
                break;
            case TEST_OFFDAY:
                cli.onCommand(NukkitSender.of(player), new String[]{"testreward", "offday"});
                break;
            case LIST:
                cli.onCommand(NukkitSender.of(player), new String[]{"list"});
                break;
            case CHECK:
                FormRouter.openInputForm(player, "check",
                        Message.FORM2_MANAGE_CHECK.getString("检查绑定"), "论坛ID");
                break;
            case DELETE:
                FormRouter.openInputForm(player, "delete",
                        Message.FORM2_MANAGE_DELETE.getString("删除玩家数据"), "玩家名");
                break;
            case RELOAD:
                cli.onCommand(NukkitSender.of(player), new String[]{"reload"});
                break;
            case DEBUG:
                FormRouter.openDebugForm(player);
                break;
            case DEBUG_CLEAR:
                cli.onCommand(NukkitSender.of(player), new String[]{"debug", "clear"});
                break;
            case DEBUG_STATUS:
                cli.onCommand(NukkitSender.of(player), new String[]{"debug", "status"});
                break;
            case DEBUG_SIMULATE:
                cli.onCommand(NukkitSender.of(player), new String[]{"debug", "simulate"});
                break;
            case BACK:
                if ("manage".equals(pending.backTo)) {
                    FormRouter.openManageForm(player);
                } else {
                    FormRouter.openMainForm(player);
                }
                break;
            default:
                player.sendMessage(Message.PREFIX.getString() + Message.INVALID.getString());
        }
    }

    // ---------------------------------------------------------------
    // 参数输入（检查 / 删除）
    // ---------------------------------------------------------------

    private void handleInput(Player player, FormRouter.PendingForm pending, PlayerFormRespondedEvent event) {
        if (!(event.getResponse() instanceof FormResponseCustom)) {
            return;
        }
        String input = ((FormResponseCustom) event.getResponse()).getInputResponse(1);
        if (input == null || input.trim().isEmpty()) {
            player.sendMessage(Message.PREFIX.getString() + Message.FORM2_INPUT_EMPTY.getString("&c输入不能为空。"));
            return;
        }
        CLI cli = KBBSToperCore.cli();
        if (cli == null) {
            return;
        }
        String id = input.trim();
        if ("check".equals(pending.inputAction)) {
            cli.onCommand(NukkitSender.of(player), new String[]{"check", "bbsid", id});
        } else if ("delete".equals(pending.inputAction)) {
            cli.onCommand(NukkitSender.of(player), new String[]{"delete", id});
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        FormRouter.clear(event.getPlayer().getUniqueId());
    }
}
