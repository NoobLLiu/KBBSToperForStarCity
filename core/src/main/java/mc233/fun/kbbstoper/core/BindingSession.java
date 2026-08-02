package mc233.fun.kbbstoper.core;

import mc233.fun.kbbstoper.core.platform.PlatformSender;

/**
 * 绑定输入会话。
 * Bukkit 端通过临时监听聊天来收集论坛 ID，因此需要在绑定流程结束时注销监听；
 * Nukkit 端用表单一次性拿到输入，不存在会话，注册一个空实现即可。
 */
public interface BindingSession {

    /** 绑定流程结束（成功、失败或取消）时调用。 */
    void finish(PlatformSender sender);

    /** 默认实现：什么都不做。 */
    BindingSession NOOP = sender -> {
    };

    // ---- 全局注册点 ----

    final class Holder {
        private static BindingSession current = NOOP;

        private Holder() {
        }

        public static void set(BindingSession session) {
            current = (session == null) ? NOOP : session;
        }

        public static BindingSession get() {
            return current;
        }
    }
}
