# 给 MGactivity 开发者的提示词汇总

> 转发对象：MGactivity 插件开发者（NoobLLiu 服活跃度插件）。
> 汇总了 KBBSToper **A6 / A7** 两轮对接中，需要 MGactivity 侧**确认或改动**的全部事项。
> 每项都是可直接转发给对面开发者的独立提示词。

---

## 提示词一（A6，仍然有效）：生命值上限维护已移交给你们

> 背景：KBBSToper v3.7.4_A6 起**删除了自己直接写玩家 `generic.max_health` 属性**的代码。
> 现在 HP 上限的"存储 + 应用到游戏内属性 + 跨天保留"**全部由 MGactivity 负责**。
> KBBSToper 只计算目标值，通过两条通道下发：① Java API `setMaxHp(player, int)`；② 回退控制台命令 `mgactivity setmaxhp <玩家> <值>`。

**请你们保证以下 4 点：**

1. **`setMaxHp` 必须真正落到游戏内属性**（不能只写 playerdata.json）：
   ```java
   Player p = Bukkit.getPlayerExact(player);
   if (p != null) {
       p.setMaxHealth(value);                       // 真正改游戏内属性
       p.setHealth(Math.min(p.getHealth(), value)); // 当前血量不要超过新上限
   }
   // 离线玩家：存 playerdata.json，上线时由 onJoin 重应用
   ```
   （你们 **8690fee** 那次提交已加过这个逻辑，请保留并确认仍生效。）

2. **⚠️ 下限钳制必须是 20，不是 30**：
   旧版可能把下限钳到 30（当时 KBBSToper 的 `hp-base` 是 30）。
   **现在 KBBSToper 下发的已是 [20, 50] 区间内的绝对值**（hp-base=20，hp-hard-cap=50），
   你们**不要再二次抬到下界 30**，否则未顶过帖的玩家初始上限会被错误抬到 30。
   ```java
   int v = value > 0 ? value : 20;
   v = Math.min(50, Math.max(1, v));   // 只防极端值，不要设 30 下限
   ```

3. **持久化 + 跨天保留 + 上线/重生重应用**：
   写入 playerdata.json；`ActivityListener.onJoin` / `onRespawn` 调用 `applyMaxHp(player)` 重新 `setMaxHealth`。
   **HP 是绝对值、永久累计、跨天保留，绝不清零、绝不每日归位**（每日归位的只有倍率）。

4. **命令回退 `setmaxhp` 与 API 走同一条逻辑**：
   `ApiExportCommand` 的 `setmaxhp` 分支必须和 `setMaxHp` API 用同一套应用逻辑（满足上面 1/2/3）。

**验收**：服主装 A6+ jar 重启 → 控制台 `/bt debug open` → 顶一个没顶过帖的小号 →
日志出现 `[debug] dispatch(xxx): 走 MGactivity Java API 下发倍率/HP`；
进游戏确认该玩家血量上限 = **22**（20 基准 + 首顶 2），重启后仍是 22，且 playerdata 里存的是 **22 不是 30**。

---

## 提示词二（A7，本次新增）：命令下发的内容要在 `/actistatus` 里显示

> 背景：KBBSToper v3.7.4_A7 起，**成长值（数值）改走控制台命令**发放：
> ```yaml
> reward:
>   growth-grant-commands:
>     - 'mgactivity addgrowthpoints %PLAYER% %VALUE%'
> ```
> 即每次有效奖励，KBBSToper 会以**控制台身份**执行 `mgactivity addgrowthpoints <玩家> <值>`。
> 诉求：**这些通过命令下发的内容，玩家执行 `/actistatus` 时要能看到**（尤其是成长值）。

**请你们保证：**

1. **`addgrowthpoints` 必须写入 `/actistatus` 读取的同一份数据**：
   增量累加到 `/actistatus` 展示所用的那个 `ActivityData` 字段（成长值 / 总活跃值），
   **不要另起一个新字段**，否则客服看到 `/actistatus` 里成长值没变，会以为奖励没发。

2. **立即持久化**：命令执行后立刻 `save`（写 playerdata.json），
   不要只改内存等定时存档——否则玩家立刻 `/actistatus` 看不到，甚至重启后丢失。

3. **`/actistatus` 展示需包含"成长值"这一项**（若已有则忽略本条）。
   建议 `/actistatus` 至少展示这些由 KBBSToper 下发/影响的字段：
   - 成长值（数值，命令 `addgrowthpoints` 累加）
   - 成长倍率（`setGrowthMultiplier` API，或回退命令 `setgrowthmultiplier`）
   - 生命上限（`setMaxHp` API，或回退命令 `setmaxhp`）
   - 连签中断（`addStreakBreak` API，或回退命令 `addstreakbreak`）

4. **Java API 与控制台命令必须写同一份数据**：
   避免"命令写 A 字段、API 写 B 字段、`/actistatus` 读 C 字段"的不一致。
   KBBSToper 在 MG API 可用时走 API、不可用时才走命令，两条路的效果必须完全一致。

5. **（建议）覆写 `getGrowthValue` 返回真实成长值**：
   KBBSToper A7 在命令下发后会**回读** `getGrowthValue(player)` 做校验，
   若你们未覆写（默认返回 -1），KBBSToper 就无法自动确认命令是否生效，只能让服主手动核 `/actistatus`。
   覆写后，服主 `/bt debug open` 能直接看到：
   `[debug] dispatch(xxx): 命令下发后回读成长值 = 1234.0 (应能在 /actistatus 看到)`

**验收**：
- 玩家顶帖拿到奖励 → 该玩家执行 `/actistatus`，**成长值应即时 +100**（或配置的 `growth-per-reward` 值）。
- 服主 `/bt debug open` 后顶贴，控制台应能看到：
  `[debug] dispatch(xxx): 控制台执行 -> mgactivity addgrowthpoints 玩家名 100`
  紧接着一行 `命令下发后回读成长值 = ...`（若你们覆写了 `getGrowthValue`）。

---

## ✅ 已确认**不需要**对面改动的事项

- **"增加成长值"的指令已存在**：你们在 8/28 的 `8690fee`（PR#1）中已给 `ApiExportCommand` 新增
  `addgrowthpoints` / `addstarlightpoints` 两个 case。所以 A7 这一步**没有**再要求你们新增指令。
- **星光点不经过 MGactivity**：KBBSToper A3 起星光点直连 EssentialsX 经济（`money give` 回退），
  不再调用你们的 `addStarlightPoints`。该方法可保留，但 KBBSToper 不会再用。
- **成长值"倍率"的 Java API 仍在正常使用**：KBBSToper **只废弃了"增加成长值(数值)"的 API**，
  `setGrowthMultiplier` 倍率 API 依然是顶贴奖励的主通道（KBBSToper 优先走 API）。
  **A16 起"经验倍率"奖励已整体移除**：`setExperienceMultiplier` / `getExperienceMultiplier` 已从
  KBBSToper 发布的接口中删除，KBBSToper 不再调用（也不下发 `setexperiencemultiplier` 命令）。
  你们的实现里对应方法可保留（多余方法不影响运行），后续可自行清理。
