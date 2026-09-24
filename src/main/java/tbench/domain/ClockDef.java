package tbench.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A clock definition: either a primary clock driven by a port/pin, or a
 * generated clock derived from a master clock through a clock-dividing cell.
 * Phase and edge factors are kept as raw quantities with units.
 */
public final class ClockDef {
    public final String name;
    public final boolean generated;
    public final String sourceRef;
    public final String masterClock;
    public final Qty period;
    public final Qty waveform0;
    public final Qty waveform1;
    public final double divideBy;
    public final double multiplyBy;
    public final Qty phaseShift;
    public final String sourceCell;
    public final String revisionId;

    private ClockDef(Builder b) {
        this.name = b.name;
        this.generated = b.generated;
        this.sourceRef = b.sourceRef;
        this.masterClock = b.masterClock;
        this.period = b.period;
        this.waveform0 = b.waveform0;
        this.waveform1 = b.waveform1;
        this.divideBy = b.divideBy;
        this.multiplyBy = b.multiplyBy;
        this.phaseShift = b.phaseShift;
        this.sourceCell = b.sourceCell;
        this.revisionId = b.revisionId;
    }

    /** Edges in ns over two periods, each with edge index and rise/fall. */
    public List<Edge> edges() {
        List<Edge> out = new ArrayList<>();
        if (!period.convertible()) {
            return out;
        }
        double p = period.value;
        double w0 = waveform0 != null && waveform0.convertible() ? waveform0.value : 0.0;
        double w1 = waveform1 != null && waveform1.convertible() ? waveform1.value : p / 2.0;
        for (int i = -1; i <= 2; i++) {
            out.add(new Edge(w0 + i * p, true));
            out.add(new Edge(w1 + i * p, false));
        }
        out.sort((a, b) -> Double.compare(a.timeNs, b.timeNs));
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClockDef)) {
            return false;
        }
        ClockDef c = (ClockDef) o;
        return generated == c.generated
                && Double.compare(c.divideBy, divideBy) == 0
                && Double.compare(c.multiplyBy, multiplyBy) == 0
                && Objects.equals(name, c.name)
                && Objects.equals(sourceRef, c.sourceRef)
                && Objects.equals(masterClock, c.masterClock)
                && Objects.equals(period, c.period)
                && Objects.equals(waveform0, c.waveform0)
                && Objects.equals(waveform1, c.waveform1)
                && Objects.equals(phaseShift, c.phaseShift)
                && Objects.equals(sourceCell, c.sourceCell);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, generated, sourceRef, masterClock, period, waveform0,
                waveform1, divideBy, multiplyBy, phaseShift, sourceCell);
    }

    public static final class Edge {
        public final double timeNs;
        public final boolean rising;

        public Edge(double timeNs, boolean rising) {
            this.timeNs = timeNs;
            this.rising = rising;
        }
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private boolean generated;
        private String sourceRef = "";
        private String masterClock;
        private Qty period = Qty.time("");
        private Qty waveform0;
        private Qty waveform1;
        private double divideBy = 1;
        private double multiplyBy = 1;
        private Qty phaseShift = Qty.time("0ns");
        private String sourceCell = "";
        private String revisionId = "";

        private Builder(String name) {
            this.name = name;
        }

        public Builder generated(boolean v) {
            this.generated = v;
            return this;
        }

        public Builder sourceRef(String v) {
            this.sourceRef = v;
            return this;
        }

        public Builder masterClock(String v) {
            this.masterClock = v;
            return this;
        }

        public Builder period(Qty v) {
            this.period = v;
            return this;
        }

        public Builder waveform(Qty a, Qty b) {
            this.waveform0 = a;
            this.waveform1 = b;
            return this;
        }

        public Builder divideBy(double v) {
            this.divideBy = v;
            return this;
        }

        public Builder multiplyBy(double v) {
            this.multiplyBy = v;
            return this;
        }

        public Builder phaseShift(Qty v) {
            this.phaseShift = v;
            return this;
        }

        public Builder sourceCell(String v) {
            this.sourceCell = v;
            return this;
        }

        public Builder revisionId(String v) {
            this.revisionId = v;
            return this;
        }

        public ClockDef build() {
            return new ClockDef(this);
        }
    }
}
