package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.PlatformConfig;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** lang.yml 的键位枚举，取值时做颜色码转换并缓存。 */
public enum Message {

    PREFIX("prefix"),
    ENABLE("enable"),
    RELOAD("reload"),
    FAILEDCONNECTSQL("failedconnectsql"),
    QUERYCOOLDOWN("querycooldown"),
    MANUALCOOLDOWN("manualcooldown"),
    POSTERID("posterid"),
    POSTERNUM("posternum"),
    OVERPAGE("overpage"),
    NOPLAYER("noplayer"),
    POSTERTIME("postertime"),
    PAGEINFO("pageinfo"),
    NOPOSTER("noposter"),
    POSTERPLAYER("posterplayer"),
    POSTERTOTAL("postertotal"),
    PAGEINFOTOP("pageinfotop"),
    NOTBOUND("notbound"),
    NOPOST("nopost"),
    OVERTIME("overtime"),
    WAITAMIN("waitamin"),
    INTERVALTOOSHORT("intervaltooshort"),
    REWARD("reward"),
    REWARDSUMMARY("rewardsummary"),
    EXTRAREWARD("extrareward"),
    REWARDGIVED("rewardgived"),
    BROADCAST("broadcast"),
    ENTER("enter"),
    CANCELED("canceled"),
    REPEAT("repeat"),
    ONCOOLDOWN("oncooldown"),
    SAMEBIND("samebind"),
    OWNSAMEBIND("ownsamebind"),
    BINDINGSUCCESS("bindingsuccess"),
    IDOWNER("idowner"),
    IDNOTFOUND("idnotfound"),
    OWNERID("ownerid"),
    OWNERNOTFOUND("ownernotfound"),
    NOPERMISSION("nopermission"),
    INVALID("invalid"),
    INVALIDNUM("invalidnum"),
    PLAYERCMD("playercmd"),
    PAGENOTVISIBLE("pagenotvisible"),
    NONE("none"),
    FAILEDGETWEB("failedgetweb"),
    FAILEDRESOLVEWEB("failedresolveweb"),
    FAILEDUNINSTALLMO("faileduninstallmo"),
    GUI_TITLE("gui.title"),
    GUI_FRAME("gui.frame"),
    GUI_NOTBOUND("gui.notbound"),
    GUI_PEAKREWARDS("gui.peakrewards"),
    GUI_PAGENOTVISIBLE("gui.pagenotvisible"),
    CLICKPOSTICON("clickposticon"),
    DELETESUCCESS("deletesuccess"),
    INFO("info"),
    EXTRAINFO("extrainfo"),
    HELP_TITLE("help.title"),
    HELP_HELP("help.help"),
    HELP_BINDING("help.binding"),
    HELP_REWARD("help.reward"),
    HELP_TESTREWARD("help.testreward"),
    HELP_LIST("help.list"),
    HELP_TOP("help.top"),
    HELP_CHECK("help.check"),
    HELP_DELETE("help.delete"),
    HELP_RELOAD("help.reload"),
    HELP_DEBUG("help.debug"),
    DEBUG_CLEAR("debug.clear"),
    DEBUG_STATUS("debug.status"),
    DEBUG_SIMULATE("debug.simulate"),
    // ---- 表单界面（Nukkit 端使用，Bukkit 端忽略） ----
    FORM_TITLE("form.title"),
    FORM_CONTENT("form.content"),
    FORM_BUTTON_BINDING("form.button.binding"),
    FORM_BUTTON_REBINDING("form.button.rebinding"),
    FORM_BUTTON_REWARD("form.button.reward"),
    FORM_BUTTON_TOP("form.button.top"),
    FORM_BUTTON_POST("form.button.post"),
    FORM_BINDING_TITLE("form.binding.title"),
    FORM_BINDING_LABEL("form.binding.label"),
    FORM_BINDING_INPUT("form.binding.input"),
    FORM_BINDING_PLACEHOLDER("form.binding.placeholder"),
    FORM_BINDING_EMPTY("form.binding.empty"),
    FORM_BINDING_CONFIRM_LABEL("form.binding.confirm-label"),
    FORM_POST_TITLE("form.post.title"),
    FORM_POST_CONTENT("form.post.content"),
    // ---- 双端新 GUI（v2 最终稿）共用文案 ----
    GUI2_STATUS_BBSID("gui2.status.bbsid"),
    GUI2_STATUS_POSTTIMES("gui2.status.posttimes"),
    GUI2_STATUS_MAXHP("gui2.status.maxhp"),
    GUI2_STATUS_REWARDTIME("gui2.status.rewardtime"),
    GUI2_STATUS_REWARDBEFORE("gui2.status.rewardbefore"),
    GUI2_STATUS_TODAY("gui2.status.today"),
    GUI2_STATUS_LEVEL("gui2.status.level"),
    GUI2_STATUS_NEXT("gui2.status.next"),
    GUI2_STATUS_CURHP("gui2.status.curhp"),
    GUI2_STATUS_CURGM("gui2.status.curgm"),
    GUI2_STATUS_CUREM("gui2.status.curem"),
    GUI2_STATUS_CURGROWTH("gui2.status.curgrowth"),
    GUI2_STATUS_CURSTAR("gui2.status.curstar"),
    GUI2_STATUS_TITLE("gui2.status.title"),
    GUI2_RECORDS_TITLE("gui2.records.title"),
    GUI2_RECORDS_EMPTY("gui2.records.empty"),
    GUI2_RECORD_KIND("gui2.records.kind"),
    GUI2_RECORD_PEAK("gui2.records.peak"),
    GUI2_RECORD_OFFPEAK("gui2.records.offpeak"),
    GUI2_RECORD_REWARD("gui2.records.reward"),
    GUI2_RECORD_NOREWARD("gui2.records.noreward"),
    GUI2_TOP_TITLE("gui2.top.title"),
    GUI2_TOP_EMPTY("gui2.top.empty"),
    GUI2_RULES_TITLE("gui2.rules.title"),
    GUI2_RULES_HOWTO("gui2.rules.howto"),
    GUI2_RULES_PEAK("gui2.rules.peak"),
    GUI2_RULES_OFFPEAK("gui2.rules.offpeak"),
    GUI2_RULES_LIMIT("gui2.rules.limit"),
    GUI2_RULES_LEVEL("gui2.rules.level"),
    GUI2_RULES_HP("gui2.rules.hp"),
    GUI2_RULES_MULT("gui2.rules.mult"),
    GUI2_RULES_GROWTH("gui2.rules.growth"),
    GUI2_RULES_STAR("gui2.rules.star"),
    GUI2_RULES_DECAY("gui2.rules.decay"),
    GUI2_RULES_TIP("gui2.rules.tip"),
    GUI2_MANAGE_TITLE("gui2.manage.title"),
    GUI2_MANAGE_TEST("gui2.manage.test"),
    GUI2_MANAGE_LIST("gui2.manage.list"),
    GUI2_MANAGE_CHECK("gui2.manage.check"),
    GUI2_MANAGE_DELETE("gui2.manage.delete"),
    GUI2_MANAGE_RELOAD("gui2.manage.reload"),
    GUI2_MANAGE_DEBUG("gui2.manage.debug"),
    GUI2_TEST_TITLE("gui2.test.title"),
    GUI2_TEST_NORMAL("gui2.test.normal"),
    GUI2_TEST_INCENTIVE("gui2.test.incentive"),
    GUI2_TEST_OFFDAY("gui2.test.offday"),
    GUI2_HELP_TITLE("gui2.help.title"),
    GUI2_HELP_HELP("gui2.help.help"),
    GUI2_HELP_BINDING("gui2.help.binding"),
    GUI2_HELP_REWARD("gui2.help.reward"),
    GUI2_HELP_LIST("gui2.help.list"),
    GUI2_HELP_TOP("gui2.help.top"),
    GUI2_HELP_CHECK("gui2.help.check"),
    GUI2_HELP_DELETE("gui2.help.delete"),
    GUI2_HELP_RELOAD("gui2.help.reload"),
    GUI2_HELP_DEBUG("gui2.help.debug"),
    GUI2_BACK("gui2.back"),
    GUI2_PREV("gui2.prev"),
    GUI2_NEXT("gui2.next"),
    GUI2_CLOSE("gui2.close"),
    GUI2_MAIN("gui2.main"),
    GUI2_PAGE("gui2.page"),
    GUI2_BINDING_HINT("gui2.binding.hint"),
    GUI2_CHECK_HINT("gui2.check.hint"),
    GUI2_DELETE_HINT("gui2.delete.hint"),
    // ---- 基岩版新表单（v2 最终稿）----
    FORM2_BTN_MYRECORDS("form2.button.myrecords"),
    FORM2_BTN_STATUS("form2.button.status"),
    FORM2_BTN_RULES("form2.button.rules"),
    FORM2_BTN_MANAGE("form2.button.manage"),
    FORM2_STATUS_TITLE("form2.status.title"),
    FORM2_RECORDS_TITLE("form2.records.title"),
    FORM2_TOP_TITLE("form2.top.title"),
    FORM2_RULES_TITLE("form2.rules.title"),
    FORM2_MANAGE_TITLE("form2.manage.title"),
    FORM2_TEST_TITLE("form2.test.title"),
    FORM2_BACK("form2.back"),
    FORM2_PREV("form2.prev"),
    FORM2_NEXT("form2.next"),
    FORM2_PAGE("form2.page"),
    FORM2_TEST_NORMAL("form2.test.normal"),
    FORM2_TEST_INCENTIVE("form2.test.incentive"),
    FORM2_TEST_OFFDAY("form2.test.offday"),
    FORM2_TEST_PEAK("form2.test.peak"),
    FORM2_TEST_MAX("form2.test.max"),
    FORM2_RECORD_PEAK("form2.records.peak"),
    FORM2_RECORD_OFFPEAK("form2.records.offpeak"),
    FORM2_RECORD_NOREWARD("form2.records.noreward"),
    FORM2_MANAGE_TEST("form2.manage.test"),
    FORM2_MANAGE_LIST("form2.manage.list"),
    FORM2_MANAGE_CHECK("form2.manage.check"),
    FORM2_MANAGE_DELETE("form2.manage.delete"),
    FORM2_MANAGE_RELOAD("form2.manage.reload"),
    FORM2_MANAGE_DEBUG("form2.manage.debug"),
    FORM2_INPUT_TITLE("form2.input.title"),
    FORM2_INPUT_LABEL("form2.input.label"),
    FORM2_INPUT_PLACEHOLDER("form2.input.placeholder"),
    FORM2_INPUT_EMPTY("form2.input.empty");

    private static PlatformConfig messageConfig;

    private final String path;
    private String cacheString;
    private List<String> cacheStringList;

    Message(String path) {
        this.path = path;
    }

    public static void load(ConfigManager configManager) {
        messageConfig = configManager.getLangFile();
        for (Message m : values()) {
            m.cacheString = null;
            m.cacheStringList = null;
        }
    }

    private String getCachedString() {
        if (cacheString != null) {
            return cacheString;
        }
        String rawString = messageConfig.getString(path);
        if (rawString == null) {
            return null;
        }
        return cacheString = KBBSToperCore.platform().colorize(rawString);
    }

    private List<String> getCachedStringList() {
        if (cacheStringList != null) {
            return cacheStringList;
        }
        List<String> rawList = messageConfig.getStringList(path);
        if (rawList == null) {
            return Collections.emptyList();
        }
        return cacheStringList = Collections.unmodifiableList(
                rawList.stream()
                        .map(msg -> KBBSToperCore.platform().colorize(msg))
                        .collect(Collectors.toList())
        );
    }

    /**
     * 取语言文案。配置缺键时返回空串而不是 null，
     * 避免所有 "prefix + message" 拼接处出现字面量 "null"。
     */
    public String getString() {
        String s = getCachedString();
        return s == null ? "" : s;
    }

    /** 取不到时回退到 def，避免界面出现 "null"。 */
    public String getString(String def) {
        String s = getCachedString();
        return (s == null || s.isEmpty()) ? KBBSToperCore.platform().colorize(def) : s;
    }

    public List<String> getStringList() {
        return getCachedStringList();
    }
}
