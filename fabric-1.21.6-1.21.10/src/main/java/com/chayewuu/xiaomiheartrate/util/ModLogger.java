package com.chayewuu.xiaomiheartrate.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod 统一日志封装。
 * <p>
 * 基于 SLF4J，所有模块统一通过本类输出日志，前缀 {@code [HeartRateMod]}，
 * 便于在游戏日志中筛选与排查。禁止在业务代码中使用 {@code System.out.println}。
 * </p>
 *
 * <p>该类为工具类，禁止实例化。</p>
 */
public final class ModLogger {
    /** SLF4J 日志器实例 */
    private static final Logger LOGGER = LoggerFactory.getLogger("HeartRateMod");

    /** 私有构造器，禁止实例化 */
    private ModLogger() {
    }

    /**
     * 输出 INFO 级别日志。
     *
     * @param message 日志消息，支持 SLF4J 占位符 {@code {}}
     * @param args    占位符参数
     */
    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    /**
     * 输出 WARN 级别日志。
     *
     * @param message 日志消息，支持 SLF4J 占位符 {@code {}}
     * @param args    占位符参数
     */
    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }

    /**
     * 输出 ERROR 级别日志。
     *
     * @param message 日志消息，支持 SLF4J 占位符 {@code {}}
     * @param args    占位符参数
     */
    public static void error(String message, Object... args) {
        LOGGER.error(message, args);
    }

    /**
     * 输出 ERROR 级别日志（含异常堆栈）。
     *
     * @param message   日志消息
     * @param throwable 异常对象
     */
    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }

    /**
     * 输出 DEBUG 级别日志。
     *
     * @param message 日志消息，支持 SLF4J 占位符 {@code {}}
     * @param args    占位符参数
     */
    public static void debug(String message, Object... args) {
        LOGGER.debug(message, args);
    }
}
