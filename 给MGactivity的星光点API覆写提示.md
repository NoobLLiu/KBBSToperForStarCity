# 给 MGactivity 开发者的覆写提示（星光点 Java API）

> 提出方：KBBSToperForStarCity（顶贴奖励插件）
> 用途：把本文件整段发给 MGactivity 开发者 / 开发 AI 即可，无需额外解释。

---

## 背景

KBBSToper 已把「星光点」奖励的发放方式改为**优先走 Java API**：

- 接口：`mc233.fun.kbbstoper.core.platform.MGactivityApi`
- 方法：`addStarlightPoints(String player, long value)`
- 该方法是 `default` 空实现（KBBSToper 为兼容旧版特意设计）：**你若不覆写，星光点不会到账，但也不会报错**。

因此请你在实现类中补一处覆写，星光点才能真实累加。

## 必改项：覆写 addStarlightPoints

在你现有的 `MGactivityApiImpl`（即 `implements mc233.fun.kbbstoper.core.platform.MGactivityApi` 的那个类，位于 `cn.gmzc.mgactivitys.api`）中新增：

```java
@Override
public void addStarlightPoints(String player, long value) {
    if (!activityManager.addStarlightPoints(player, value)) {
        logger.warning("[MGactivityApi] addStarlightPoints rejected: player=" + player + ", value=" + value);
    }
}
```

- `activityManager` 就是你现有的 `ActivityManager` 实例，其 `addStarlightPoints(String, long)` 已实现（增量累加、持久化），直接委托即可。
- 语义约定：**增量累加、立即生效、value 非负**，与 `mgactivity addstarlightpoints %PLAYER% <数值>` 命令完全一致。

## 注意事项（三选一都行，别踩坑）

1. **覆写的必须是 `mc233.fun.kbbstoper.core.platform.MGactivityApi`**，不是 `cn.gmzc.mgactivitys.api.MGActivityApi`（后者是你自己的 API 入口类，ServicesManager 按 Class 精确匹配，注册/加载的是前者）。你现在 `MGactivityApiImpl implements MGactivityApi` 的写法已经是正确的，只需加方法。
2. 你仓库里 `compile-only/mc233/fun/kbbstoper/core/platform/MGactivityApi.java`（编译期桩）请**同步为带 `default addStarlightPoints` 的新版**，或直接用 KBBSToper 最新 jar 作为 compileOnly 依赖，避免桩与运行时接口不一致。
3. 重新构建 jar，**给出版本号**（当前 plugin.yml 为 1.0.2），服务器替换 jar 后重启。

## 验收标准（改完自测）

1. 控制台执行 `mgactivity addstarlightpoints <玩家名> 300` → 玩家星光点 +300（`/acti` 面板或 `playerdata.json` 的 `starlightPoints` 字段可见）。
2. KBBSToper 顶帖触发附加奖励（配置 `reward.values.star-points: 300`）后，该玩家星光点 +300。
3. 重复发放可累加，负数/空值被拒绝且不崩溃。

## 顺带（可选）

若你愿意在 Java API 中再补 `addGrowth(String player, double value)`（成长值增量），KBBSToper 后续可以把「成长值 +100」也改为走 API；当前该奖励通过命令 `mgactivity addgrowthpoints %PLAYER% %VALUE%` 已可生效，此项非阻塞。
