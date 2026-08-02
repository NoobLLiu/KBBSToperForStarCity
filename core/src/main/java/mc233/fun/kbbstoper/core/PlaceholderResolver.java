package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.PlatformPlayer;
import mc233.fun.kbbstoper.core.sql.SQLManager;
import mc233.fun.kbbstoper.core.sql.SQLer;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 占位符求值。两个平台的 PlaceholderAPI 实现互不兼容，
 * 但取值逻辑一致，所以放在 core 里。
 *
 * <p>注意：lastpost 与 extrarewards 会发起一次网页抓取，
 * 不要放在计分板这类高频刷新的位置。</p>
 */
public final class PlaceholderResolver {

    private static final Pattern TOP_PATTERN = Pattern.compile("^top_[1-9]\\d*$");

    private PlaceholderResolver() {
    }

    /**
     * 求一个占位符的值。
     *
     * @param player     发起请求的玩家，可为 null（表示无玩家上下文）
     * @param identifier 去掉前缀后的标识，例如 bbsid
     * @return 无法识别时返回 null
     */
    public static String resolve(PlatformPlayer player, String identifier) {
        SQLer sql = SQLManager.getSQLer();
        if (sql == null) {
            return null;
        }

        if (player != null) {
            Poster poster = sql.getPoster(player.getUniqueId().toString());
            if (identifier.equals("bbsid")) {
                return poster == null ? Message.GUI_NOTBOUND.getString() : poster.getBbsname();
            }
            if (identifier.equals("posttimes")) {
                return poster == null
                        ? Message.GUI_NOTBOUND.getString()
                        : String.valueOf(poster.getTopStates().size());
            }
        }
        if (identifier.equals("pageid")) {
            return Option.BBS_URL.getString();
        }
        if (identifier.equals("pageurl")) {
            return "https://" + Option.WEBSITE.getString() + "/thread-" + Option.BBS_URL.getString() + "-1-1.html";
        }
        if (identifier.equals("lastpost")) {
            Crawler crawler = Crawler.fetch();
            if (!crawler.visible) {
                return Message.GUI_PAGENOTVISIBLE.getString();
            }
            return crawler.Time.isEmpty() ? "----" : crawler.Time.get(0);
        }
        if (identifier.equals("extrarewards")) {
            String extra = Util.getExtraReward(Crawler.fetch());
            return extra == null ? Message.NONE.getString() : extra;
        }
        if (TOP_PATTERN.matcher(identifier).matches()) {
            int rank = Integer.parseInt(identifier.split("_")[1]);
            int index = rank - 1;
            List<Poster> listposter = sql.getTopPosters();
            if (listposter != null && index < listposter.size()) {
                Poster p = listposter.get(index);
                return Message.POSTERPLAYER.getString() + ":" + p.getName() + " "
                        + Message.POSTERID.getString() + ":" + p.getBbsname() + " "
                        + Message.POSTERNUM.getString() + ":" + p.getCount();
            }
        }
        return null;
    }
}
