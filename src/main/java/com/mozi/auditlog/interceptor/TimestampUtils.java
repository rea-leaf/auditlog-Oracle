package com.mozi.auditlog.interceptor;

/**
 * @author chenhf
 * @date 2025-04-02 09:25
 */

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Timestamp 转时间字符串工具类（Java 8+ 推荐）
 */
public class TimestampUtils {

    // 默认时间格式（可修改）
    private static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter DEFAULT_FORMATTER =
            DateTimeFormatter.ofPattern(DEFAULT_PATTERN)
                    .withZone(ZoneId.systemDefault()) // 使用系统默认时区
                    .withLocale(Locale.getDefault()); // 本地化

    /**
     * 将 Timestamp 转换为默认格式字符串
     */
    public static String timestampToString(Timestamp timestamp) {
        return timestampToString(timestamp, DEFAULT_FORMATTER);
    }

    /**
     * 将 Timestamp 转换为自定义格式字符串
     * @param pattern 时间格式（如 "yyyy-MM-dd HH:mm:ss.SSS"）
     */
    public static String timestampToString(Timestamp timestamp, String pattern) {
        return timestampToString(timestamp, DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 使用预定义的 DateTimeFormatter 转换
     */
    public static String timestampToString(Timestamp timestamp, DateTimeFormatter formatter) {
        if (timestamp == null) return null;
        LocalDateTime dateTime = timestamp.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        return dateTime.format(formatter);
    }

    public static void main(String[] args) {
        Timestamp ts = new Timestamp(System.currentTimeMillis());

        // 使用默认格式
        System.out.println(TimestampUtils.timestampToString(ts));
        // 输出: 2023-08-21 15:30:45

        // 使用自定义格式
        System.out.println(TimestampUtils.timestampToString(ts, "yyyy/MM/dd HH:mm"));
        // 输出: 2023/08/21 15:30

        // 带毫秒的格式
        System.out.println(TimestampUtils.timestampToString(ts, "yyyy-MM-dd HH:mm:ss.SSS"));
        // 输出: 2023-08-21 15:30:45.123
    }
}