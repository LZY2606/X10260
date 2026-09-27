package fpgata.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 稳定身份工具：显式身份（样例包、导入归档）原样保留；
 * 未显式给出的身份按 (种子, 类型, 序号, 负载) 确定性派生，
 * 保证“固定 seed、重放一致、导出再导入一致”。
 */
public final class Ids {
    private Ids() {
    }

    public static String hash8(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String p : parts) {
                md.update((p == null ? "" : p).getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            String alphabet = "0123456789abcdefghijklmnopqrstuvwxyz";
            for (int i = 0; i < 10; i++) {
                int v = d[i] & 0xff;
                sb.append(alphabet[v % 36]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String derive(String seed, String type, int index, String payload) {
        return type + "_" + hash8(seed, type, Integer.toString(index), payload == null ? "" : payload);
    }
}
