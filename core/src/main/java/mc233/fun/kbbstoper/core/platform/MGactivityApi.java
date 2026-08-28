package mc233.fun.kbbstoper.core.platform;

/**
 * MGactivity 插件对 KBBSToper 暴露的对接接口。
 *
 * <p>本接口由 <b>MGactivity 插件实现</b>，并在其 {@code onEnable} 中通过 Bukkit 的
 * {@link org.bukkit.plugin.ServicesManager} 注册；KBBSToper 在运行时通过
 * {@link Platform#getMGactivityApi()} 取得实现并直接调用，从而绕过控制台命令，
 * 实现插件之间的原生 Java 对接（更稳、更快、无权限/命令解析风险）。</p>
 *
 * <h2>为什么接口放在 KBBSToper 的 core 里</h2>
 * <p>为了保证 {@code ServicesManager} 按 {@link Class} 精确匹配，MGactivity 与 KBBSToper
 * 两端必须加载的是<b>同一个</b> {@code MGactivityApi} Class 对象。因此本接口随 KBBSToper
 * 的 core 模块发布，MGactivity 在编译期依赖 KBBSToper core（或仅复制本文件，但包名与全限定名
 * 必须完全一致），并在 {@code plugin.yml} 中声明 {@code softdepend: [KBBSToper]}，使服务端把
 * KBBSToper 的 jar 挂到 MGactivity 的类加载器上。</p>
 *
 * <h2>MGactivity 侧注册示例</h2>
 * <pre>{@code
 * @Override
 * public void onEnable() {
 *     MGactivityApi api = new MGactivityApiImpl(this);
 *     getServer().getServicesManager()
 *             .register(MGactivityApi.class, api, this, ServicePriority.Normal);
 * }
 *
 * @Override
 * public void onDisable() {
 *     getServer().getServicesManager().unregister(MGactivityApi.class, this);
 * }
 * }</pre>
 *
 * <h2>语义约定（务必遵守）</h2>
 * <ul>
 *   <li>倍率（growth / experience）：<b>不叠加，取最大值</b>；每日由 MGactivity 自动归位基准值（通常 1.0）。</li>
 *   <li>maxhp：KBBSToper 下发的是<b>已累加并钳制后的绝对值</b>（整数，约定范围 [30, 50]），
 *       应绝对值写入并持久化，跨天保留，不要每日清零。</li>
 *   <li>streakbreak：<b>增量</b>累加，立即生效。</li>
 *   <li>玩家名与顶帖绑定名一致；含中文/特殊字符时必须可正确解析。</li>
 * </ul>
 *
 * <p>若 MGactivity 未安装或未注册本接口，{@link Platform#getMGactivityApi()} 返回 {@code null}，
 * KBBSToper 自动回退到 {@code reward.mgactivity:} 下的控制台命令模板（见《MGactivity对接文档.md》）。</p>
 */
public interface MGactivityApi {

    /**
     * 设置玩家成长值倍率（取最大值，不叠加）。
     *
     * @param player 玩家名（与顶帖绑定名一致）
     * @param value  倍率，建议范围 [1.0, 5.0]
     */
    void setGrowthMultiplier(String player, double value);

    /**
     * 设置玩家经验值倍率（取最大值，不叠加）。
     *
     * @param player 玩家名
     * @param value  倍率，建议范围 [1.0, 5.0]
     */
    void setExperienceMultiplier(String player, double value);

    /**
     * 设置玩家生命值上限（绝对值写入，跨天保留）。
     *
     * @param player 玩家名
     * @param value  生命值上限，有效范围由 MGactivity 钳制（StarCity 约定 [30, 50]）
     */
    void setMaxHp(String player, int value);

    /**
     * 增加玩家连签中断计数（增量累加，立即生效）。
     *
     * @param player 玩家名
     * @param value  增量，非负
     */
    void addStreakBreak(String player, int value);

    /**
     * 增加玩家星光点（增量累加，立即生效）。
     *
     * <p>此方法为 {@code default} 空实现：MGactivity 未覆写时静默无效果，
     * 从而保证旧版 MGactivity（未实现星光点 API）无需改代码即可与新版 KBBSToper 共存。
     * MGactivity 应在实现类中覆写本方法，委托给其 {@code ActivityManager.addStarlightPoints(player, value)}。</p>
     *
     * @param player 玩家名
     * @param value  增量，非负
     */
    default void addStarlightPoints(String player, long value) {
        // 默认空实现：MGactivity 未实现时静默（调用方自行决定是否回退 Vault）
    }
}
