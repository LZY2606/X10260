package fpgata.model;

/**
 * 时序例外（false path / multicycle），基线源数据。
 * 携带对象集合（through/get 列表支持层级前缀匹配）与适用检查类型。
 * holdMulticycle / setupMulticycle 配对由引擎检查（hold 不应孤立）。
 */
public final class ExceptionConstraint {
    public enum Kind { FALSE_PATH, MULTICYCLE }
    public enum CheckType { SETUP, HOLD, BOTH }
    public enum Direction { FROM, THROUGH, TO, GET_PINS, GET_CLOCKS, GET_CELLS }

    public final String id;
    public final String text;           // SDC 文本，逐字保留
    public final Kind kind;
    public final CheckType checkType;
    public final java.util.List<String> from;
    public final java.util.List<String> through;
    public final java.util.List<String> to;
    public final int multiplier;        // multicycle 倍数；false path 为 0
    public final int ordinal;           // 文本中的顺序（仅作并列时的参考，不作“最后一条取胜”）
    public final String rawBlock;

    public ExceptionConstraint(String id, String text, Kind kind, CheckType checkType,
                               java.util.List<String> from, java.util.List<String> through,
                               java.util.List<String> to, int multiplier, int ordinal, String rawBlock) {
        this.id = id;
        this.text = text;
        this.kind = kind;
        this.checkType = checkType;
        this.from = from;
        this.through = through;
        this.to = to;
        this.multiplier = multiplier;
        this.ordinal = ordinal;
        this.rawBlock = rawBlock;
    }
}
