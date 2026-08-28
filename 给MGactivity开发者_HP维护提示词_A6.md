# 给 MGactivity 开发者的对接提示词（KBBSToper v3.7.4_A6 · HP 上限维护移交）

> 转发对象：MGactivity 插件开发者（NoobLLiu 服的活跃度插件，负责把 KBBSToper 下发的奖励数值落地到游戏内）。
> 用途：KBBSToper 在 v3.7.4_A6 移除了"自己写玩家 `generic.max_health` 属性"的代码，HP 上限现在**完全由 MGactivity 负责**。本文件说明你们需要保证的行为，可直接转发给对面开发者。

---

## 一、背景：架构变了

KBBSToper 之前的版本（A5）在检测到顶帖发奖后，除了调用你们的 API，还**自己**直接 `Player.setMaxHealth(...)` 兜底（怕你们没应用）。

**A6 起这个兜底被删掉了。** 原因：用户要求 HP 上限统一由 MGactivity 维护。所以现在链路是：

```
KBBSToper 计算目标 HP（已按 hp-base=20 / hp-hard-cap=50 钳制成绝对值）
        │
        ├─① 优先：MGactivity Java API   setMaxHp(player, int)        ← 你们注册到 ServicesManager
        │
        └─② 回退：控制台命令            mgactivity setmaxhp <玩家> <值>   ← 你们 ApiExportCommand 的 setmaxhp 分支
```

**结论：你们 `setMaxHp`（以及命令回退 `setmaxhp`）必须可靠地把值应用到游戏内属性并持久化。** 否则玩家血量上限永不变——之前的 bug 就是这么来的。

---

## 二、你们必须保证的 4 点

### 1. `setMaxHp` 必须真正落到游戏内属性（不能只写文件）

拿到值后，对**在线玩家**必须调用 `Player.setMaxHealth(value)`，并把当前血量钳制不超上限：

```java
Player p = Bukkit.getPlayerExact(player);
if (p != null) {
    p.setMaxHealth(value);                       // 真正改游戏内属性
    p.setHealth(Math.min(p.getHealth(), value)); // 当前血量不要超过新上限
}
// 离线玩家：先存 playerdata.json，上线时由 onJoin 重应用（见第 3 点）
```

> ⚠️ 你们 **8690fee** 那次提交已经加了 `Bukkit.getPlayerExact → setMaxHealth` 的逻辑，请**保留并确认仍然生效**，不要被后续改动覆盖掉。

### 2. ⚠️ 下限钳制必须是 20，不是 30（最重要）

旧版你们可能把 `setMaxHp` 的下限钳到 **30**（那会儿 KBBSToper 的 `hp-base` 是 30）。

**现在 KBBSToper 下发的已经是 [20, 50] 区间内的绝对值**（hp-base=20，hp-hard-cap=50），你们**不要再二次抬到下界 30**。否则会出现：未顶过帖的玩家初始上限被错误抬到 30（用户当初报的 bug 就是这个）。

正确做法：直接采用 KBBSToper 传入的值，最多做个防 0/负数硬保险：

```java
// 只防极端值，不要设 30 下限
int v = value > 0 ? value : 20;
v = Math.min(50, Math.max(1, v));
```

### 3. 持久化 + 跨天保留 + 上线/重生重应用

- 写入 `playerdata.json`（你们已有）。
- `ActivityListener.onJoin` / `onRespawn` 必须调用 `applyMaxHp(player)`，把存档上限重新 `setMaxHealth`。
- **HP 是绝对值、永久累计、跨天保留，绝不清零、绝不停每日归位**（每日归位的是倍率 growth/exp，不是 HP）。

### 4. 命令回退 `setmaxhp <玩家> <值>` 与 API 走同一条逻辑

KBBSToper 在 MGactivity 未注册时会改发控制台命令 `mgactivity setmaxhp %PLAYER% %VALUE%`。你们 `ApiExportCommand` 的 `setmaxhp` 分支必须和 `setMaxHp` API 走**同一套**应用逻辑（满足上面 1/2/3）。你们已经有该 case，确认一致即可。

---

## 三、接口契约（确认你们实现的就是这个）

```java
// 由你们实现，onEnable 注册到 ServicesManager；KBBSToper core 持有同名接口
void setMaxHp(String player, int value);   // 绝对值写入 + 应用到游戏内属性 + 持久化
int  getMaxHp(String player);              // 可选：状态页展示"当前生命上限"，未实现返回 -1
```

- 倍率 `setGrowthMultiplier / setExperienceMultiplier`：取最大值、不叠加，每日由你们归位 1.0。
- 星光点：`addStarlightPoints` 你们可保留，**但 KBBSToper A6 起不再调用它**（星光点改走 EssentialsX 经济，`money give`）。不必为星光点做改动。

---

## 四、验收方式（用 KBBSToper 的 `bt debug open`）

1. 服主开代理后装上 KBBSToper **A6** jar + 你们最新 jar，重启服务器。
2. 控制台执行 `/bt debug open` 开启检测过程追踪。
3. 顶一个**没顶过帖**的小号 → 控制台应出现：
   `[debug] dispatch(xxx): 走 MGactivity Java API 下发倍率/HP`
   随后你们的日志/数据应显示该玩家 maxhp 被设为 **22**（20 基准 + 首顶 2）。
4. 进游戏确认该玩家血量上限 = 22（11 颗心）。
5. **重启服务器后再确认仍是 22**（验证跨天保留 + onJoin 重应用）。
6. 用 `mgactivity getmaxhp <玩家>` 或看 `playerdata.json`，确认存的是 **22 而不是 30**（验证第 2 点下限）。

任一项不符，说明 `setMaxHp` 还没完全满足上面 4 点，需要改。

---

## 五、一句话总结给开发者

> KBBSToper A6 不再碰玩家血量属性了，HP 全归你们。请保证 `setMaxHp` 真的 `Player.setMaxHealth` 落地、按传入值（已 [20,50]）处理、存盘并在上线/重生重应用；别把下限锁成 30，要与 hp-base=20 对齐。
