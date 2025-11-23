package com.mozi.auditlog.interceptor;

/**
 * @author chenhf
 * @date 2025-04-01 15:28
 */
public class CamelCaseUtils {
    /**
     * 将字符串转换为驼峰命名，并去除 "TC_" 前缀
     * @param input 输入字符串
     * @return 驼峰命名字符串，输入为 null 时返回 null
     */
    public static String toCamelCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // 去除 "TC_" 前缀
        if (input.startsWith("TC_")) {
            input = input.substring(3);
        }

        String[] parts = input.split("_");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue; // 跳过空部分（如连续下划线）
            }

            if (i == 0) {
                // 首单词全小写
                result.append(part.toLowerCase());
            } else {
                // 后续单词首字母大写，其余小写
                result.append(capitalize(part));
            }
        }

        return result.toString();
    }

    // 辅助方法：首字母大写，其余小写
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    // 测试用例
    public static void main(String[] args) {
        System.out.println(toCamelCase("TC_UL_ID"));     // 输出 "ulId"
        System.out.println(toCamelCase("TC_ABC_DEF"));   // 输出 "abcDef"
        System.out.println(toCamelCase("TC_"));          // 输出 ""
        System.out.println(toCamelCase("HELLO_WORLD"));  // 输出 "helloWorld"
        System.out.println(toCamelCase("My_NAME_Is"));   // 输出 "myNameIs"
    }
}
