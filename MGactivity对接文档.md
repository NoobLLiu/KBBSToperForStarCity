# KBBSToperForStarCity × MGactivity 对接文档（开发者版）

> 面向对象：MGactivity 插件开发者
> 用途：说明 KBBSToperForStarCity（苦力怕BBS顶贴奖励 StarCity 定制版）如何调用 MGactivity 的「导出接口」，以及 MGactivity 需要提供的命令与语义。

---

## 一、对接方式总览

KBBSToper 在**每次玩家有效顶贴触发奖励**时，通过服务端控制台下发一组 MGactivity 命令（非直接调 Java API，而是以控制台指令形式执行，等价于「导出接口」）。

- 调用方：KBBSToper（Bukkit 端 `dispatchConsoleCommand`）
- 执行身份：控制台（Console Sender），因此 MGactivity 的相关命令必须**允许控制台执行**（无权限拦截）
- 触发频率：每个奖励事件下发若干条命令（首顶 / 额外 / 附加 各不同，见下文）
- 占位符：`%PLAYER%` = 玩家游戏名（字符串）；`%VALUE%` = 数值（由 KBBSToper 计算后填入，整数或小数）

> 说明：KBBSToper **不会**使用 `eco give` 之类的通用奖励命令，全部走 MGactivity 导出接口；星光点（货币）走 Vault，不在此文档范围。

---

## 二、KBBSToper 当前实际下发的命令（必需）

以下命令 KBBSToper **一定会调用**，MGactivity 必须实现并保证控制台可执行：

| 命令模板（config 默认值） | 含义 | 下发时机 | 示例 |
| --- | --- | --- | --- |
| `mgactivity setgrowthmultiplier %PLAYER% %VALUE%` | 设置成长倍率 | **首顶**奖励 | `mgactivity setgrowthmultiplier Steve 1.25` |
| `mgactivity setmaxhp %PLAYER% %VALUE%` | 设置生命值上限（HP 上限） | 首顶 / 附加（累加） | `mgactivity setmaxhp Steve 34` |
| `mgactivity addstreakbreak %PLAYER% %VALUE%` | 增加连签中断值 | 玩家**当日未有效顶贴**、次日补扣 | `mgactivity addstreakbreak Steve 2` |

命令模板可在 `config.yml` 的 `reward.mgactivity:` 节点自定义（KBBSToper 仅做字符串替换后下发），因此 MGactivity 侧的**命令名/参数顺序需与模板一致**。

---

## 三、期望的语义约定（MGactivity 需保证）

### 1. 倍率（growth multiplier）
- **不叠加，取最大值**：同一天多次顶贴，KBBSToper 每次都下发相同目标值（如 `1.25`）；MGactivity 应 `max(当前倍率, 新值)`，不得相乘或累加。
- **每日自动清零**：倍率应在服务器每日重置为基准值（通常为 `1.0`）。KBBSToper 只发 `set`，**不发 reset**，因此每日清零由 MGactivity 负责。
- 倍率应实时作用于玩家的成长值获取。

### 2. 生命值上限（maxhp）
- KBBSToper 下发的是**已累加并钳制后的绝对值**（整数，范围 `[30, 50]`）。
- MGactivity 应将该玩家 HP 上限**设为该值**（绝对值，非增量），并持久化。
- **不每日清零**：HP 上限是长期累积属性（顶贴越多越高，封顶 50），MGactivity 应跨天保留，不要每日重置。
- 若收到超出 `[30, 50]` 的值，建议 MGactivity 自行再钳制一次以防御异常。

### 3. 连签中断（streakbreak）
- KBBSToper 在「玩家某天未进行有效顶贴」的判定后，于次日下发 `addstreakbreak <玩家> 2`。
- MGactivity 应**立即**将连签中断值累加（增量，非绝对值）。
- 该值用于活跃度/连签系统的中断惩罚。

---

## 四、建议同时提供的查询 / 重置接口（可选，便于联调）

KBBSToper 当前未调用，但建议 MGactivity 一并暴露，方便排查与未来扩展：

| 命令 | 说明 |
| --- | --- |
| `mgactivity getgrowthmultiplier %PLAYER%` | 查询当前成长倍率 |
| `mgactivity resetgrowthmultiplier %PLAYER%` | 重置成长倍率为基准 |
| `mgactivity getmaxhp %PLAYER%` | 查询当前 HP 上限 |

---

## 五、当前缺口 / 需 MGactivity 补充的接口

KBBSToper 的需求中，每次有效顶贴还应发放 **「成长值 +100」**，但 MGactivity 的导出接口**目前没有「增加成长值」的命令**，因此该部分暂未实际发放。

- KBBSToper 侧已在 `config.yml` 预留 `reward.growth-grant-commands:` 列表（支持 `%PLAYER%` 与 `%VALUE%` 占位符，`%VALUE%` 会被替换为 `100`）。
- **需要 MGactivity 提供**类似下面的命令（命令名可协商，配置里填对应模板即可）：
  - `mgactivity addgrowth %PLAYER% %VALUE%`  —— 给玩家增加成长值（增量）
- 提供后，KBBSToper 只需在 `reward.growth-grant-commands` 填入该命令即可启用，无需改代码。

---

## 六、联调 checklist（MGactivity 开发者自测）

- [ ] 上述 4 条必需命令已注册，且**控制台可直接执行**（无需 op 权限）
- [ ] `setgrowthmultiplier` 为「取最大值」语义，且次日自动归 `1.0`
- [ ] `setmaxhp` 为绝对值写入，跨天保留，范围钳制 `[30, 50]`
- [ ] `addstreakbreak` 为增量写入，立即生效
- [ ] 玩家名（含中文/特殊字符）作为参数可正确解析
- [ ] （可选）`addgrowth` 已实现并通知 KBBSToper 侧填入配置

---

## 七、附：KBBSToper 侧配置对照（config.yml）

```yaml
reward:
    values:
        growth-multiplier: 1.25
        hp-step: 2
        hp-base: 30
        hp-hard-cap: 50
        growth-per-reward: 100      # 待 MGactivity 提供 addgrowth 后启用
        additional-hp-step: 2
        additional-growth: 100       # 待启用
        star-points: 300             # 走 Vault，不在此文档
        streak-break-daily: 2
    mgactivity:
        growth-multiplier-cmd: 'mgactivity setgrowthmultiplier %PLAYER% %VALUE%'
        setmaxhp-cmd: 'mgactivity setmaxhp %PLAYER% %VALUE%'
        streak-break-cmd: 'mgactivity addstreakbreak %PLAYER% %VALUE%'
    growth-grant-commands: []        # 填入 addgrowth 命令后启用成长值 +100
```
