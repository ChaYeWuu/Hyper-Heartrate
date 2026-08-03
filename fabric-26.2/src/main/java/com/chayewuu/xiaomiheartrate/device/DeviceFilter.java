package com.chayewuu.xiaomiheartrate.device;

import java.util.Locale;

/**
 * 设备过滤规则工具类。
 * <p>
 * 根据设备名称前缀/包含匹配，识别小米 / Redmi 系列手环与手表，
 * 并推断对应的 {@link DeviceType}。用于在扫描阶段过滤出受支持的设备，
 * 仅将匹配的设备通过 {@link ScanCallback} 上报给上层。
 * </p>
 *
 * <p>该类为工具类，禁止实例化。</p>
 *
 * <p>匹配规则（大小写不敏感）：</p>
 * <ul>
 *     <li>{@code "Mi Band"} / {@code "Mi Smart Band"} / {@code "Xiaomi Band"}
 *         / {@code "Xiaomi Smart Band"} → {@link DeviceType#XIAOMI_BAND}；</li>
 *     <li>{@code "Mi Watch"} / {@code "Xiaomi Watch"} → {@link DeviceType#XIAOMI_WATCH}；</li>
 *     <li>{@code "Redmi Watch"} → {@link DeviceType#REDMI_WATCH}；</li>
 *     <li>{@code "Redmi Band"} → 归类为 {@link DeviceType#REDMI_WATCH}（与手表共用解析路径）；</li>
 *     <li>其余返回 {@link DeviceType#UNKNOWN}。</li>
 * </ul>
 */
public final class DeviceFilter {
    /** 小米手环名称前缀（小写） */
    private static final String[] XIAOMI_BAND_PREFIXES = {
            "mi band",
            "mi smart band",
            "xiaomi band",
            "xiaomi smart band",
            "miband"
    };

    /** 小米手表名称前缀（小写） */
    private static final String[] XIAOMI_WATCH_PREFIXES = {
            "mi watch",
            "xiaomi watch",
            "miwatch"
    };

    /** iPhone 心率广播 App 名称前缀（小写） */
    private static final String[] IPHONE_HEART_RATE_PREFIXES = {
            "xinlvguangbo",
            "xinlvguangbo-iphone"
    };

    /** Redmi 设备名称前缀（小写） */
    private static final String[] REDMI_PREFIXES = {
            "redmi watch",
            "redmi band",
            "redmi"
    };

    /**
     * 通用小米设备名称前缀（小写）。
     * <p>部分设备（如 Redmi Watch 6）在 BLE 广播时使用短名称 "Xiaomi 27DB"，
     * 不含 "Band"/"Watch" 关键字，需通过通用前缀匹配识别。</p>
     */
    private static final String[] XIAOMI_GENERIC_PREFIXES = {
            "xiaomi"
    };

    /** 私有构造器，禁止实例化 */
    private DeviceFilter() {
    }

    /**
     * 判断设备是否为受支持的小米/Redmi 设备。
     *
     * @param device 待判定设备
     * @return {@code true} 表示该设备受支持
     */
    public static boolean isSupportedDevice(BleDevice device) {
        if (device == null) {
            return false;
        }
        return isSupportedDevice(device.getName());
    }

    /**
     * 判断设备名称是否匹配受支持的设备。
     *
     * @param name 设备名称
     * @return {@code true} 表示名称匹配小米/Redmi 设备
     */
    public static boolean isSupportedDevice(String name) {
        return getDeviceType(name) != DeviceType.UNKNOWN;
    }

    /**
     * 根据设备名称推断设备类型。
     * <p>匹配方式为大小写不敏感的前缀匹配。</p>
     *
     * @param name 设备名称
     * @return 设备类型；无法识别时返回 {@link DeviceType#UNKNOWN}
     */
    public static DeviceType getDeviceType(String name) {
        if (name == null || name.isBlank()) {
            return DeviceType.UNKNOWN;
        }
        String lower = name.toLowerCase(Locale.ROOT);

        // iPhone 心率广播 App 匹配（在小米匹配之前检查）
        for (String prefix : IPHONE_HEART_RATE_PREFIXES) {
            if (lower.startsWith(prefix) || lower.contains(prefix)) {
                return DeviceType.IPHONE_HEART_RATE_APP;
            }
        }

        // 优先匹配更具体的前缀（"mi smart band" 优先于 "mi band"）
        for (String prefix : XIAOMI_BAND_PREFIXES) {
            if (lower.startsWith(prefix) || lower.contains(prefix)) {
                return DeviceType.XIAOMI_BAND;
            }
        }
        for (String prefix : XIAOMI_WATCH_PREFIXES) {
            if (lower.startsWith(prefix) || lower.contains(prefix)) {
                return DeviceType.XIAOMI_WATCH;
            }
        }
        for (String prefix : REDMI_PREFIXES) {
            if (lower.startsWith(prefix) || lower.contains(prefix)) {
                // Redmi 手环/手表统一归为 REDMI_WATCH
                return DeviceType.REDMI_WATCH;
            }
        }
        // 通用小米前缀兜底（如 "Xiaomi 27DB" 这类短广播名称）
        for (String prefix : XIAOMI_GENERIC_PREFIXES) {
            if (lower.startsWith(prefix) || lower.contains(prefix)) {
                // 无法区分手环/手表，统一归为 XIAOMI_WATCH（解析路径相同）
                return DeviceType.XIAOMI_WATCH;
            }
        }
        return DeviceType.UNKNOWN;
    }
}
