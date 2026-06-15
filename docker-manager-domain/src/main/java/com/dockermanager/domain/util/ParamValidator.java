package com.dockermanager.domain.util;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 通用参数校验工具类。
 *
 * <p>提供常用的入参校验方法，校验失败时抛出 {@link IllegalArgumentException}。
 * 纯 Java 实现，无框架依赖，可在 Domain 层和 Application 层使用。</p>
 *
 * <h3>使用示例：</h3>
 * <pre>
 * ParamValidator.requireNotBlank(username, "用户名不能为空");
 * ParamValidator.requireLength(username, 3, 50, "用户名长度需在3-50之间");
 * ParamValidator.requireMinLength(password, 6, "密码至少6位");
 * ParamValidator.requireInRange(limit, 1, 500, "limit需在1-500之间");
 * </pre>
 */
public final class ParamValidator {

    private ParamValidator() {} // 工具类禁止实例化

    /** Docker 容器 ID 格式正则：12-64 位十六进制字符 */
    private static final Pattern CONTAINER_ID_PATTERN = Pattern.compile("^[a-fA-F0-9]{12,64}$");

    /** 合法镜像名正则：允许 registry/namespace/name:tag 格式 */
    private static final Pattern IMAGE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._\\-/]*[a-zA-Z0-9]?$");

    /** 安全文件路径正则：禁止 .. 和特殊字符 */
    private static final Pattern SAFE_PATH_PATTERN = Pattern.compile("^(?!.*\\.\\.)[a-zA-Z0-9/_.\\-]+$");

    // ==================== 字符串校验 ====================

    /**
     * 校验字符串不为 null 且不为空白。
     *
     * @param value   待校验值
     * @param message 校验失败时的错误消息
     * @throws IllegalArgumentException 值为 null 或空白时抛出
     */
    public static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 校验字符串最小长度。
     *
     * @param value   待校验值
     * @param min     最小长度（含）
     * @param message 校验失败时的错误消息
     */
    public static void requireMinLength(String value, int min, String message) {
        if (value == null || value.length() < min) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 校验字符串长度范围。
     *
     * @param value   待校验值
     * @param min     最小长度（含）
     * @param max     最大长度（含）
     * @param message 校验失败时的错误消息
     */
    public static void requireLength(String value, int min, int max, String message) {
        if (value == null || value.length() < min || value.length() > max) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 校验字符串最大长度（若值非空）。
     *
     * @param value   待校验值（允许 null）
     * @param max     最大长度（含）
     * @param message 校验失败时的错误消息
     */
    public static void requireMaxLength(String value, int max, String message) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(message);
        }
    }

    // ==================== 数值校验 ====================

    /**
     * 校验整数在指定范围内 [min, max]。
     *
     * @param value   待校验值
     * @param min     最小值（含）
     * @param max     最大值（含）
     * @param message 校验失败时的错误消息
     */
    public static void requireInRange(int value, int min, int max, String message) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 校验整数不小于最小值。
     *
     * @param value   待校验值
     * @param min     最小值（含）
     * @param message 校验失败时的错误消息
     */
    public static void requireMin(int value, int min, String message) {
        if (value < min) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 校验浮点数在指定范围内 [min, max]。
     *
     * @param value   待校验值
     * @param min     最小值（含）
     * @param max     最大值（含）
     * @param message 校验失败时的错误消息
     */
    public static void requireInRange(double value, double min, double max, String message) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(message);
        }
    }

    // ==================== 对象校验 ====================

    /**
     * 校验对象不为 null。
     *
     * @param value   待校验值
     * @param message 校验失败时的错误消息
     */
    public static void requireNotNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    // ==================== 枚举校验 ====================

    /**
     * 校验字符串值在允许的枚举值集合中。
     *
     * @param value    待校验值
     * @param allowed  允许的值集合
     * @param message  校验失败时的错误消息
     */
    public static void requireOneOf(String value, Set<String> allowed, String message) {
        if (value == null || !allowed.contains(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 校验字符串值可解析为指定枚举类型。
     *
     * @param value       待校验值
     * @param enumClass   枚举类型
     * @param message     校验失败时的错误消息
     */
    public static <E extends Enum<E>> void requireEnumValue(String value, Class<E> enumClass, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        try {
            Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(message);
        }
    }

    // ==================== 格式校验 ====================

    /**
     * 校验 Docker 容器 ID 格式（12-64 位十六进制字符）。
     *
     * @param containerId 容器 ID
     * @param message     校验失败时的错误消息
     */
    public static void requireContainerId(String containerId, String message) {
        requireNotBlank(containerId, message);
        if (!CONTAINER_ID_PATTERN.matcher(containerId).matches()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 校验 Docker 镜像名格式。
     *
     * @param imageName 镜像名
     * @param message   校验失败时的错误消息
     */
    public static void requireImageName(String imageName, String message) {
        requireNotBlank(imageName, message);
        if (imageName.length() > 256 || !IMAGE_NAME_PATTERN.matcher(imageName).matches()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 校验文件路径安全性（禁止路径遍历攻击）。
     *
     * <p>检查规则：</p>
     * <ul>
     *   <li>路径不为空</li>
     *   <li>不包含 {@code ..} 路径遍历序列</li>
     *   <li>不包含空字节（\0）注入</li>
     * </ul>
     *
     * @param path    文件路径
     * @param message 校验失败时的错误消息
     */
    public static void requireSafePath(String path, String message) {
        requireNotBlank(path, message);
        // 禁止路径遍历和空字节注入
        if (path.contains("..") || path.contains("\0")) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 安全地从 Map 中提取 String 值，不存在或为 null 时返回默认值。
     *
     * @param body         请求体 Map
     * @param key          键名
     * @param defaultValue 默认值
     * @return 字符串值或默认值
     */
    public static String getStringOrDefault(java.util.Map<String, ?> body, String key, String defaultValue) {
        if (body == null) return defaultValue;
        Object value = body.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 安全地从 Map 中提取 int 值，不存在或类型错误时返回默认值。
     *
     * @param body         请求体 Map
     * @param key          键名
     * @param defaultValue 默认值
     * @return 整数值或默认值
     */
    public static int getIntOrDefault(java.util.Map<String, ?> body, String key, int defaultValue) {
        if (body == null) return defaultValue;
        Object value = body.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        return defaultValue;
    }

    /**
     * 安全地从 Map 中提取 double 值，不存在或类型错误时返回默认值。
     *
     * @param body         请求体 Map
     * @param key          键名
     * @param defaultValue 默认值
     * @return 浮点数值或默认值
     */
    public static double getDoubleOrDefault(java.util.Map<String, ?> body, String key, double defaultValue) {
        if (body == null) return defaultValue;
        Object value = body.get(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        return defaultValue;
    }

    /**
     * 安全地从 Map 中提取 Long 值，不存在或类型错误时返回 null。
     *
     * @param body 请求体 Map
     * @param key  键名
     * @return Long 值或 null
     */
    public static Long getLongOrNull(java.util.Map<String, ?> body, String key) {
        if (body == null) return null;
        Object value = body.get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        return null;
    }

    /**
     * 规范化 limit 参数，确保在 [1, max] 范围内。
     *
     * @param limit 用户传入的 limit 值
     * @param max   允许的最大值
     * @return 规范化后的 limit 值
     */
    public static int normalizeLimit(int limit, int max) {
        if (limit < 1) return 1;
        return Math.min(limit, max);
    }
}
