package mc233.fun.kbbstoper.core;

/**
 * 单条顶帖记录。
 *
 * <p>用于「我的顶帖记录」菜单/表单，除时间外还展示类型与本轮奖励：
 * <ul>
 *   <li>{@code kind}：0 = 平峰期，1 = 高峰期。</li>
 *   <li>{@code seq}：该条是当天的第几次（1 起）；0 表示旧版本数据未知。</li>
 *   <li>{@code reward}：本轮发放的奖励文案；{@code null}/空 表示未发放奖励（如已达每日上限）。</li>
 * </ul>
 */
public class TopState {

    public final String time;
    public final int kind;
    public final int seq;
    public final String reward;

    public TopState(String time, int kind, int seq, String reward) {
        this.time = time;
        this.kind = kind;
        this.seq = seq;
        this.reward = reward;
    }

    /** 是否为高峰期顶帖。 */
    public boolean isPeak() {
        return kind == 1;
    }

    /** 是否发放了奖励（有奖励文案）。 */
    public boolean hasReward() {
        return reward != null && !reward.isBlank();
    }
}
