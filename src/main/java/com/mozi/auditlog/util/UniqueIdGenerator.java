package com.mozi.auditlog.util;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;

/**
 *
 * @author chenhf
 * @date 2025-11-23 10:03
 */
public class UniqueIdGenerator {
    private static final int TOTAL_LENGTH = 24;
    private static final int TIME_PART_LENGTH = 15; // 格式: yyMMddHHmmssSSS
    private static final int RANDOM_PART_LENGTH = TOTAL_LENGTH - TIME_PART_LENGTH - 1; // 减去开头的 '9'
    private static final int MAX_RANDOM = (int) Math.pow(10, RANDOM_PART_LENGTH);

    // 使用SecureRandom增强随机性，并通过ThreadLocal缓存实例
    private static final ThreadLocal<SecureRandom> secureRandom = ThreadLocal.withInitial(SecureRandom::new);
    /**
     * 生成24位唯一ID的方法
     * @return 唯一ID字符串，格式为 '9' + 15位时间戳 + 8位随机数字
     */
    public static String generateUniqueId() {
        // 预分配StringBuilder的容量，提高性能
        StringBuilder id = new StringBuilder(TOTAL_LENGTH);
        id.append('9'); // 开头为 '9'
        // 快速获取时间戳（比 LocalDateTime.now() 更快）
        long timestamp = System.currentTimeMillis();
        id.append(new SimpleDateFormat("yyMMddHHmmssSSS").format(timestamp));
        // 生成随机部分
        int randomPart = secureRandom.get().nextInt(MAX_RANDOM);
        // 使用String.format进行零填充
        id.append(String.format("%0" + RANDOM_PART_LENGTH + "d", randomPart));
        return id.toString();
    }
}
