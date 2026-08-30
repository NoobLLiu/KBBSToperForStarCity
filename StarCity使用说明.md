# KBBSToperForStarCity 使用说明

> StarCity 服务器定制版（基于 KBBSToper 多模块重构版，插件版本 `3.7.4_A1`）
> 顶帖奖励系统：玩家在论坛顶 StarCity 宣传帖，服务器自动发放成长倍率、生命值上限、星光点等奖励。

---

## 一、插件依赖

| 依赖 | 必须 | 说明 |
| --- | --- | --- |
| Paper 1.21（或 Nukkit 基岩版） | ✅ | 服务端核心 |
| **MGactivity** | ✅ | 接收成长倍率 / HP 上限 / 连签中断的**导出接口命令** |
| **Vault** + 任意经济插件（如 EssentialsX） | ✅ | 用于发放「星光点」（虚拟货币） |
| PlaceholderAPI | ⬜ | 可选，提供占位符支持 |

> ⚠️ 若未安装 Vault 或经济插件，星光点将无法发放（插件会在控制台给出警告，不影响其它奖励）。

---

## 二、玩家使用说明

### 1. 绑定论坛账号
顶贴奖励按「论坛账号 ↔ 游戏账号」绑定发放。先用指令绑定你的论坛 ID：

```
/bt binding <论坛ID>
```

绑定后，爬虫检测到你顶了服务器宣传帖，就会自动给你发奖。

### 2. 奖励规则（全部可在 `config.yml` 调整）

| 项目 | 默认规则 | 说明 |
| --- | --- | --- |
| **峰值时段** | 10:00–22:00（服务器本地时间） | 该时段内顶贴计入「高峰」 |
| **每日首顶上限** | 1 次 | 每天第 1 次有效顶贴 = 首顶奖励 |
| **每日额外上限** | 2 次 | 每天第 2–3 次有效顶贴 = 额外奖励 |
| **每日总上限** | 3 次 | 超过后当天只记录、不再发奖 |
| **顶贴间隔** | ≥ 2 小时 | 两次顶贴间隔不足 2h，本次不发奖 |
| **附加奖励触发** | 高峰时段 **或** 全服 12h 无人顶贴（满足其一即给） | 叠加在普通 / 额外奖励之上 |

### 3. 奖励内容

**首顶（每天第 1 次）**
- 成长倍率 ×1.25（由 MGactivity 设置，次日自动清零取最大值）
- 生命值上限 +2（累加，封顶 50；默认基础 30 → 最高 50，即 +1.5 颗心）
- 成长值 +100

**额外（每天第 2–3 次）**
- 成长值 +100

**附加奖励（高峰时段 / 全服 12h 无人顶贴 触发其一）**
- 在以上基础上额外：生命值上限 +2、成长值 +100、**星光点 +300**

**连签中断（未顶贴惩罚）**
- 若某天未进行有效顶贴，次日自动执行 `addstreakbreak 2`（连签中断 -2）。
- 倍率（成长）不叠加，取当日最大值；每日自动重置。

### 4. 给玩家的提示
- 奖励全自动，无需手动领取。
- 倍率类奖励（成长 / 经验）**不累加**，当天多次顶贴只保留最高倍率，第二天清零。
- HP 上限是**累加并持久化**的（数据库记录 `maxhp`），最高到 50；错过顶贴不会扣 HP，只影响连签。

---

## 三、管理员 / OP 指令说明

主指令为 `/bt`，下面列出常用与管理相关指令。

### 1. OP 调试指令（本版本新增）

```
/bt debug clear      清空自己当前的顶帖状态（rewardbefore / rewardtime / 顶贴记录），用于重置测试
/bt debug status     查看自己当前的顶帖状态（已绑定论坛ID、上次顶贴日期、今日已领次数、当前 HP 上限等）
/bt debug simulate   手动模拟「检测到自己顶贴」，走完整发奖逻辑（每日配额 / 2h 间隔仍生效，附加奖励必触发，方便测试）
```

> 权限节点：`bbstoper.debug`（默认 OP 拥有）。`clear` / `simulate` 只影响执行指令的玩家自己，不会误改他人数据。

### 2. 其它管理指令

| 指令 | 权限 | 作用 |
| --- | --- | --- |
| `/bt binding <论坛ID>` | `bbstoper.binding` | 绑定自己的论坛账号（需输入两次确认；**本版仅本人，不支持为他人绑定**） |
| `/bt check bbsid <论坛ID>` | `bbstoper.check` | 查某论坛ID被哪个玩家绑定 |
| `/bt check player <玩家ID>` | `bbstoper.check` | 查某玩家绑定的论坛ID |
| `/bt reward` | `bbstoper.reward` | 主动领取**自己**已顶帖的奖励（走完整发奖逻辑；**本版仅本人，无目标玩家参数**） |
| `/bt testreward <玩家>` | `bbstoper.testreward` | 测试奖励下发（不发实际数值） |
| `/bt top` | `bbstoper.top` | 查看顶贴排行榜 |
| `/bt list` | `bbstoper.list` | 列出已绑定玩家 |
| `/bt delete <玩家ID>` | `bbstoper.delete` | 删除某玩家数据 |
| `/bt reload` | `bbstoper.reload` | 热重载配置文件 |

输入 `/bt` 或 `/bt help` 可查看当前权限下可见的指令列表。

---

## 四、配置文件说明（`config.yml`）

奖励相关全部位于 `reward:` 节点，可按服务器需求调整：

```yaml
reward:
    auto: 30                      # 自动检测间隔（秒）
    period: 10                    # 查询冷却（秒）
    interval: 120                 # 两次顶贴最小间隔（秒）= 2 小时
    times: 3                      # 每日总次数上限（首顶1 + 额外2）
    peak:
        start-hour: 10            # 峰值时段开始（小时，本地时间）
        end-hour: 22              # 峰值时段结束
    daily:
        first-limit: 1            # 每日首顶奖励次数上限
        extra-limit: 2            # 每日额外奖励次数上限
    inactive-hours-for-additional: 12   # 全服多少小时无人顶贴 → 触发附加奖励
    values:
        growth-multiplier: 1.25   # 首顶：成长倍率
        hp-step: 2                # 每次奖励 HP 上限 +2（首顶生效）
        hp-base: 30               # HP 上限基础值
        hp-hard-cap: 50           # HP 上限硬上限
        growth-per-reward: 100    # 每次有效奖励成长值 +100
        additional-hp-step: 2     # 附加奖励额外 HP +2
        additional-growth: 100    # 附加奖励额外成长 +100
        star-points: 300          # 附加奖励星光点 +300（Vault）
        streak-break-daily: 2     # 未顶贴次日连签中断 -2
    mgactivity:                    # MGactivity 导出接口命令模板（%PLAYER% / %VALUE% 占位）
        growth-multiplier-cmd: 'mgactivity setgrowthmultiplier %PLAYER% %VALUE%'
        setmaxhp-cmd: 'mgactivity setmaxhp %PLAYER% %VALUE%'
        streak-break-cmd: 'mgactivity addstreakbreak %PLAYER% %VALUE%'
    growth-grant-commands: []      # 成长值 +100 发放命令（见下方说明）
    additional:
        enable: true               # 是否启用附加奖励
    vault:
        enable-star-points: true   # 是否通过 Vault 发放星光点
```

### 关于「成长值 +100」

MGactivity 的导出接口目前只提供倍率 / HP / 连签类命令，**没有直接的「增加成长值」接口**。
`reward.growth-grant-commands` 用于配置服务器实际的成长值发放命令（支持 `%PLAYER%` 与 `%VALUE%` 占位符，
`%VALUE%` 会被替换为 `growth-per-reward` / `additional-growth` 的值）。

- 若 MGactivity 后续暴露成长值增加接口，在此填入对应命令即可（例如 `mgactivity addgrowth %PLAYER% %VALUE%`）。
- 若该列表为空，成长值 +100 暂不会发放，但倍率 / HP 上限 / 星光点 / 连签中断不受影响。

默认宣传帖 ID 已设为 `173068`（`bbs.url`）。

---

## 五、安装与构建

1. 将构建产物 `KBBSToperForStarCity-bukkit-*.jar` 放入服务端 `plugins/` 目录。
2. 启动服务器生成默认 `config.yml`，按需修改 `reward:` 节点。
3. 确保已安装 MGactivity 与 Vault（+ 经济插件）。

本地构建（需要能联网拉取 Gradle 依赖的环境）：

```bash
./gradlew build
# 产物：
#   bukkit/build/libs/KBBSToperForStarCity-bukkit-<version>.jar   (Paper 端)
#   nukkit/build/libs/KBBSToperForStarCity-nukkit-<version>.jar   (Nukkit 端)
```

---

## 六、常见问题

- **星光点没收到？** 检查 Vault 与经济插件是否安装；控制台会有 `Vault 经济核心未安装` 警告。
- **倍率没生效？** 确认 MGactivity 已加载且导出接口命令格式与 `mgactivity:` 配置一致。
- **想重置某个玩家测试？** OP 用 `/bt debug clear` 清状态，再用 `/bt debug simulate` 模拟顶贴。
- **改了配置不生效？** `/bt reload` 热重载，或重启服务端。
