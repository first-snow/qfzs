package work.cxlm.model.enums;

/**
 * 时段状态
 *
 * @author cxlm
 */
public enum TimeState implements ValueEnum<Integer> {

    /**
     * 时间段空闲
     */
    IDLE(0),

    /**
     * 时间段已经被预定
     */
    OCCUPIED(1),

    /**
     * 时段被关注中
     */
    FOLLOWED(2),

    /**
     * 被自己占用，数据库中不应该出现此状态，在传递给前端时需要
     */
    MINE(4),

    /**
     * 过去的时间段
     */
    PASSED(5),

    /**
     * 未开放的时间段，比如未来时段
     */
    NOT_OPEN(6),


    /**
     * 时间段已经被管理员禁用
     */
    DISABLED_RED(10),
    DISABLED_WARM(11),
    DISABLED_COOL(12),
    ;

    private final Integer value;

    TimeState(Integer value) {
        this.value = value;
    }

    /**
     * 判断当前时间段是否被预约
     */
    public boolean isOccupied() {
        return this == OCCUPIED;
    }

    /**
     * 判断当前活动室是否空闲
     */
    public boolean isIdle() {
        return this == IDLE;
    }

    /**
     * 判断当前活动室是否被禁止使用
     */
    public boolean disabled() {
        return this == DISABLED_RED;
    }

    /**
     * 判断当前状态是否为禁用
     */
    public boolean isDisabledState() {
        return this == DISABLED_COOL || this == DISABLED_RED || this == DISABLED_WARM;
    }


    @Override
    public Integer getValue() {
        return value;
    }

}
