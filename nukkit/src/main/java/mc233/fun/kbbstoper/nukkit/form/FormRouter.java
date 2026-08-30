package mc233.fun.kbbstoper.nukkit.form;

import cn.nukkit.Player;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.element.ElementInput;
import cn.nukkit.form.element.ElementLabel;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.form.window.FormWindowSimple;
import mc233.fun.kbbstoper.core.GuiDataResolver;
import mc233.fun.kbbstoper.core.KBBSToperCore;
import mc233.fun.kbbstoper.core.Message;
import mc233.fun.kbbstoper.core.Option;
import mc233.fun.kbbstoper.core.PlaceholderResolver;
import mc233.fun.kbbstoper.core.Poster;
import mc233.fun.kbbstoper.core.sql.SQLManager;
import mc233.fun.kbbstoper.core.sql.SQLer;
import mc233.fun.kbbstoper.nukkit.NukkitPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表单构建与状态跟踪（v2 最终稿，多级表单）。
 *
 * <p>Nukkit 的表单回调只带回表单 id，不带上下文，
 * 所以这里按「玩家 UUID + 表单 id」记住每张表单是什么、按钮各对应什么动作。</p>
 */
public final class FormRouter {

    /** 基岩版每页行数（表单行高有限，比 JAVA 少）。 */
    private static final int BEDROCK_PAGE_SIZE = 12;

    /** key = 玩家UUID + "#" + 表单id。 */
    private static final Map<String, PendingForm> PENDING = new ConcurrentHashMap<>();

    private FormRouter() {
    }

    /** 一张已发出、等待回应的表单。 */
    public static final class PendingForm {

        /** 主界面表单，buttons 记录每个按钮下标对应的动作。 */
        public static final int TYPE_MAIN = 0;
        /** 绑定输入表单。 */
        public static final int TYPE_BINDING = 1;
        /** 纯展示表单（宣传帖/状态/规则/信息），点任何按钮都只是关闭。 */
        public static final int TYPE_INFO = 2;
        /** 分页列表表单（我的记录/排行榜），buttons 为 上一页/下一页/返回。 */
        public static final int TYPE_PAGED = 3;
        /** 菜单按钮表单（管理/测试/调试），buttons 为动作列表。 */
        public static final int TYPE_MENU = 4;
        /** 参数输入表单（检查/删除），inputAction 记录输入的用途。 */
        public static final int TYPE_INPUT = 5;

        public final int type;
        public final List<FormAction> buttons;
        /** TYPE_PAGED: "records" | "top"。 */
        public final String pagedKind;
        /** TYPE_PAGED: 当前页码。 */
        public final int page;
        /** TYPE_MENU: BACK 动作返回哪一级（"main" | "manage"）。 */
        public final String backTo;
        /** TYPE_INPUT: 输入用于哪个命令动作（"check" | "delete"）。 */
        public final String inputAction;

        PendingForm(int type, List<FormAction> buttons, String pagedKind, int page,
                    String backTo, String inputAction) {
            this.type = type;
            this.buttons = buttons;
            this.pagedKind = pagedKind;
            this.page = page;
            this.backTo = backTo;
            this.inputAction = inputAction;
        }

        static PendingForm main(List<FormAction> buttons) {
            return new PendingForm(TYPE_MAIN, buttons, null, 1, null, null);
        }

        static PendingForm binding() {
            return new PendingForm(TYPE_BINDING, null, null, 1, null, null);
        }

        static PendingForm info() {
            return new PendingForm(TYPE_INFO, null, null, 1, null, null);
        }

        static PendingForm paged(List<FormAction> buttons, String kind, int page) {
            return new PendingForm(TYPE_PAGED, buttons, kind, page, null, null);
        }

        static PendingForm menu(List<FormAction> buttons, String backTo) {
            return new PendingForm(TYPE_MENU, buttons, null, 1, backTo, null);
        }

        static PendingForm input(String inputAction) {
            return new PendingForm(TYPE_INPUT, null, null, 1, null, inputAction);
        }
    }

    private static String key(UUID uuid, int formId) {
        return uuid + "#" + formId;
    }

    /** 取出并移除一张待回应表单。 */
    public static PendingForm consume(UUID uuid, int formId) {
        return PENDING.remove(key(uuid, formId));
    }

    /** 玩家退出时清掉他所有待回应表单，避免累积。 */
    public static void clear(UUID uuid) {
        String prefix = uuid + "#";
        PENDING.keySet().removeIf(k -> k.startsWith(prefix));
    }

    // ---------------------------------------------------------------
    // 主界面
    // ---------------------------------------------------------------

    /** 打开主界面。会读数据库判断是否已绑定，先异步再回主线程。 */
    public static void openMainForm(Player player) {
        KBBSToperCore.scheduler().runAsync(() -> {
            Poster poster = GuiDataResolver.poster(new NukkitPlayer(player));
            boolean bound = poster != null && poster.getBbsname() != null && !poster.getBbsname().isBlank();
            List<String> status = GuiDataResolver.statusLines(new NukkitPlayer(player));
            boolean op = player.hasPermission("bbstoper.manage");
            KBBSToperCore.scheduler().runSync(() -> sendMainForm(player, status, bound, op));
        });
    }

    private static void sendMainForm(Player player, List<String> status, boolean bound, boolean op) {
        String title = Message.FORM_TITLE.getString("KBBSToperForStarCity")
                .replace("%PREFIX%", Message.PREFIX.getString());

        StringBuilder content = new StringBuilder();
        for (String line : status) {
            content.append(line).append("\n");
        }

        FormWindowSimple form = new FormWindowSimple(strip(title), strip(content.toString()));
        List<FormAction> actions = new ArrayList<>();

        String bindLabel = bound
                ? Message.FORM_BUTTON_REBINDING.getString("更换论坛ID")
                : Message.FORM_BUTTON_BINDING.getString("绑定论坛ID");
        form.addButton(new ElementButton(strip(bindLabel)));
        actions.add(FormAction.BINDING);

        form.addButton(new ElementButton(strip(Message.FORM_BUTTON_REWARD.getString("领取顶帖奖励"))));
        actions.add(FormAction.REWARD);

        form.addButton(new ElementButton(strip(Message.FORM2_BTN_MYRECORDS.getString("我的顶帖记录"))));
        actions.add(FormAction.MY_RECORDS);

        form.addButton(new ElementButton(strip(Message.FORM2_BTN_STATUS.getString("我的状态"))));
        actions.add(FormAction.MY_STATUS);

        form.addButton(new ElementButton(strip(Message.FORM_BUTTON_TOP.getString("顶帖排行榜"))));
        actions.add(FormAction.TOP);

        form.addButton(new ElementButton(strip(Message.FORM_BUTTON_POST.getString("查看宣传帖"))));
        actions.add(FormAction.POST);

        form.addButton(new ElementButton(strip(Message.FORM2_BTN_RULES.getString("活动规则"))));
        actions.add(FormAction.RULES);

        if (op) {
            form.addButton(new ElementButton(strip(Message.FORM2_BTN_MANAGE.getString("管理菜单"))));
            actions.add(FormAction.MANAGE);
        }

        int id = player.showFormWindow(form);
        PENDING.put(key(player.getUniqueId(), id), PendingForm.main(actions));
    }

    // ---------------------------------------------------------------
    // 绑定输入表单
    // ---------------------------------------------------------------

    /** 打开绑定输入表单。基岩版没有可点击聊天消息，所以直接用输入框。 */
    public static void openBindingForm(Player player) {
        KBBSToperCore.scheduler().runSync(() -> {
            FormWindowCustom form = new FormWindowCustom(
                    strip(Message.FORM_BINDING_TITLE.getString("绑定论坛ID")));
            form.addElement(new ElementLabel(
                    strip(Message.FORM_BINDING_LABEL.getString("请输入你的 KLPBBS 论坛用户名（不是 uid）"))));
            form.addElement(new ElementInput(
                    strip(Message.FORM_BINDING_INPUT.getString("论坛ID")),
                    strip(Message.FORM_BINDING_PLACEHOLDER.getString("在此输入")),
                    ""));

            int id = player.showFormWindow(form);
            PENDING.put(key(player.getUniqueId(), id), PendingForm.binding());
        });
    }

    /** 打开绑定二次确认输入表单，提示玩家再输入一遍用户名称。 */
    public static void openBindingConfirmForm(Player player) {
        KBBSToperCore.scheduler().runSync(() -> {
            FormWindowCustom form = new FormWindowCustom(
                    strip(Message.FORM_BINDING_TITLE.getString("绑定论坛ID")));
            form.addElement(new ElementLabel(
                    strip(Message.FORM_BINDING_CONFIRM_LABEL.getString("再输入一遍用户名称确认"))));
            form.addElement(new ElementInput(
                    strip(Message.FORM_BINDING_INPUT.getString("论坛ID")),
                    strip(Message.FORM_BINDING_PLACEHOLDER.getString("在此输入")),
                    ""));

            int id = player.showFormWindow(form);
            PENDING.put(key(player.getUniqueId(), id), PendingForm.binding());
        });
    }

    // ---------------------------------------------------------------
    // 我的状态 / 规则 / 宣传帖（只读表单）
    // ---------------------------------------------------------------

    public static void openStatusForm(Player player) {
        KBBSToperCore.scheduler().runAsync(() -> {
            List<String> lines = GuiDataResolver.statusLines(new NukkitPlayer(player));
            KBBSToperCore.scheduler().runSync(() ->
                    openInfoForm(player, Message.FORM2_STATUS_TITLE.getString("我的状态"), lines));
        });
    }

    public static void openRulesForm(Player player) {
        List<String> lines = GuiDataResolver.rulesLines();
        KBBSToperCore.scheduler().runSync(() ->
                openInfoForm(player, Message.FORM2_RULES_TITLE.getString("活动规则"), lines));
    }

    /** 显示宣传帖链接。基岩版无法点击打开链接，所以把地址原样列出来供玩家复制。 */
    public static void openPostForm(Player player) {
        KBBSToperCore.scheduler().runAsync(() -> {
            String url = "https://" + Option.WEBSITE.getString() + "/thread-"
                    + Option.BBS_URL.getString() + "-1-1.html";
            String lastpost = PlaceholderResolver.resolve(new NukkitPlayer(player), "lastpost");

            StringBuilder sb = new StringBuilder();
            for (String line : Message.CLICKPOSTICON.getStringList()) {
                sb.append(line.replace("%PAGE%", url)).append("\n");
            }
            if (lastpost != null) {
                sb.append("\n").append(Message.POSTERTIME.getString()).append(": ").append(lastpost);
            }
            KBBSToperCore.scheduler().runSync(() ->
                    openInfoForm(player, Message.FORM_POST_TITLE.getString("本服宣传帖"), List.of(sb.toString())));
        });
    }

    /** 用一张只读表单展示多行文本。 */
    public static void openInfoForm(Player player, String title, List<String> lines) {
        KBBSToperCore.scheduler().runSync(() -> {
            FormWindowSimple form = new FormWindowSimple(strip(title),
                    strip(lines == null ? "" : String.join("\n", lines)));
            int id = player.showFormWindow(form);
            PENDING.put(key(player.getUniqueId(), id), PendingForm.info());
        });
    }

    // ---------------------------------------------------------------
    // 我的记录 / 排行榜（分页）
    // ---------------------------------------------------------------

    public static void openRecordsForm(Player player, int page) {
        KBBSToperCore.scheduler().runAsync(() -> {
            Poster poster = GuiDataResolver.poster(new NukkitPlayer(player));
            List<String> all = (poster == null) ? new ArrayList<>() : poster.getTopStates();
            List<String> lines = new ArrayList<>();
            for (String t : all) {
                lines.add(t);
            }
            KBBSToperCore.scheduler().runSync(() -> sendPagedForm(player,
                    Message.FORM2_RECORDS_TITLE.getString("我的顶帖记录"), "records", page,
                    lines));
        });
    }

    public static void openTopForm(Player player, int page) {
        KBBSToperCore.scheduler().runAsync(() -> {
            List<String> lines = new ArrayList<>();
            SQLer sql = SQLManager.getSQLer();
            if (sql != null) {
                List<Poster> counted = sql.getTopPosters();
                if (counted != null) {
                    lines.addAll(rankLines(counted));
                }
                List<Poster> nocount = sql.getNoCountPosters();
                if (nocount != null) {
                    lines.addAll(rankLines(nocount));
                }
            }
            KBBSToperCore.scheduler().runSync(() -> sendPagedForm(player,
                    Message.FORM2_TOP_TITLE.getString("顶帖排行榜"), "top", page,
                    lines));
        });
    }

    private static List<String> rankLines(List<Poster> list) {
        List<String> out = new ArrayList<>();
        for (Poster p : list) {
            out.add(p.getName() + " (" + p.getBbsname() + ") - " + p.getCount());
        }
        return out;
    }

    private static void sendPagedForm(Player player, String title, String kind, int page,
                                      List<String> all) {
        int total = Math.max(1, (int) Math.ceil(all.size() / (double) BEDROCK_PAGE_SIZE));
        int cur = Math.max(1, Math.min(page, total));
        int start = (cur - 1) * BEDROCK_PAGE_SIZE;
        int end = Math.min(start + BEDROCK_PAGE_SIZE, all.size());

        StringBuilder content = new StringBuilder();
        if (start >= all.size()) {
            content.append(strip(Message.GUI2_RECORDS_EMPTY.getString("暂无数据")));
        } else {
            for (int i = start; i < end; i++) {
                content.append(i + 1).append(". ").append(all.get(i)).append("\n");
            }
        }
        content.append(strip(Message.FORM2_PAGE.getString("第 %PAGE%/%TOTAL% 页")
                .replace("%PAGE%", String.valueOf(cur))
                .replace("%TOTAL%", String.valueOf(total))));

        FormWindowSimple form = new FormWindowSimple(strip(title), strip(content.toString()));
        List<FormAction> actions = new ArrayList<>();
        if (cur > 1) {
            form.addButton(new ElementButton(strip(Message.FORM2_PREV.getString("上一页"))));
            actions.add(FormAction.PREV_PAGE);
        }
        if (cur < total) {
            form.addButton(new ElementButton(strip(Message.FORM2_NEXT.getString("下一页"))));
            actions.add(FormAction.NEXT_PAGE);
        }
        form.addButton(new ElementButton(strip(Message.FORM2_BACK.getString("返回主菜单"))));
        actions.add(FormAction.BACK);

        int id = player.showFormWindow(form);
        PENDING.put(key(player.getUniqueId(), id), PendingForm.paged(actions, kind, cur));
    }

    // ---------------------------------------------------------------
    // 管理 / 测试 / 调试（菜单表单）
    // ---------------------------------------------------------------

    public static void openManageForm(Player player) {
        FormWindowSimple form = new FormWindowSimple(
                strip(Message.FORM2_MANAGE_TITLE.getString("管理菜单")), "");
        List<FormAction> actions = new ArrayList<>();

        addMenuButton(form, actions, Message.FORM2_MANAGE_TEST.getString("测试奖励"), FormAction.TEST_REWARD);
        addMenuButton(form, actions, Message.FORM2_MANAGE_LIST.getString("顶帖列表(全服)"), FormAction.LIST);
        addMenuButton(form, actions, Message.FORM2_MANAGE_CHECK.getString("检查绑定"), FormAction.CHECK);
        addMenuButton(form, actions, Message.FORM2_MANAGE_DELETE.getString("删除玩家数据"), FormAction.DELETE);
        addMenuButton(form, actions, Message.FORM2_MANAGE_RELOAD.getString("重载插件"), FormAction.RELOAD);
        addMenuButton(form, actions, Message.FORM2_MANAGE_DEBUG.getString("调试"), FormAction.DEBUG);
        addMenuButton(form, actions, Message.FORM2_BACK.getString("返回主菜单"), FormAction.BACK);

        int id = player.showFormWindow(form);
        PENDING.put(key(player.getUniqueId(), id), PendingForm.menu(actions, "main"));
    }

    /** 测试奖励子菜单：三种类型 + 返回管理菜单。 */
    public static void openTestForm(Player player) {
        FormWindowSimple form = new FormWindowSimple(
                strip(Message.FORM2_TEST_TITLE.getString("测试奖励")), "");
        List<FormAction> actions = new ArrayList<>();

        addMenuButton(form, actions, Message.FORM2_TEST_NORMAL.getString("普通奖励"), FormAction.TEST_NORMAL);
        addMenuButton(form, actions, Message.FORM2_TEST_INCENTIVE.getString("激励奖励"), FormAction.TEST_INCENTIVE);
        addMenuButton(form, actions, Message.FORM2_TEST_OFFDAY.getString("休息日奖励"), FormAction.TEST_OFFDAY);
        addMenuButton(form, actions, Message.FORM2_BACK.getString("返回管理菜单"), FormAction.BACK);

        int id = player.showFormWindow(form);
        PENDING.put(key(player.getUniqueId(), id), PendingForm.menu(actions, "manage"));
    }

    /** 调试子菜单。 */
    public static void openDebugForm(Player player) {
        FormWindowSimple form = new FormWindowSimple(
                strip(Message.FORM2_MANAGE_DEBUG.getString("调试")), "");
        List<FormAction> actions = new ArrayList<>();

        addMenuButton(form, actions, "清空", FormAction.DEBUG_CLEAR);
        addMenuButton(form, actions, "状态", FormAction.DEBUG_STATUS);
        addMenuButton(form, actions, "模拟", FormAction.DEBUG_SIMULATE);
        addMenuButton(form, actions, strip(Message.FORM2_BACK.getString("返回管理菜单")), FormAction.BACK);

        int id = player.showFormWindow(form);
        PENDING.put(key(player.getUniqueId(), id), PendingForm.menu(actions, "manage"));
    }

    private static void addMenuButton(FormWindowSimple form, List<FormAction> actions,
                                      String label, FormAction action) {
        form.addButton(new ElementButton(strip(label)));
        actions.add(action);
    }

    // ---------------------------------------------------------------
    // 参数输入表单（检查 / 删除）
    // ---------------------------------------------------------------

    public static void openInputForm(Player player, String inputAction, String title, String label) {
        FormWindowCustom form = new FormWindowCustom(strip(title));
        form.addElement(new ElementLabel(strip(Message.FORM2_INPUT_LABEL.getString("请输入:"))));
        form.addElement(new ElementInput(
                strip(label),
                strip(Message.FORM2_INPUT_PLACEHOLDER.getString("在此输入")),
                ""));
        int id = player.showFormWindow(form);
        PENDING.put(key(player.getUniqueId(), id), PendingForm.input(inputAction));
    }

    // ---------------------------------------------------------------
    // 工具
    // ---------------------------------------------------------------

    /**
     * 表单标题与内容不支持 § 颜色码渲染的场合会显示成乱码，
     * 这里把已经转换过的颜色码去掉，只保留纯文本。
     */
    private static String strip(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }
}
