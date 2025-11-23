package com.mozi.auditlog.interceptor.handler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库字段命名转换工具类
 * 将数据库字段名转换为Java驼峰命名格式
 * @author chenhf
 * @date 2025-10-23 10:20
 */
public class DatabaseFieldConverter {
    private DatabaseFieldConverter() {
        // 工具类，防止实例化
    }

    /**
     * 将数据库字段名转换为Java驼峰命名
     * @param dbField 数据库字段名（如：TC_DST_ID）
     * @return 驼峰命名的字段名（如：dstId）
     */
    public static String toCamelCase(String dbField) {
        if (dbField == null || dbField.trim().isEmpty()) {
            return dbField;
        }

        String field = dbField.trim();

        // 如果字段已经是驼峰命名，直接返回
        if (!field.contains("_") && Character.isLowerCase(field.charAt(0))) {
            return field;
        }

        // 分割下划线
        String[] parts = field.split("_");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].toLowerCase();
            if (part.isEmpty()) {
                continue;
            }

            if (i == 0) {
                // 第一个单词全小写
                result.append(part);
            } else {
                // 后续单词首字母大写
                result.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1));
            }
        }

        return result.toString();
    }

    /**
     * 专门处理 TC_DST_ID 转换为 dstId 的方法
     * @param dbField 数据库字段名
     * @return 转换后的字段名
     */
    public static String convertToDstId(String dbField) {
        String camelCase = toCamelCase(dbField);

        // 如果转换后是 tcDstId，去掉 tc 前缀
        if (camelCase.startsWith("tc")) {
            // 确保第三个字符是小写（保持驼峰格式）
            if (camelCase.length() > 2) {
                String remaining = camelCase.substring(2);
                if (!remaining.isEmpty()) {
                    return Character.toLowerCase(remaining.charAt(0)) + remaining.substring(1);
                }
            }
        }

        return camelCase;
    }

    /**
     * 批量转换字段名
     * @param dbFields 数据库字段名数组
     * @return 转换后的字段名数组
     */
    public static String[] convertToDstId(String[] dbFields) {
        if (dbFields == null) {
            return new String[0];
        }

        String[] result = new String[dbFields.length];
        for (int i = 0; i < dbFields.length; i++) {
            result[i] = convertToDstId(dbFields[i]);
        }
        return result;
    }

    /**
     * 检查字段名是否需要转换（是否包含下划线）
     * @param fieldName 字段名
     * @return 是否需要转换
     */
    public static boolean needsConversion(String fieldName) {
        return fieldName != null && fieldName.contains("_");
    }

    /**
     * 恢复转换：将驼峰命名转换回数据库字段名
     * @param camelCase 驼峰命名字段名
     * @return 数据库字段名
     */
    public static String toDatabaseField(String camelCase) {
        if (camelCase == null || camelCase.trim().isEmpty()) {
            return camelCase;
        }

        String field = camelCase.trim();
        Pattern pattern = Pattern.compile("([A-Z])");
        Matcher matcher = pattern.matcher(field);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            matcher.appendReplacement(result, "_" + matcher.group(1).toLowerCase());
        }
        matcher.appendTail(result);

        return result.toString().toUpperCase();
    }

    public static void main(String[] args) {
        // 单个字段转换
        String dbField = "TC_DST_ID";
        String javaField = DatabaseFieldConverter.convertToDstId(dbField);
        System.out.println(dbField + " -> " + javaField); // 输出: TC_DST_ID -> dstId

        // 批量转换
        String[] dbFields = {"TC_DST_ID", "USER_NAME", "CREATE_TIME", "IS_ACTIVE"};
        String[] javaFields = DatabaseFieldConverter.convertToDstId(dbFields);

        for (int i = 0; i < dbFields.length; i++) {
            System.out.println(dbFields[i] + " -> " + javaFields[i]);
        }
        // 输出:
        // TC_DST_ID -> dstId
        // USER_NAME -> userName
        // CREATE_TIME -> createTime
        // IS_ACTIVE -> isActive

        // 恢复转换
        String original = DatabaseFieldConverter.toDatabaseField("dstId");
        System.out.println("dstId -> " + original); // 输出: dstId -> DST_ID
    }
}
