package mc233.fun.kbbstoper.bukkit.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import mc233.fun.kbbstoper.bukkit.BukkitPlatform;
import mc233.fun.kbbstoper.bukkit.BukkitPlayer;
import mc233.fun.kbbstoper.core.PlaceholderResolver;
import org.bukkit.entity.Player;

/** %bbstoper_xxx% 占位符。取值逻辑在 core 的 PlaceholderResolver。 */
public class PAPIExpansion extends PlaceholderExpansion {

    private final String author;
    private final String version;

    public PAPIExpansion(BukkitPlatform platform) {
        this.author = platform.getPluginAuthors().toString();
        this.version = platform.getPluginVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getAuthor() {
        return author;
    }

    @Override
    public String getIdentifier() {
        return "bbstoper";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        return PlaceholderResolver.resolve(
                player == null ? null : new BukkitPlayer(player),
                identifier
        );
    }
}
