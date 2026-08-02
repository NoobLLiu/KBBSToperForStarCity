package mc233.fun.kbbstoper.core.commands;

/**
 * 重载钩子。平台模块如果有自己的配置文件（例如 Bukkit 的 gui.yml），
 * 在启动时注册一个实现，reload 时会被调到。
 */
public interface ReloadHook {

    void onReload();

    ReloadHook NOOP = () -> {
    };

    final class Holder {
        private static ReloadHook current = NOOP;

        private Holder() {
        }

        public static void set(ReloadHook hook) {
            current = (hook == null) ? NOOP : hook;
        }

        public static ReloadHook get() {
            return current;
        }
    }
}
