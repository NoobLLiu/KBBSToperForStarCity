# 给 MGactivity 开发者的修复提示词（KBBSToper 侧提交）

> 用途：把下面整段内容直接发给 MGactivity 的开发者 / 开发 AI，请其按此修复。
> 提出方：KBBSToperForStarCity（顶贴奖励插件）

---

## 问题现象（联调实测）

KBBSToper 顶贴奖励流程正常，控制台与玩家端都显示"奖励发放成功"，但玩家实际**没有收到任何效果**：
- 生命值上限（maxHp）不增加，游戏内血量上限无变化；
- 星光点不发放；
- （另有已知缺口：成长值 +100 未发放，双方此前已确认）。

已对两侧源码逐行核对，问题集中在 **MGactivity 侧**，请按下面 4 项修改。

---

## 修改项 1【严重】setMaxHp 只写 JSON，从不应用到玩家实际生命值

- 文件：`src/cn/gmzc/mgactivitys/data/ActivityManager.java`
- 方法：`setMaxHp(String playerName, int value)`
- 现状：仅 `data.setMaxHp(clamped); save();` 落盘，全插件（ActivityManager / ApiExportCommand / MGactivityApiImpl / MGActivitysPlugin / ActivityListener）**没有任何** `Player#setMaxHealth()` / `Attribute.MAX_HEALTH` / 生命值缩放逻辑。
- 后果：maxHp 是"存了但永远不生效"的死数据，玩家游戏内生命上限永不变。
- 要求：
  1. `setMaxHp` 写入后立即应用到在线玩家：
     ```java
     Player p = Bukkit.getPlayerExact(name);
     if (p != null) {
         p.setMaxHealth(clamped);
         if (p.getHealth() > clamped) {
             p.setHealth(clamped);
         }
     }
     ```
  2. 在 `listener/ActivityListener.java` 的 `onJoin` / `onRespawn` 中读取 `getMaxHp(player.getName())` 重新应用（覆盖重启后进服、以及离线期间被设置 HP 的玩家），例如：
     ```java
     @EventHandler
     public void onJoin(PlayerJoinEvent e) {
         Player p = e.getPlayer();
         int maxHp = activityManager.getMaxHp(p.getName());
         if (maxHp >= 30) {
             p.setMaxHealth(maxHp);
             if (p.getHealth() > maxHp) p.setHealth(maxHp);
         }
         // ...原有 addActivity 逻辑保留
     }
     ```

## 修改项 2【严重】成长值/经验值倍率"存而不用"

- 文件：`data/ActivityManager.java`、`listener/ActivityListener.java`
- 现状：`setGrowthMultiplier` / `setExperienceMultiplier` 只把倍率存进 `ActivityData`；`ActivityListener.onExpGain` 等发放路径只是 `addActivity(...)` 累加成长值，**没有按倍率放大实际发放值**，也没有任何消费方读取倍率。
- 后果：KBBSToper 首顶/额外奖励设置的"当日成长值倍率 / 经验值倍率"形同虚设。
- 要求：明确倍率的作用对象并落地实现。推荐语义（与对接文档一致）：**当日该玩家获得的成长值/经验值 × 倍率**。即在所有 `addActivity` 加值处或单独的经验结算处乘以对应倍率；如果倍率另有消费方（商店折扣、面板展示等），请确认该消费方真实存在且生效，否则请实现为"发放放大"。

## 修改项 3【中等】get 系列不走 resolvePlayerName，大小写不一致读错值

- 文件：`data/ActivityManager.java`
- 方法：`getMaxHp` / `getGrowthMultiplier` / `getExperienceMultiplier`
- 现状：`set*` 先 `resolvePlayerName(playerName)` 再写，`get*` 却直接用原始字符串读。玩家名大小写不一致时（如写入 "Steve"、读取 "steve"），`getPlayerData` 会按不同 key 自动新建默认条目，返回默认值。
- 要求：`get*` 系列同样先 `resolvePlayerName`，与 `set*` 保持一致。

## 修改项 4【中等】文档宣称的 addgrowthpoints 命令未实现

- 文件：`command/ApiExportCommand.java` 的 `onCommand` switch
- 现状：`MGactivity-API-Spec.md` 声称支持 `mgactivity addgrowthpoints %PLAYER% <数值>`（直接增加成长值），但命令处理 switch 中**没有该 case**，实际执行会落入 `printUsage`。
- 后果：KBBSToper 的 `reward.growth-grant-commands` 无法填命令补发"成长值 +100"，该奖励项一直空缺。
- 要求：在 `ApiExportCommand` 增加 `addgrowthpoints` 分支，直接累加 `totalActivity` / `dynamicActivity`（`ActivityManager` 已有 `setGrowthValue` 可复用/参照）。

## 修改项 5【确认项】星光点

- 星光点由 KBBSToper 通过 **Vault 经济**发放（`depositPlayer(name, 300)`），不经 MGactivity 命令。
- 请确认：服务器是否安装了 Vault 及其经济提供者；星光点是否为该经济插件的默认货币。
- 若星光点属于 MGactivity 体系内的货币（`MGactivity-API-Spec.md` 中 `addstarlightpoints` 标注"暂未实现"），请实现它，KBBSToper 可改为调用该命令；否则请与服主确认 Vault 侧配置。

---

## 验收标准（改完请自测）

1. 控制台执行 `mgactivity setmaxhp <玩家名> 50` → 该玩家在线时游戏内生命上限立即变为 50；重进服务器后仍为 50。
2. `mgactivity setgrowthmultiplier <玩家名> 2.0` 后，该玩家当日获取成长值时实际到账 ×2。
3. `mgactivity addgrowthpoints <玩家名> 100` → 玩家成长值 +100（排名/面板可见）。
4. `mgactivity setmaxhp Steve 50` 后用 `mgactivity getmaxhp steve` 也能读到 50（大小写不敏感）。

修改完成后请重新构建 jar 并告知版本号（当前 plugin.yml 版本为 1.0.2）。
