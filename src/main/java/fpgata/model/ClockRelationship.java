package fpgata.model;

/**
 * 时钟间关系定义。没有关系定义的跨时钟域路径必须判为 UNCONSTRAINED，
 * 不假定同源即同步。
 *   ASYNC：set_clock_groups -asynchronous（跨域不分析时序）
 *   LOGIC：set_max_delay 等显式约束（仍按给定 delay 分析）
 *   PHASE：create_generated_clock 等建立的已知相位关系（引擎可算）
 */
public final class ClockRelationship {
    public enum RelType { ASYNC, LOGIC, PHASE }

    public final String id;
    public final RelType type;
    public final String clockA;
    public final String clockB;
    public final Quantity maxDelay;  // LOGIC 时有效，否则 null
    public final String rawBlock;

    public ClockRelationship(String id, RelType type, String clockA, String clockB,
                             Quantity maxDelay, String rawBlock) {
        this.id = id;
        this.type = type;
        this.clockA = clockA;
        this.clockB = clockB;
        this.maxDelay = maxDelay;
        this.rawBlock = rawBlock;
    }
}
