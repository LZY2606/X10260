package fpgata.model;

/**
 * 源数据层显式改名记录：网表对象在新版本中的名字变化，
 * 供“对象改名”情形下保持对象身份稳定并追踪受影响结果。
 */
public final class RenameRecord {
    public final String id;
    public final String oldName;
    public final String newName;
    public final String objectType; // PIN / CELL / NET
    public final String reason;
    public final String rawBlock;

    public RenameRecord(String id, String oldName, String newName, String objectType,
                        String reason, String rawBlock) {
        this.id = id;
        this.oldName = oldName;
        this.newName = newName;
        this.objectType = objectType;
        this.reason = reason;
        this.rawBlock = rawBlock;
    }
}
