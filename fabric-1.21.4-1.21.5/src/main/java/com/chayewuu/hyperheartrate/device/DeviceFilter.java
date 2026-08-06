package com.chayewuu.hyperheartrate.device;

import java.util.Locale;

/**
 * 设备类型识别与过滤工具类。
 * <p>
 * 负责识别小米/Redmi/已知心率品牌设备，并过滤明显非心率设备（如家电、空名称未知设备）。
 * 非小米/Redmi 但属于已知心率品牌的设备归类为 STANDARD_GATT（通用心率广播设备），
 * 通过标准 GATT 0x2A37 心率服务解析。
 * </p>
 *
 * <p>该类为工具类，禁止实例化。</p>
 *
 * <p>识别规则（大小写不敏感）：</p>
 * <ul>
 *     <li>{@code "Mi Band"} / {@code "Mi Smart Band"} / {@code "Xiaomi Band"}
 *         / {@code "Xiaomi Smart Band"} → {@link DeviceType#XIAOMI_BAND}；</li>
 *     <li>{@code "Mi Watch"} / {@code "Xiaomi Watch"} → {@link DeviceType#XIAOMI_WATCH}；</li>
 *     <li>{@code "Redmi Watch"} / {@code "Redmi Band"} → {@link DeviceType#REDMI_WATCH}；</li>
 *     <li>已知心率品牌（华为/苹果/三星/佳明等）→ {@link DeviceType#STANDARD_GATT}；</li>
 *     <li>空名称、黑名单（midea 等家电/物联网）→ 不受支持，扫描时过滤。</li>
 * </ul>
 *
 * <p>品牌分类通过 {@link #getBrandLabel(String)} 返回中文标签（如 "华为设备"、"苹果设备"），
 * 用于设备列表展示。</p>
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

    /**
     * 非心率设备黑名单关键词（小写，包含匹配）。
     * <p>这些关键词对应的设备明显不是心率设备（家电、物联网、音频等），扫描时直接过滤。</p>
     */
    private static final String[] NON_HR_BLACKLIST_KEYWORDS = {
            "midea", "tv", "speaker", "lamp", "bulb", "plug", "socket",
            "router", "gateway", "sensor tag", "empty", "unknown", "n/a",
            "ble keyboard", "mouse", "headphone", "earphone", "earbuds",
            "airpods", "air conditioner", "washing", "fridge", "microwave",
            "oven", "dishwasher", "vacuum", "cleaner", "fan", "heater",
            "thermostat", "doorbell", "camera", "lock", "scale"
    };

    /**
     * 通用可穿戴设备关键词（小写，包含匹配）。
     * <p>含这些关键词的设备大概率是可穿戴/运动设备，放行并归类为通用心率设备。</p>
     */
    private static final String[] WEARABLE_GENERIC_KEYWORDS = {
            "watch", "band", "fit", "heart", "pulse", "sport", "fitness",
            "tracker", "wear", "run", "cycle", "bike"
    };

    /**
     * 已知心率品牌分类表。
     * <p>每个条目包含品牌关键词数组和对应的中文标签。
     * 匹配时按顺序检查，首个命中即返回对应标签。</p>
     */
    private static final BrandCategory[] BRAND_CATEGORIES = {
            new BrandCategory(new String[]{"huawei", "honor"}, "华为设备"),
            new BrandCategory(new String[]{"apple watch", "apple", "iphone", "iwatch"}, "苹果设备"),
            new BrandCategory(new String[]{"samsung", "galaxy watch", "galaxy"}, "三星设备"),
            new BrandCategory(new String[]{"garmin"}, "佳明设备"),
            new BrandCategory(new String[]{"polar"}, "博能设备"),
            new BrandCategory(new String[]{"wahoo"}, "Wahoo设备"),
            new BrandCategory(new String[]{"fitbit"}, "Fitbit设备"),
            new BrandCategory(new String[]{"amazfit", "zepp"}, "Amazfit设备"),
            new BrandCategory(new String[]{"withings"}, "Withings设备"),
            new BrandCategory(new String[]{"coros"}, "高驰设备"),
            new BrandCategory(new String[]{"suunto"}, "松拓设备"),
            new BrandCategory(new String[]{"whoop"}, "Whoop设备"),
            new BrandCategory(new String[]{"decathlon", "geonaute", "kalenji"}, "迪卡侬设备"),
            new BrandCategory(new String[]{"keep"}, "Keep设备"),
            new BrandCategory(new String[]{"ticwatch", "mobvoi"}, "出门问问设备"),
            new BrandCategory(new String[]{"mibo"}, "咪步设备"),
            new BrandCategory(new String[]{"sony"}, "索尼设备"),
            new BrandCategory(new String[]{"lg watch", "lg w"}, "LG设备"),
            new BrandCategory(new String[]{"asus", "zenwatch"}, "华硕设备"),
            new BrandCategory(new String[]{"fossil"}, "Fossil设备"),
            new BrandCategory(new String[]{"skagen"}, "Skagen设备"),
            new BrandCategory(new String[]{"oneplus", "oneplus watch"}, "一加设备"),
            new BrandCategory(new String[]{"realme"}, "真我设备"),
            new BrandCategory(new String[]{"oppo watch", "oppo w"}, "OPPO设备"),
            new BrandCategory(new String[]{"vivo watch", "vivo w"}, "vivo设备"),
            new BrandCategory(new String[]{"casetify"}, "Casetify设备"),
            new BrandCategory(new String[]{"xiaomi", "mi band", "mi smart band", "mi watch", "redmi"}, "小米设备"),
            new BrandCategory(new String[]{"casio"}, "卡西欧设备"),
            new BrandCategory(new String[]{"tomtom"}, "TomTom设备"),
            new BrandCategory(new String[]{"microsoft band", "microsoft"}, "微软设备"),
            new BrandCategory(new String[]{"huami"}, "华米设备"),
            new BrandCategory(new String[]{"codoon", "咕咚"}, "咕咚设备"),
            new BrandCategory(new String[]{"maibo", "迈宝"}, "迈宝设备"),
            new BrandCategory(new String[]{"gamin"}, "佳明设备"),
            new BrandCategory(new String[]{"timex"}, "天美时设备"),
            new BrandCategory(new String[]{"nike"}, "Nike设备"),
            new BrandCategory(new String[]{"adidas", "runtastic"}, "Adidas设备"),
            new BrandCategory(new String[]{"bryton"}, "百锐腾设备"),
            new BrandCategory(new String[]{"igpsport"}, "iGPSPORT设备"),
            new BrandCategory(new String[]{"magene"}, "迈金设备"),
            new BrandCategory(new String[]{"hammerhead"}, "Hammerhead设备"),
            new BrandCategory(new String[]{"lezyne"}, "Lezyne设备"),
            new BrandCategory(new String[]{"sigma"}, "Sigma设备"),
            new BrandCategory(new String[]{"cateye"}, "猫眼设备"),
            new BrandCategory(new String[]{"garmin", "vector"}, "佳明设备"),
            new BrandCategory(new String[]{"wahoo", "tickr"}, "Wahoo设备"),
            new BrandCategory(new String[]{"polar", "h10", "h9", "oh1"}, "博能设备"),
            new BrandCategory(new String[]{"scosche"}, "Scosche设备"),
            new BrandCategory(new String[]{"lifebeam"}, "LifeBeam设备"),
            new BrandCategory(new String[]{"jarmin"}, "佳明设备"),
            new BrandCategory(new String[]{"moov"}, "Moov设备"),
            new BrandCategory(new String[]{"pebble"}, "Pebble设备"),
            new BrandCategory(new String[]{"misfit"}, "Misfit设备"),
            new BrandCategory(new String[]{"jawbone"}, "Jawbone设备"),
            new BrandCategory(new String[]{"moto 360", "motorola"}, "摩托罗拉设备"),
            new BrandCategory(new String[]{"huawei watch", "huawei band"}, "华为设备"),
            new BrandCategory(new String[]{"tiktok"}, "TikTok设备"),
            new BrandCategory(new String[]{"noise"}, "Noise设备"),
            new BrandCategory(new String[]{"fireboltt", "fire-boltt"}, "Fire-Boltt设备"),
            new BrandCategory(new String[]{"boat", "boAt"}, "boAt设备"),
            new BrandCategory(new String[]{"dizo"}, "Dizo设备"),
            new BrandCategory(new String[]{"crossbeats"}, "Crossbeats设备"),
            new BrandCategory(new String[]{"ambrane"}, "Ambrane设备"),
            new BrandCategory(new String[]{"gionee"}, "金立设备"),
            new BrandCategory(new String[]{"lava"}, "Lava设备"),
            new BrandCategory(new String[]{"micromax"}, "Micromax设备"),
            new BrandCategory(new String[]{"tcl"}, "TCL设备"),
            new BrandCategory(new String[]{"zte"}, "中兴设备"),
            new BrandCategory(new String[]{"meizu"}, "魅族设备"),
            new BrandCategory(new String[]{"letv", "leeco"}, "乐视设备"),
            new BrandCategory(new String[]{"360 watch", "360 band", "360wear"}, "360设备"),
            new BrandCategory(new String[]{"amazfit"}, "Amazfit设备"),
            new BrandCategory(new String[]{"haylou"}, "嘿喽设备"),
            new BrandCategory(new String[]{"qcy"}, "QCY设备"),
            new BrandCategory(new String[]{"jabra"}, "捷波朗设备"),
            new BrandCategory(new String[]{"jbl"}, "JBL设备"),
            new BrandCategory(new String[]{"sennheiser"}, "森海塞尔设备"),
            new BrandCategory(new String[]{"shokz", "aftershokz"}, "韶音设备"),
            new BrandCategory(new String[]{"columbia"}, "哥伦比亚设备"),
            new BrandCategory(new String[]{"sunder"}, "Sunder设备"),
            new BrandCategory(new String[]{"freestyle"}, "Freestyle设备"),
            new BrandCategory(new String[]{"casio", "g-shock"}, "卡西欧设备"),
            new BrandCategory(new String[]{"seiko"}, "精工设备"),
            new BrandCategory(new String[]{"citizen"}, "西铁城设备"),
            new BrandCategory(new String[]{"orient"}, "东方双狮设备"),
    };

    /** 私有构造器，禁止实例化 */
    private DeviceFilter() {
    }

    /**
     * 判断设备是否为受支持的设备。
     * <p>过滤空名称设备与黑名单设备；小米/Redmi/已知心率品牌/通用可穿戴设备视为受支持。</p>
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
     * <p>空名称与黑名单设备被过滤；小米/Redmi/已知心率品牌/通用可穿戴设备放行。</p>
     *
     * @param name 设备名称
     * @return {@code true} 表示该设备受支持
     */
    public static boolean isSupportedDevice(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        // 黑名单设备直接过滤
        for (String kw : NON_HR_BLACKLIST_KEYWORDS) {
            if (lower.contains(kw)) {
                return false;
            }
        }
        // 小米/Redmi 设备放行
        if (matchesXiaomiOrRedmi(lower)) {
            return true;
        }
        // 已知心率品牌放行
        for (BrandCategory bc : BRAND_CATEGORIES) {
            for (String kw : bc.keywords) {
                if (lower.contains(kw)) {
                    return true;
                }
            }
        }
        // 通用可穿戴关键词放行（watch/band/fit 等）
        for (String kw : WEARABLE_GENERIC_KEYWORDS) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        // 其余未知设备过滤（避免扫描列表出现乱七八糟的设备）
        return false;
    }

    /**
     * 获取设备的品牌分类标签（中文）。
     * <p>用于设备列表显示，如 "华为设备"、"苹果设备"、"小米手环" 等。
     * 无法匹配具体品牌时返回 "通用心率设备"。</p>
     *
     * @param name 设备名称
     * @return 中文品牌标签
     */
    public static String getBrandLabel(String name) {
        if (name == null || name.isBlank()) {
            return "未知设备";
        }
        String lower = name.toLowerCase(Locale.ROOT);

        // 小米手环
        for (String p : XIAOMI_BAND_PREFIXES) {
            if (lower.startsWith(p) || lower.contains(p)) {
                return "小米手环";
            }
        }
        // 红米手表/手环
        for (String p : REDMI_PREFIXES) {
            if (lower.startsWith(p) || lower.contains(p)) {
                return "红米手表";
            }
        }
        // 小米手表
        for (String p : XIAOMI_WATCH_PREFIXES) {
            if (lower.startsWith(p) || lower.contains(p)) {
                return "小米手表";
            }
        }
        // 通用小米前缀
        for (String p : XIAOMI_GENERIC_PREFIXES) {
            if (lower.startsWith(p) || lower.contains(p)) {
                return "小米设备";
            }
        }
        // 已知品牌分类
        for (BrandCategory bc : BRAND_CATEGORIES) {
            for (String kw : bc.keywords) {
                if (lower.contains(kw)) {
                    return bc.label;
                }
            }
        }
        // 通用可穿戴关键词
        for (String kw : WEARABLE_GENERIC_KEYWORDS) {
            if (lower.contains(kw)) {
                return "通用心率设备";
            }
        }
        return "通用心率设备";
    }

    /**
     * 判断名称是否匹配小米/Redmi 设备。
     *
     * @param lower 小写设备名称
     * @return {@code true} 表示匹配小米/Redmi
     */
    private static boolean matchesXiaomiOrRedmi(String lower) {
        for (String p : XIAOMI_BAND_PREFIXES) {
            if (lower.startsWith(p) || lower.contains(p)) {
                return true;
            }
        }
        for (String p : XIAOMI_WATCH_PREFIXES) {
            if (lower.startsWith(p) || lower.contains(p)) {
                return true;
            }
        }
        for (String p : REDMI_PREFIXES) {
            if (lower.startsWith(p) || lower.contains(p)) {
                return true;
            }
        }
        for (String p : XIAOMI_GENERIC_PREFIXES) {
            if (lower.startsWith(p) || lower.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据设备名称推断设备类型。
     * <p>匹配方式为大小写不敏感的前缀/包含匹配。无法识别的设备统一归类为
     * {@link DeviceType#STANDARD_GATT}（通用心率广播设备），通过标准 GATT 0x2A37 心率服务解析。</p>
     *
     * @param name 设备名称
     * @return 设备类型；空名称或无法识别时返回 {@link DeviceType#STANDARD_GATT}
     */
    public static DeviceType getDeviceType(String name) {
        if (name == null || name.isBlank()) {
            return DeviceType.STANDARD_GATT;
        }
        String lower = name.toLowerCase(Locale.ROOT);

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
        return DeviceType.STANDARD_GATT;
    }

    /**
     * 品牌分类内部数据结构。
     */
    private static final class BrandCategory {
        final String[] keywords;
        final String label;

        BrandCategory(String[] keywords, String label) {
            this.keywords = keywords;
            this.label = label;
        }
    }
}
