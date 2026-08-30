package mc233.fun.kbbstoper.core;

/**
 * GUI 动作标识，双端（Bukkit 箱子 / Nukkit 表单）共用。
 * 值即 gui.yml 中 action 字段的取值，也用于表单按钮分发。
 */
public enum GuiAction {

    /** 关闭界面。 */
    CLOSE,
    /** 打开主菜单。 */
    OPEN_MAIN,
    /** 绑定 / 更换论坛 ID（JAVA 打开铁砧，基岩打开输入表单）。 */
    BINDING,
    /** 领取顶帖奖励。 */
    REWARD,
    /** 我的顶帖记录（分页）。 */
    MY_RECORDS,
    /** 查看宣传帖。 */
    PROMO_POST,
    /** 顶帖排行榜（分页）。 */
    TOP,
    /** 活动规则说明。 */
    RULES,
    /** 我的状态。 */
    MY_STATUS,
    /** 管理菜单（OP）。 */
    MANAGE,
    /** 测试奖励子菜单（OP）。 */
    TEST_REWARD,
    /** 测试奖励 - 普通（OP）。 */
    TEST_NORMAL,
    /** 测试奖励 - 高峰期（OP）。 */
    TEST_PEAK,
    /** 测试奖励 - 满级效果（OP）。 */
    TEST_MAX,
    /** 顶帖列表（全服，OP）。 */
    LIST,
    /** 检查绑定（OP）。 */
    CHECK,
    /** 删除玩家数据（OP）。 */
    DELETE,
    /** 重载插件（OP）。 */
    RELOAD,
    /** 调试（OP）。 */
    DEBUG,
    /** 调试 - 清空（OP）。 */
    DEBUG_CLEAR,
    /** 调试 - 状态（OP）。 */
    DEBUG_STATUS,
    /** 调试 - 模拟（OP）。 */
    DEBUG_SIMULATE,
    /** 帮助。 */
    HELP,
    /** 上一页。 */
    PREV_PAGE,
    /** 下一页。 */
    NEXT_PAGE,
    /** 返回上一级 / 主菜单。 */
    BACK
}
