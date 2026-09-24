package workbench.core;

/** 稳定身份：顺序号生成，导出/导入后保持不变。 */
public final class Ids {
    private Ids() {}

    public static String event(long seq) {
        return String.format("ev-%05d", seq);
    }

    public static String input(String kind, int vno) {
        return kind + "-v" + vno;
    }

    public static String decision(int n) {
        return String.format("dec-%04d", n);
    }
}
