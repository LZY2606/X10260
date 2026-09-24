package tbench.domain;

import tbench.util.Ids;

/**
 * A netlist object. Identity is the stable {@link #key} (netlist pin/net/port),
 * while {@link #name} may change after a rename. Keeping both means a path can
 * be followed across renames instead of silently becoming a new endpoint.
 */
public final class NetRef {
    public final String key;
    public final String name;
    public final String kind;

    public NetRef(String key, String name, String kind) {
        this.key = key;
        this.name = name;
        this.kind = kind;
    }

    public String stableId() {
        return Ids.shortId("net", key);
    }

    @Override
    public String toString() {
        return name + "[" + key + "]";
    }
}
