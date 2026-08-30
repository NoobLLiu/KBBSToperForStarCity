package mc233.fun.kbbstoper.nukkit.form;

/** 表单按钮对应的动作（v2 最终稿，与 Java 端 GuiAction 对应）。 */
public enum FormAction {

    /** 绑定 / 换绑论坛 ID。 */
    BINDING,
    /** 领取顶帖奖励。 */
    REWARD,
    /** 我的顶帖记录（分页）。 */
    MY_RECORDS,
    /** 我的状态。 */
    MY_STATUS,
    /** 顶帖排行榜（分页）。 */
    TOP,
    /** 显示宣传帖链接。 */
    POST,
    /** 活动规则。 */
    RULES,
    /** 帮助（指令速查）。 */
    HELP,
    /** 管理菜单（OP）。 */
    MANAGE,
    /** 打开测试奖励子菜单（OP）。 */
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
    /** 上一页。 */
    PREV_PAGE,
    /** 下一页。 */
    NEXT_PAGE,
    /** 返回上级菜单。 */
    BACK
}
