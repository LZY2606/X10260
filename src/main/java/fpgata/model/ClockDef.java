package fpgata.model;

import java.util.List;

/**
 * 时钟定义（基线源数据）。
 * virtual=true 表示虚拟时钟（无源引脚，用于 I/O 约束）。
 * generated=true 时通过 masterClock + masterEdge + sourceEdges/edges 表达生成关系。
 */
public final class ClockDef {
    public final String id;
    public final String name;
    public final String sourcePin;          // null 表示虚拟时钟
    public final boolean virtual;
    public final Quantity period;           // 周期，坐标基准：时间零点由 waveform 第一项给出
    public final List<Double> waveformPs;   // 长度 2 或 4：边沿时刻(ps)，null 表示原报文未给
    public final boolean generated;
    public final String masterClock;        // 生成时钟：主时钟名
    public final Integer masterEdge;        // 主时钟起始边沿（1=上升沿）
    public final List<Double> sourceEdgesPs;// 生成时钟源边沿（对应 master 上的时刻 ps）
    public final List<Double> genEdgesPs;   // 生成时钟边沿（本时钟时间轴 ps）
    public final String rawBlock;           // 原报文段，逐字保留
    public final List<String> diagnostics;

    public ClockDef(String id, String name, String sourcePin, boolean virtual,
                    Quantity period, List<Double> waveformPs,
                    boolean generated, String masterClock, Integer masterEdge,
                    List<Double> sourceEdgesPs, List<Double> genEdgesPs,
                    String rawBlock, List<String> diagnostics) {
        this.id = id;
        this.name = name;
        this.sourcePin = sourcePin;
        this.virtual = virtual;
        this.period = period;
        this.waveformPs = waveformPs;
        this.generated = generated;
        this.masterClock = masterClock;
        this.masterEdge = masterEdge;
        this.sourceEdgesPs = sourceEdgesPs;
        this.genEdgesPs = genEdgesPs;
        this.rawBlock = rawBlock;
        this.diagnostics = diagnostics;
    }
}
