package com.gsb.workbench.util;

import java.security.SecureRandom;

/**
 * 固定 seed 的确定性 ID 生成器：测试可复现；生产使用系统种子。
 * ID 一经生成永不复用，保证导出/删除再导入后身份稳定。
 */
public final class Ids {
    private static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuv".toCharArray();

    private static SecureRandom random = new SecureRandom(seed());

    private Ids() {
    }

    private static byte[] seed() {
        byte[] seed = new byte[16];
        new SecureRandom().nextBytes(seed);
        return seed;
    }

    public static synchronized void seed(long fixedSeed) {
        random = new SecureRandom(new byte[]{
                (byte) fixedSeed, (byte) (fixedSeed >>> 8),
                (byte) (fixedSeed >>> 16), (byte) (fixedSeed >>> 24),
                (byte) ~fixedSeed, (byte) (~fixedSeed >>> 8),
                (byte) (~fixedSeed >>> 16), (byte) (~fixedSeed >>> 24)});
    }

    public static synchronized String newId(String prefix) {
        byte[] bytes = new byte[10];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(prefix).append('_');
        for (byte b : bytes) {
            sb.append(ALPHABET[b & 0x1f]);
        }
        return sb.toString();
    }
}
