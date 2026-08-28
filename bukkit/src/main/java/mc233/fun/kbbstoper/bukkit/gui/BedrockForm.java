package mc233.fun.kbbstoper.bukkit.gui;

import mc233.fun.kbbstoper.bukkit.BukkitPlayer;
import mc233.fun.kbbstoper.bukkit.BukkitSender;
import mc233.fun.kbbstoper.bukkit.KBBSToperBukkit;
import mc233.fun.kbbstoper.core.GuiDataResolver;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.PlaceholderResolver;
import mc233.fun.kbbstoper.core.Poster;
import mc233.fun.kbbstoper.core.sql.SQLManager;
import mc233.fun.kbbstoper.core.sql.SQLer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 基岩版(Geyser)玩家的原生表单（v2 最终稿，多级表单）。
 *
 * <p>设计目标：让通过 Geyser 接入的基岩版玩家收到和 Java 版一致的多级菜单，
 * 而不是只能看 Geyser 翻译出来的箱子界面（铁砧输入在基岩版上尤其难用）。
 * 表单内容/动作与 Nukkit 端的 {@code FormRouter} 一致，所有动作都走
 * {@link mc233.fun.kbbstoper.core.CLI#onCommand} 同一路径，因此冷却、权限、二次确认全部生效。</p>
 *
 * <p>关键约束：本类<b>不直接 import</b> 任何 Geyser/Cumulus 类（全部用全限定名写在方法体内）。
 * 这样即使服务端没装 Geyser，本插件也能正常加载，Java 版玩家走 {@link GUI}，
 * 基岩版检测失败也只是回退到 Java 界面。所有 Geyser 调用都包在 try/catch 里，
 * 发送失败会自动回退到 {@link GUI#openMain}。</p>
 */
public final class BedrockForm {

    /** 基岩版每页行数（表单行高有限，比 JAVA 少）。 */
    private static final int PAGE_SIZE = 12;

    private BedrockForm() {
    }

    /** 表单动作（与 Nukkit 端 FormRouter 对齐）。 */
    private enum FormAction {
        BINDING, REWARD, MY_RECORDS, MY_STATUS, TOP, POST, RULES, MANAGE,
        PREV_PAGE, NEXT_PAGE, BACK,
        TEST_REWARD, TEST_NORMAL, TEST_INCENTIVE, TEST_OFFDAY,
        LIST, CHECK, DELETE, RELOAD, DEBUG,
        DEBUG_CLEAR, DEBUG_STATUS, DEBUG_SIMULATE,
        BACK_MANAGE
    }

    // ---------------------------------------------------------------
    // 基岩版检测
    // ---------------------------------------------------------------

    /** 该玩家是否经 Geyser 接入的基岩版。Geyser 未安装/未就绪时安全返回 false。 */
    public static boolean isBedrock(Player player) {
        try {
            org.geysermc.geyser.api.GeyserApi api = org.geysermc.geyser.api.GeyserApi.api();
            if (api == null) {
                return false;
            }
            return api.isBedrockPlayer(player.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }

    /** 把表单发给玩家；Geyser 不可用或发送失败返回 false（调用方应回退到 Java 界面）。 */
    private static boolean sendForm(Player player, org.geysermc.cumulus.form.Form form) {
        try {
            org.geysermc.geyser.api.GeyserApi api = org.geysermc.geyser.api.GeyserApi.api();
            if (api == null) {
                return false;
            }
            return api.sendForm(player.getUniqueId(), form);
        } catch (Throwable t) {
            KBBSToperCore.logger().warning("发送基岩表单失败: " + t.getMessage());
            return false;
        }
    }

    /** & 颜色码转 §（基岩版表单渲染 §）。 */
    private static String fmt(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('&', '§');
    }

    /** 在主线程执行命令（表单响应回调可能不在主线程，命令派发需回主线程）。 */
    private static void runCommand(Player player, String... args) {
        Bukkit.getScheduler().runTask(KBBSToperBukkit.getInstance(),
                () -> KBBSToperCore.cli().onCommand(BukkitSender.of(player), args));
    }

    // ---------------------------------------------------------------
    // 主界面
    // ---------------------------------------------------------------

    public static void openMain(Player player) {
        KBBSToperCore.scheduler().runAsync(() -> {
            Poster poster = GuiDataResolver.poster(new BukkitPlayer(player));
            boolean bound = poster != null && poster.getBbsname() != null && !poster.getBbsname().isBlank();
            List<String> status = GuiDataResolver.statusLines(new BukkitPlayer(player));
            boolean op = player.hasPermission("bbstoper.manage");
            KBBSToperCore.scheduler().runSync(() -> sendMainForm(player, status, bound, op));
        });
    }

    private static void sendMainForm(Player player, List<String> status, boolean bound, boolean op) {
        String title = fmt(Message.FORM_TITLE.getString("KBBSToperForStarCity")
                .replace("%PREFIX%", Message.PREFIX.getString()));

        StringBuilder content = new StringBuilder();
        for (String line : status) {
            content.append(line).append("\n");
        }

        var form = org.geysermc.cumulus.form.SimpleForm.builder()
                .title(title)
                .content(fmt(content.toString()));
        List<FormAction> actions = new ArrayList<>();

        String bindLabel = bound
                ? Message.FORM_BUTTON_REBINDING.getString("更换论坛ID")
                : Message.FORM_BUTTON_BINDING.getString("绑定论坛ID");
        form.button(fmt(bindLabel));
        actions.add(FormAction.BINDING);

        form.button(fmt(Message.FORM_BUTTON_REWARD.getString("领取顶帖奖励")));
        actions.add(FormAction.REWARD);

        form.button(fmt(Message.FORM2_BTN_MYRECORDS.getString("我的顶帖记录")));
        actions.add(FormAction.MY_RECORDS);

        form.button(fmt(Message.FORM2_BTN_STATUS.getString("我的状态")));
        actions.add(FormAction.MY_STATUS);

        form.button(fmt(Message.FORM_BUTTON_TOP.getString("顶帖排行榜")));
        actions.add(FormAction.TOP);

        form.button(fmt(Message.FORM_BUTTON_POST.getString("查看宣传帖")));
        actions.add(FormAction.POST);

        form.button(fmt(Message.FORM2_BTN_RULES.getString("活动规则")));
        actions.add(FormAction.RULES);

        if (op) {
            form.button(fmt(Message.FORM2_BTN_MANAGE.getString("管理菜单")));
            actions.add(FormAction.MANAGE);
        }

        final List<FormAction> finalActions = actions;
        form.validResultHandler((org.geysermc.cumulus.response.SimpleFormResponse resp) -> {
            int id = resp.clickedButtonId();
            if (id < 0 || id >= finalActions.size()) {
                return;
            }
            dispatch(player, finalActions.get(id));
        });
        form.closedOrInvalidResultHandler(() -> {
        });

        if (!sendForm(player, form.build())) {
            GUI.openMain(player);
        }
    }

    private static void dispatch(Player player, FormAction action) {
        switch (action) {
            case BINDING:
                openBinding(player);
                break;
            case REWARD:
                runCommand(player, "reward");
                break;
            case MY_RECORDS:
                openRecords(player, 1);
                break;
            case MY_STATUS:
                openStatus(player);
                break;
            case TOP:
                openTop(player, 1);
                break;
            case POST:
                openPost(player);
                break;
            case RULES:
                openRules(player);
                break;
            case MANAGE:
                openManage(player);
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------
    // 绑定输入表单
    // ---------------------------------------------------------------

    public static void openBinding(Player player) {
        KBBSToperCore.scheduler().runSync(() -> {
            var form = org.geysermc.cumulus.form.CustomForm.builder()
                    .title(fmt(Message.FORM_BINDING_TITLE.getString("绑定论坛ID")))
                    .label(fmt(Message.FORM_BINDING_LABEL.getString("请输入你的 KLPBBS 论坛用户名（不是 uid）")))
                    .input(fmt(Message.FORM_BINDING_INPUT.getString("论坛ID")),
                            fmt(Message.FORM_BINDING_PLACEHOLDER.getString("在此输入")), "");
            form.validResultHandler((org.geysermc.cumulus.response.CustomFormResponse resp) -> {
                String input;
                try {
                    input = resp.next();
                } catch (Throwable t) {
                    return;
                }
                if (input == null || input.trim().isEmpty()) {
                    player.sendMessage(Message.PREFIX.getString()
                            + Message.FORM_BINDING_EMPTY.getString("&c论坛ID不能为空。"));
                    return;
                }
                String id = input.trim();
                // 走与命令完全相同的路径：二次确认 / 冷却 / 重复绑定检查全部生效
                runCommand(player, "binding", id);
                // 缓存里还留着待确认的 ID，说明是第一次提交，重新弹表单完成二次确认
                Bukkit.getScheduler().runTaskLater(KBBSToperBukkit.getInstance(), () -> {
                    if (player.isOnline()
                            && KBBSToperCore.cli().getCache().containsKey(player.getUniqueId().toString())) {
                        openBinding(player);
                    }
                }, 10L);
            });
            form.closedOrInvalidResultHandler(() -> {
            });

            if (!sendForm(player, form.build())) {
                GUI.openMain(player);
            }
        });
    }

    // ---------------------------------------------------------------
    // 我的状态 / 规则 / 宣传帖（只读表单）
    // ---------------------------------------------------------------

    public static void openStatus(Player player) {
        KBBSToperCore.scheduler().runAsync(() -> {
            List<String> lines = GuiDataResolver.statusLines(new BukkitPlayer(player));
            KBBSToperCore.scheduler().runSync(() ->
                    openInfo(player, Message.FORM2_STATUS_TITLE.getString("我的状态"), lines));
        });
    }

    public static void openRules(Player player) {
        List<String> lines = GuiDataResolver.rulesLines();
        KBBSToperCore.scheduler().runSync(() ->
                openInfo(player, Message.FORM2_RULES_TITLE.getString("活动规则"), lines));
    }

    public static void openPost(Player player) {
        KBBSToperCore.scheduler().runAsync(() -> {
            String url = "https://" + Option.WEBSITE.getString() + "/thread-"
                    + Option.BBS_URL.getString() + "-1-1.html";
            String lastpost = PlaceholderResolver.resolve(new BukkitPlayer(player), "lastpost");

            StringBuilder sb = new StringBuilder();
            for (String line : Message.CLICKPOSTICON.getStringList()) {
                sb.append(line.replace("%PAGE%", url)).append("\n");
            }
            if (lastpost != null) {
                sb.append("\n").append(Message.POSTERTIME.getString()).append(": ").append(lastpost);
            }
            KBBSToperCore.scheduler().runSync(() ->
                    openInfo(player, Message.FORM_POST_TITLE.getString("本服宣传帖"), List.of(sb.toString())));
        });
    }

    /** 一张只读表单 + 一个"返回主菜单"按钮。 */
    private static void openInfo(Player player, String title, List<String> lines) {
        var form = org.geysermc.cumulus.form.SimpleForm.builder()
                .title(fmt(title))
                .content(fmt(lines == null ? "" : String.join("\n", lines)))
                .button(fmt(Message.FORM2_BACK.getString("返回主菜单")));
        form.validResultHandler((org.geysermc.cumulus.response.SimpleFormResponse resp) -> openMain(player));
        form.closedOrInvalidResultHandler(() -> {
        });

        if (!sendForm(player, form.build())) {
            GUI.openMain(player);
        }
    }

    // ---------------------------------------------------------------
    // 我的记录 / 排行榜（分页）
    // ---------------------------------------------------------------

    public static void openRecords(Player player, int page) {
        KBBSToperCore.scheduler().runAsync(() -> {
            Poster poster = GuiDataResolver.poster(new BukkitPlayer(player));
            List<String> all = (poster == null) ? new ArrayList<>() : new ArrayList<>(poster.getTopStates());
            KBBSToperCore.scheduler().runSync(() ->
                    sendPagedForm(player, Message.FORM2_RECORDS_TITLE.getString("我的顶帖记录"), "records", page, all));
        });
    }

    public static void openTop(Player player, int page) {
        KBBSToperCore.scheduler().runAsync(() -> {
            List<String> all = new ArrayList<>();
            SQLer sql = SQLManager.getSQLer();
            if (sql != null) {
                List<Poster> counted = sql.getTopPosters();
                if (counted != null) {
                    all.addAll(rankLines(counted));
                }
                List<Poster> nocount = sql.getNoCountPosters();
                if (nocount != null) {
                    all.addAll(rankLines(nocount));
                }
            }
            KBBSToperCore.scheduler().runSync(() ->
                    sendPagedForm(player, Message.FORM2_TOP_TITLE.getString("顶帖排行榜"), "top", page, all));
        });
    }

    private static List<String> rankLines(List<Poster> list) {
        List<String> out = new ArrayList<>();
        for (Poster p : list) {
            out.add(p.getName() + " (" + p.getBbsname() + ") - " + p.getCount());
        }
        return out;
    }

    private static void sendPagedForm(Player player, String title, String kind, int page, List<String> all) {
        int total = Math.max(1, (int) Math.ceil(all.size() / (double) PAGE_SIZE));
        int cur = Math.max(1, Math.min(page, total));
        int start = (cur - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, all.size());

        StringBuilder content = new StringBuilder();
        if (start >= all.size()) {
            content.append(fmt(Message.GUI2_RECORDS_EMPTY.getString("暂无数据")));
        } else {
            for (int i = start; i < end; i++) {
                content.append(i + 1).append(". ").append(all.get(i)).append("\n");
            }
        }
        content.append(fmt(Message.FORM2_PAGE.getString("第 %PAGE%/%TOTAL% 页")
                .replace("%PAGE%", String.valueOf(cur))
                .replace("%TOTAL%", String.valueOf(total))));

        var form = org.geysermc.cumulus.form.SimpleForm.builder()
                .title(fmt(title))
                .content(content.toString());
        List<FormAction> actions = new ArrayList<>();
        if (cur > 1) {
            form.button(fmt(Message.FORM2_PREV.getString("上一页")));
            actions.add(FormAction.PREV_PAGE);
        }
        if (cur < total) {
            form.button(fmt(Message.FORM2_NEXT.getString("下一页")));
            actions.add(FormAction.NEXT_PAGE);
        }
        form.button(fmt(Message.FORM2_BACK.getString("返回主菜单")));
        actions.add(FormAction.BACK);

        final List<FormAction> finalActions = actions;
        form.validResultHandler((org.geysermc.cumulus.response.SimpleFormResponse resp) -> {
            int id = resp.clickedButtonId();
            if (id < 0 || id >= finalActions.size()) {
                return;
            }
            FormAction a = finalActions.get(id);
            switch (a) {
                case PREV_PAGE:
                    if ("top".equals(kind)) {
                        openTop(player, cur - 1);
                    } else {
                        openRecords(player, cur - 1);
                    }
                    break;
                case NEXT_PAGE:
                    if ("top".equals(kind)) {
                        openTop(player, cur + 1);
                    } else {
                        openRecords(player, cur + 1);
                    }
                    break;
                case BACK:
                    openMain(player);
                    break;
                default:
                    break;
            }
        });
        form.closedOrInvalidResultHandler(() -> {
        });

        if (!sendForm(player, form.build())) {
            GUI.openMain(player);
        }
    }

    // ---------------------------------------------------------------
    // 管理 / 测试 / 调试（菜单表单）
    // ---------------------------------------------------------------

    public static void openManage(Player player) {
        var form = org.geysermc.cumulus.form.SimpleForm.builder()
                .title(fmt(Message.FORM2_MANAGE_TITLE.getString("管理菜单")))
                .content("");
        List<FormAction> actions = new ArrayList<>();

        addMenu(form, actions, Message.FORM2_MANAGE_TEST.getString("测试奖励"), FormAction.TEST_REWARD);
        addMenu(form, actions, Message.FORM2_MANAGE_LIST.getString("顶帖列表(全服)"), FormAction.LIST);
        addMenu(form, actions, Message.FORM2_MANAGE_CHECK.getString("检查绑定"), FormAction.CHECK);
        addMenu(form, actions, Message.FORM2_MANAGE_DELETE.getString("删除玩家数据"), FormAction.DELETE);
        addMenu(form, actions, Message.FORM2_MANAGE_RELOAD.getString("重载插件"), FormAction.RELOAD);
        addMenu(form, actions, Message.FORM2_MANAGE_DEBUG.getString("调试"), FormAction.DEBUG);
        addMenu(form, actions, Message.FORM2_BACK.getString("返回主菜单"), FormAction.BACK);

        final List<FormAction> finalActions = actions;
        form.validResultHandler((org.geysermc.cumulus.response.SimpleFormResponse resp) -> {
            int id = resp.clickedButtonId();
            if (id < 0 || id >= finalActions.size()) {
                return;
            }
            dispatchManage(player, finalActions.get(id));
        });
        form.closedOrInvalidResultHandler(() -> {
        });

        if (!sendForm(player, form.build())) {
            GUI.openMain(player);
        }
    }

    private static void addMenu(org.geysermc.cumulus.form.SimpleForm.Builder form,
                                List<FormAction> actions, String label, FormAction action) {
        form.button(fmt(label));
        actions.add(action);
    }

    private static void dispatchManage(Player player, FormAction action) {
        switch (action) {
            case TEST_REWARD:
                openTest(player);
                break;
            case LIST:
                runCommand(player, "list");
                break;
            case CHECK:
                openInput(player, "check", Message.FORM2_MANAGE_CHECK.getString("检查绑定"), "论坛ID");
                break;
            case DELETE:
                openInput(player, "delete", Message.FORM2_MANAGE_DELETE.getString("删除玩家数据"), "玩家名");
                break;
            case RELOAD:
                runCommand(player, "reload");
                break;
            case DEBUG:
                openDebug(player);
                break;
            case BACK:
                openMain(player);
                break;
            default:
                break;
        }
    }

    /** 测试奖励子菜单：三种类型 + 返回管理菜单。 */
    public static void openTest(Player player) {
        var form = org.geysermc.cumulus.form.SimpleForm.builder()
                .title(fmt(Message.FORM2_TEST_TITLE.getString("测试奖励")))
                .content("");
        List<FormAction> actions = new ArrayList<>();

        addMenu(form, actions, Message.FORM2_TEST_NORMAL.getString("普通奖励"), FormAction.TEST_NORMAL);
        addMenu(form, actions, Message.FORM2_TEST_INCENTIVE.getString("激励奖励"), FormAction.TEST_INCENTIVE);
        addMenu(form, actions, Message.FORM2_TEST_OFFDAY.getString("休息日奖励"), FormAction.TEST_OFFDAY);
        addMenu(form, actions, Message.FORM2_BACK.getString("返回管理菜单"), FormAction.BACK_MANAGE);

        final List<FormAction> finalActions = actions;
        form.validResultHandler((org.geysermc.cumulus.response.SimpleFormResponse resp) -> {
            int id = resp.clickedButtonId();
            if (id < 0 || id >= finalActions.size()) {
                return;
            }
            dispatchTest(player, finalActions.get(id));
        });
        form.closedOrInvalidResultHandler(() -> {
        });

        if (!sendForm(player, form.build())) {
            GUI.openMain(player);
        }
    }

    private static void dispatchTest(Player player, FormAction action) {
        switch (action) {
            case TEST_NORMAL:
                runCommand(player, "testreward", "normal");
                break;
            case TEST_INCENTIVE:
                runCommand(player, "testreward", "incentive");
                break;
            case TEST_OFFDAY:
                runCommand(player, "testreward", "offday");
                break;
            case BACK_MANAGE:
                openManage(player);
                break;
            default:
                break;
        }
    }

    /** 调试子菜单。 */
    public static void openDebug(Player player) {
        var form = org.geysermc.cumulus.form.SimpleForm.builder()
                .title(fmt(Message.FORM2_MANAGE_DEBUG.getString("调试")))
                .content("");
        List<FormAction> actions = new ArrayList<>();

        addMenu(form, actions, "清空", FormAction.DEBUG_CLEAR);
        addMenu(form, actions, "状态", FormAction.DEBUG_STATUS);
        addMenu(form, actions, "模拟", FormAction.DEBUG_SIMULATE);
        addMenu(form, actions, fmt(Message.FORM2_BACK.getString("返回管理菜单")), FormAction.BACK_MANAGE);

        final List<FormAction> finalActions = actions;
        form.validResultHandler((org.geysermc.cumulus.response.SimpleFormResponse resp) -> {
            int id = resp.clickedButtonId();
            if (id < 0 || id >= finalActions.size()) {
                return;
            }
            dispatchDebug(player, finalActions.get(id));
        });
        form.closedOrInvalidResultHandler(() -> {
        });

        if (!sendForm(player, form.build())) {
            GUI.openMain(player);
        }
    }

    private static void dispatchDebug(Player player, FormAction action) {
        switch (action) {
            case DEBUG_CLEAR:
                runCommand(player, "debug", "clear");
                break;
            case DEBUG_STATUS:
                runCommand(player, "debug", "status");
                break;
            case DEBUG_SIMULATE:
                runCommand(player, "debug", "simulate");
                break;
            case BACK_MANAGE:
                openManage(player);
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------
    // 参数输入表单（检查 / 删除）
    // ---------------------------------------------------------------

    public static void openInput(Player player, String inputAction, String title, String label) {
        KBBSToperCore.scheduler().runSync(() -> {
            var form = org.geysermc.cumulus.form.CustomForm.builder()
                    .title(fmt(title))
                    .label(fmt(Message.FORM2_INPUT_LABEL.getString("请输入:")))
                    .input(fmt(label), fmt(Message.FORM2_INPUT_PLACEHOLDER.getString("在此输入")), "");
            form.validResultHandler((org.geysermc.cumulus.response.CustomFormResponse resp) -> {
                String input;
                try {
                    input = resp.next();
                } catch (Throwable t) {
                    return;
                }
                if (input == null || input.trim().isEmpty()) {
                    player.sendMessage(Message.PREFIX.getString()
                            + Message.FORM2_INPUT_EMPTY.getString("&c输入不能为空。"));
                    return;
                }
                String id = input.trim();
                if ("check".equals(inputAction)) {
                    runCommand(player, "check", "bbsid", id);
                } else if ("delete".equals(inputAction)) {
                    runCommand(player, "delete", id);
                }
            });
            form.closedOrInvalidResultHandler(() -> {
            });

            if (!sendForm(player, form.build())) {
                GUI.openMain(player);
            }
        });
    }
}
