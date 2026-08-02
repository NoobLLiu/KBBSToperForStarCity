package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.PlatformSender;

import java.util.List;

/**
 * 子命令处理器。
 * handle() 完成业务逻辑，tabComplete() 返回二级参数建议（可返回 null）。
 */
public interface CommandHandler {

    void handle(PlatformSender sender, String[] args);

    default List<String> tabComplete(PlatformSender sender, String[] args) {
        return null;
    }
}
