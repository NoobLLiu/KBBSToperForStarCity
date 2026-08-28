# KBBSToperForStarCity × MGactivity —— Java API 对接需求文档（致 MGactivity 开发者）

> 面向对象：MGactivity 插件开发者  
> 目的：请 MGactivity 提供一套**插件之间可直接调用的 Java 接口**，以替代 / 补充当前基于控制台命令的对接方式。  
> 关联文档：命令式对接仍保留作为回退，详见《MGactivity对接文档.md》（开发者版）。



---

## 0. 背景与动机

当前 KBBSToperForStarCity 通过服务端控制台下发 MGactivity 命令（如 `mgactivity setgrowthmultiplier %PLAYER% %VALUE%`）来发放奖励效果。命令式对接可用，但存在以下痛点：

1. 每次奖励都走控制台命令，有命令解析 / 权限 / 并发风险；
2. 倍率、HP、连签等数值需要字符串拼装后再解析，易出错；
3. 无法获得返回值（例如查询当前倍率）。

因此我们希望 MGactivity **以 Bukkit `ServicesManager` 注册一个 Java 接口实现**，KBBSToper 在运行时直接调用，实现原生插件对接。若 MGactivity 未安装或未注册，KBBSToper 自动回退到命令式下发，行为完全向后兼容。

---

## 1. 接口定义（请 MGactivity 实现）

接口已由 KBBSToper 在 core 模块中定义并随 jar 发布，全限定名如下（**包名与类名必须完全一致**，否则 `ServicesManager` 按 `Class` 精确匹配会失败）：

```java
package mc233.fun.kbbstoper.core.platform;

public interface MGactivityApi {

    /** 设置玩家成长值倍率（取最大值，不叠加）。 */
    void setGrowthMultiplier(String player, double value);

    /** 设置玩家经验值倍率（取最大值，不叠加）。 */
    void setExperienceMultiplier(String player, double value);

    /** 设置玩家生命值上限（绝对值写入，跨天保留）。 */
    void setMaxHp(String player, int value);

    /** 增加玩家连签中断计数（增量累加，立即生效）。 */
    void addStreakBreak(String player, int value);

    /** 增加玩家星光点（增量累加）。default 空实现：MGactivity 未覆写时静默，保持向后兼容。 */
    default void addStarlightPoints(String player, long value) {
        // 请覆写为: 委托 ActivityManager.addStarlightPoints(player, value)
    }

    // ---- 可选查询方法（default 返回 -1，供 KBBSToper 奖励提示显示"当前值"）----
    default double getGrowthMultiplier(String player) { return -1; }
    default double getExperienceMultiplier(String player) { return -1; }
    default double getGrowthValue(String player) { return -1; }      // 当前成长值(totalActivity)
    default long getStarlightPoints(String player) { return -1; }
}
```

### 1.1 语义约定（务必遵守）

| 方法                        | 含义    | 语义                                 | 范围 / 说明                                        |
| ------------------------- | ----- | ---------------------------------- | ---------------------------------------------- |
| `setGrowthMultiplier`     | 成长倍率  | **取最大值，不叠加相乘**；每日自动归位基准值（通常 `1.0`） | 建议 `[1.0, 5.0]`                                |
| `setExperienceMultiplier` | 经验倍率  | **取最大值，不叠加相乘**；每日自动归位基准值           | 建议 `[1.0, 5.0]`                                |
| `setMaxHp`                | 生命值上限 | **绝对值写入并持久化**，跨天保留，不要每日清零          | KBBSToper 已钳制到 `[30, 50]`，MGactivity 可再防御性钳制一次 |
| `addStreakBreak`          | 连签中断值 | **增量累加**，立即生效                      | 非负整数                                           |
| `addStarlightPoints`（default） | 星光点    | **增量累加**，立即生效；KBBSToper 优先走此 API，不可用时回退 Vault | 非负整数，**请务必覆写 default 空实现**          |

- `player` 为玩家游戏名（与顶帖绑定名一致，可能含中文 / 特殊字符），请确保按名解析正确。
- 倍率每日清零由 **MGactivity** 负责：KBBSToper 只下发 `set`，不下发 `reset`。
- `maxhp` 是长期累积属性，KBBSToper 下发的是「已累加并钳制后的绝对值」，MGactivity 直接设为该值即可。
- **可选查询方法**（`getGrowthMultiplier` / `getExperienceMultiplier` / `getGrowthValue` / `getStarlightPoints`）：
  建议覆写（default 返回 `-1`）。KBBSToper 奖励完成提示需要展示「当前成长值 / 星光点 / 倍率」时会调用；
  不覆写则提示中自动省略这些"当前值"，**不影响发奖本身**。对应实现：委托 `ActivityManager` 现有查询（`getGrowthMultiplier` / `getExperienceMultiplier` / `getPlayerData(name).getTotalActivity()` / `getStarlightPoints`）。

---

## 2. MGactivity 侧接入步骤

### 2.1 编译期依赖 KBBSToper core

为获得上述 `MGactivityApi` 接口类，MGactivity 需在编译期依赖 KBBSToper 的 core 模块（或仅复制本接口文件，但**包名与全限定名必须完全一致**）。推荐方式：把 `KBBSToper-Bukkit-3.7.4_A1.jar` 或 core 模块产物作为 `compileOnly` / `provided` 依赖引入。

### 2.2 在 `plugin.yml` 声明软依赖

```yaml
softdepend:
  - KBBSToper
```

`softdepend` 确保服务端在加载 MGactivity 之前先加载 KBBSToper，从而两端共享同一个 `MGactivityApi` Class 对象（`ServicesManager` 按 `Class` 精确匹配）。

### 2.3 注册 / 注销实现

```java
public class MGactivityPlugin extends JavaPlugin {

    private MGactivityApi apiImpl;

    @Override
    public void onEnable() {
        apiImpl = new MGactivityApiImpl(this); // 你的实现
        getServer().getServicesManager()
                .register(MGactivityApi.class, apiImpl, this, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        if (apiImpl != null) {
            getServer().getServicesManager().unregister(MGactivityApi.class, this);
        }
    }
}
```

### 2.4 最小实现骨架（参考）

```java
public class MGactivityApiImpl implements MGactivityApi {

    private final MGactivityPlugin plugin;

    public MGactivityApiImpl(MGactivityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void setGrowthMultiplier(String player, double value) {
        // max(当前, 新值); 每日自动归 1.0
    }

    @Override
    public void setExperienceMultiplier(String player, double value) {
        // max(当前, 新值); 每日自动归 1.0
    }

    @Override
    public void setMaxHp(String player, int value) {
        int v = Math.max(30, Math.min(50, value)); // 防御性钳制
        // 绝对值写入并持久化
    }

    @Override
    public void addStreakBreak(String player, int value) {
        // 增量累加，立即生效
    }

    @Override
    public void addStarlightPoints(String player, long value) {
        // 星光点增量累加（建议委托 ActivityManager.addStarlightPoints，与命令语义一致）
    }
}
```

---

## 3. KBBSToper 侧如何消费（供参考，无需 MGactivity 处理）

KBBSToper 在奖励判定后调用：

```java
MGactivityApi mg = Bukkit.getServer().getServicesManager().load(MGactivityApi.class);
if (mg != null) {
    mg.setGrowthMultiplier(player, growthMult);   // 首顶
    mg.setExperienceMultiplier(player, expMult);  // 额外
    mg.setMaxHp(player, newMaxHp);                // 首顶 / 附加
    // 断签: mg.addStreakBreak(player, val)
} else {
    // 回退：下发 reward.mgactivity: 下的控制台命令（见《MGactivity对接文档.md》）
}
```

- 若 `load()` 返回 `null`（MGactivity 未安装 / 未注册），KBBSToper 自动走命令式回退，现有配置与命令完全兼容。
- 因此 MGactivity 是否提供该接口，对 KBBSToper 是**可选增强**，不影响已有部署。

---

## 4. 成长值 +100 的发放说明

- 命令式：MGactivity 现已提供 `mgactivity addgrowthpoints %PLAYER% %VALUE%`（直接累加成长值）。
  KBBSToper 侧只需在配置 `reward.growth-grant-commands:` 填入该命令即可生效（无需改代码）：
  ```yaml
  growth-grant-commands:
    - 'mgactivity addgrowthpoints %PLAYER% %VALUE%'
  ```
- API 式（可选，非阻塞）：若 MGactivity 在 Java API 中补充 `addGrowth(String player, double value)`，
  KBBSToper 后续可改为优先走 API 发放成长值。此项可选。

---

## 5. 联调 checklist（MGactivity 开发者自测）

- [ ] 已实现 `mc233.fun.kbbstoper.core.platform.MGactivityApi` 四个抽象方法并覆写 `addStarlightPoints`，包名完全一致
- [ ] 编译期依赖 KBBSToper core（或复制接口文件，全限定名一致）
- [ ] `plugin.yml` 声明 `softdepend: [KBBSToper]`
- [ ] `onEnable` 注册、`onDisable` 注销 `MGactivityApi`
- [ ] `setGrowthMultiplier` / `setExperienceMultiplier` 为「取最大值」语义，且次日自动归 `1.0`
- [ ] `setMaxHp` 为绝对值写入，跨天保留，范围钳制 `[30, 50]`，且应用到在线玩家
- [ ] `addStreakBreak` 为增量写入，立即生效
- [ ] **覆写 `addStarlightPoints`**（委托 `ActivityManager.addStarlightPoints`），星光点才能经 API 到账
- [ ] 玩家名（含中文 / 特殊字符）作为参数可正确解析
- [ ] 未注册时 KBBSToper 自动回退命令（可在测试服卸载 MGactivity 验证无报错）

---

## 6. 版本与发布说明

- 本接口随 **KBBSToperForStarCity v3.7.4_A1** 起提供。
- KBBSToper 侧代码已改造为「API 优先、命令回退」，无需 MGactivity 同步发版即可工作（回退路径）。
- MGactivity 提供实现后，KBBSToper 将自动切换为原生 Java 对接，无需 KBBSToper 改配置。
