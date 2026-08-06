package com.chayewuu.hyperheartrate.device;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 心率解析器注册表。
 * <p>
 * 维护按优先级排序的 {@link HeartRateParser} 列表，提供基于设备类型的解析器路由。
 * 上层（如 {@code HeartRateNotificationHandler}）在收到 BLE 通知时，
 * 通过 {@link #getParser(BleDevice)} 获取第一个能处理该设备的解析器。
 * </p>
 *
 * <p><b>默认优先级（从高到低）：</b></p>
 * <ol>
 *     <li>{@link XiaomiParser} — 小米手环/手表私有协议（兼容标准 GATT）；</li>
 *     <li>{@link RedmiParser} — Redmi Watch 私有协议（兼容标准 GATT）；</li>
 *     <li>{@link StandardGattHeartRateParser} — 标准 GATT 0x2A37 兜底解析器。</li>
 * </ol>
 *
 * <p><b>可扩展性：</b>新增解析器只需实现 {@link HeartRateParser} 接口，
 * 通过 {@link #register(HeartRateParser)} 或 {@link #register(int, HeartRateParser)} 注册，
 * 或直接修改默认构造器中的初始化列表。</p>
 *
 * <p><b>线程安全：</b>解析器列表在构造时确定后通过不可变视图暴露，
 * 注册方法使用 {@code synchronized} 保护；解析器本身应是无状态的。</p>
 */
public class HeartRateParserRegistry {
    /** 按优先级排序的解析器列表（高优先级在前） */
    private final List<HeartRateParser> parsers = new ArrayList<>();

    /** 不可变视图缓存，避免每次调用都创建新视图 */
    private volatile List<HeartRateParser> parsersView = Collections.emptyList();

    /**
     * 默认构造器，注册内置解析器并按优先级排序。
     */
    public HeartRateParserRegistry() {
        // 按优先级添加（私有协议优先，标准 GATT 兜底）
        parsers.add(new XiaomiParser());
        parsers.add(new RedmiParser());
        parsers.add(new StandardGattHeartRateParser());
        refreshView();
    }

    /**
     * 在列表末尾追加解析器（优先级最低）。
     *
     * @param parser 待注册解析器，{@code null} 忽略
     */
    public synchronized void register(HeartRateParser parser) {
        if (parser == null) {
            return;
        }
        parsers.add(parser);
        refreshView();
    }

    /**
     * 在指定位置插入解析器。
     *
     * @param index  插入位置（0 表示最高优先级）
     * @param parser 待注册解析器，{@code null} 忽略
     */
    public synchronized void register(int index, HeartRateParser parser) {
        if (parser == null) {
            return;
        }
        parsers.add(index, parser);
        refreshView();
    }

    /**
     * 获取第一个能处理指定设备的解析器。
     * <p>按优先级顺序遍历，返回首个 {@link HeartRateParser#canParse(BleDevice)}
     * 返回 {@code true} 的解析器；若全部不匹配则返回末尾的标准解析器兜底。</p>
     *
     * @param device 目标设备，{@code null} 时返回兜底解析器
     * @return 匹配的解析器；注册列表为空时返回 {@code null}
     */
    public HeartRateParser getParser(BleDevice device) {
        List<HeartRateParser> snapshot = parsersView;
        for (HeartRateParser parser : snapshot) {
            if (parser.canParse(device)) {
                return parser;
            }
        }
        // 兜底：返回末尾解析器（通常为 StandardGattHeartRateParser）
        if (snapshot.isEmpty()) {
            return null;
        }
        return snapshot.get(snapshot.size() - 1);
    }

    /**
     * 获取已注册解析器数量。
     *
     * @return 解析器数量
     */
    public int size() {
        return parsersView.size();
    }

    /**
     * 获取已注册解析器的不可变视图。
     *
     * @return 解析器列表（不可变）
     */
    public List<HeartRateParser> getParsers() {
        return parsersView;
    }

    /**
     * 刷新不可变视图缓存（在注册方法中调用）。
     */
    private void refreshView() {
        this.parsersView = Collections.unmodifiableList(new ArrayList<>(parsers));
    }
}
