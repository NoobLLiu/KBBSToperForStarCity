package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.Platform;
import mc233.fun.kbbstoper.core.platform.PlatformConfig;

import java.io.File;
import java.util.List;

/** config.yml 的键位枚举。 */
public enum Option {

    DEBUG("debug"),
    DATABASE_TYPE("database.type"),
    DATABASE_PREFIX("database.prefix"),
    DATABASE_TIMINGRECONNECT("database.timingreconnect"),
    DATABASE_MYSQL_IP("database.mysql.ip"),
    DATABASE_MYSQL_PORT("database.mysql.port"),
    DATABASE_MYSQL_DATABASE("database.mysql.database"),
    DATABASE_MYSQL_USER("database.mysql.user"),
    DATABASE_MYSQL_PASSWORD("database.mysql.password"),
    DATABASE_MYSQL_SSL("database.mysql.ssl"),
    DATABASE_SQLITE_FOLDER("database.sqlite.folder"),
    DATABASE_SQLITE_DATABASE("database.sqlite.database"),
    BBS_URL("bbs.url"),
    BBS_PAGESIZE("bbs.pagesize"),
    BBS_CHANGEIDCOOLDOWN("bbs.changeidcooldown"),
    BBS_QUERYCOOLDOWN("bbs.querycooldown"),
    BBS_JOINMESSAGE("bbs.joinmessage"),
    PROXY_ENABLE("proxy.enable"),
    PROXY_IP("proxy.ip"),
    PROXY_PORT("proxy.port"),
    GUI_TOPPLAYERS("gui.topplayers"),
    GUI_DISPLAYHEADSKIN("gui.displayheadskin"),
    GUI_USECHATGETID("gui.usechatgetid"),
    GUI_CANCELKEYWORDS("gui.cancelkeywords"),
    REWARD_AUTO("reward.auto"),
    REWARD_PERIOD("reward.period"),
    REWARD_INTERVAL("reward.interval"),
    REWARD_TIMES("reward.times"),
    REWARD_PEAK_START("reward.peak.start-hour"),
    REWARD_PEAK_END("reward.peak.end-hour"),
    REWARD_DAILY_FIRST("reward.daily.first-limit"),
    REWARD_DAILY_EXTRA("reward.daily.extra-limit"),
    REWARD_INACTIVE_HOURS("reward.inactive-hours-for-additional"),
    REWARD_VAL_GROWTH_MULT("reward.values.growth-multiplier"),
    REWARD_VAL_EXP_MULT("reward.values.experience-multiplier"),
    REWARD_VAL_HP_STEP("reward.values.hp-step"),
    REWARD_VAL_HP_BASE("reward.values.hp-base"),
    REWARD_VAL_HP_CAP("reward.values.hp-hard-cap"),
    REWARD_VAL_GROWTH("reward.values.growth-per-reward"),
    REWARD_VAL_ADD_HP_STEP("reward.values.additional-hp-step"),
    REWARD_VAL_ADD_GROWTH("reward.values.additional-growth"),
    REWARD_VAL_STAR("reward.values.star-points"),
    REWARD_VAL_STREAK("reward.values.streak-break-daily"),
    REWARD_MG_GROWTH_CMD("reward.mgactivity.growth-multiplier-cmd"),
    REWARD_MG_EXP_CMD("reward.mgactivity.experience-multiplier-cmd"),
    REWARD_MG_MAXHP_CMD("reward.mgactivity.setmaxhp-cmd"),
    REWARD_MG_STREAK_CMD("reward.mgactivity.streak-break-cmd"),
    REWARD_GROWTH_GRANT("reward.growth-grant-commands"),
    REWARD_ADDITIONAL_ENABLE("reward.additional.enable"),
    REWARD_VAULT_STAR("reward.vault.enable-star-points"),
    REWARD_COMMANDS("reward.commands"),
    REWARD_INCENTIVEREWARD_ENABLE("reward.incentivereward.enable"),
    REWARD_INCENTIVEREWARD_EXTRA("reward.incentivereward.extra"),
    REWARD_INCENTIVEREWARD_PERIOD("reward.incentivereward.period"),
    REWARD_INCENTIVEREWARD_COMMANDS("reward.incentivereward.commands"),
    REWARD_OFFDAYREWARD_ENABLE("reward.offdayreward.enable"),
    REWARD_OFFDAYREWARD_EXTRA("reward.offdayreward.extra"),
    REWARD_OFFDAYREWARD_OFFDAYS("reward.offdayreward.offdays"),
    REWARD_OFFDAYREWARD_COMMANDS("reward.offdayreward.commands"),
    WEBSITE("website");

    private static PlatformConfig config;
    private final String path;

    Option(String path) {
        this.path = path;
    }

    public static void load(Platform platform) {
        config = platform.loadConfig(new File(platform.getDataFolder(), "config.yml"));
    }

    public String getString() {
        return config.getString(path);
    }

    public List<String> getStringList() {
        return config.getStringList(path);
    }

    public boolean getBoolean() {
        return config.getBoolean(path);
    }

    public int getInt() {
        return config.getInt(path);
    }

    /** 读取小数(倍率等)。键缺失或非数字时返回 0.0。 */
    public double getDouble() {
        String s = config.getString(path);
        if (s == null || s.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
