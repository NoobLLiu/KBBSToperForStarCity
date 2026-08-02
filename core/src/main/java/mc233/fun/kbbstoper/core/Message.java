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
    GUI_INCENTIVEREWARDS("gui.incentiverewards"),
    GUI_OFFDAYREWARDS("gui.offdayrewards"),
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
    FORM_POST_TITLE("form.post.title"),
    FORM_POST_CONTENT("form.post.content");

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
