package com.example.fuzzer.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 模糊测试日志管理类
 * 使用单例模式确保全局只有一个日志实例
 */
public class FuzzingLogger {
    // 定义不同类型的日志文件
    public static final String GENERAL_LOG = "fuzzer.log";
    public static final String ERROR_LOG = "error.log";
    public static final String COVERAGE_LOG = "coverage.log";
    public static final String PERFORMANCE_LOG = "performance.log";
    public static final String MUTATION_LOG = "mutation.log";
    public static final String CRASH_LOG = "crash.log";
    private static final ReentrantLock instanceLock = new ReentrantLock();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String HEX_CHARS = "0123456789ABCDEF";
    private static volatile FuzzingLogger instance;
    private final Path logsDir;
    private final ConcurrentHashMap<String, ReentrantLock> fileLocks;

    private FuzzingLogger(Path logsDirectory) throws IOException {
        this.logsDir = logsDirectory;
        this.fileLocks = new ConcurrentHashMap<>();
        initializeLogDirectory();
    }

    public static FuzzingLogger createInstance(Path logsDirectory) throws IOException {
        if (instance != null) {
            throw new IllegalStateException("FuzzingLogger instance already exists");
        }

        instanceLock.lock();
        try {
            if (instance != null) {
                throw new IllegalStateException("FuzzingLogger instance already exists");
            }
            instance = new FuzzingLogger(logsDirectory);
            return instance;
        } finally {
            instanceLock.unlock();
        }
    }

    public static FuzzingLogger getInstance() {
        if (instance == null) {
            throw new IllegalStateException("FuzzingLogger not initialized. Call createInstance first.");
        }
        return instance;
    }

    private void initializeLogDirectory() throws IOException {
        if (!Files.exists(logsDir)) {
            Files.createDirectories(logsDir);
        }
    }

    private ReentrantLock getOrCreateLock(String logFile) {
        return fileLocks.computeIfAbsent(logFile, k -> new ReentrantLock());
    }

    /**
     * 写入一般信息日志
     */
    public void info(String message) throws IOException {
        log(GENERAL_LOG, "INFO", message);
    }

    /**
     * 写入警告日志
     */
    public void warn(String message) throws IOException {
        log(GENERAL_LOG, "WARN", message);
    }

    /**
     * 写入错误日志
     */
    public void error(String message, Throwable throwable) throws IOException {
        StringBuilder errorMessage = new StringBuilder(message);
        if (throwable != null) {
            errorMessage.append("\nException: ").append(throwable.getClass().getName())
                    .append("\nMessage: ").append(throwable.getMessage())
                    .append("\nStack trace:\n");
            for (StackTraceElement element : throwable.getStackTrace()) {
                errorMessage.append("\tat ").append(element.toString()).append("\n");
            }
        }
        log(ERROR_LOG, "ERROR", errorMessage.toString());
    }

    /**
     * 写入覆盖率相关日志
     *
     * @param seedId 种子ID
     */
    public void coverage(String seedId) throws IOException {
        log(COVERAGE_LOG, "COVERAGE", "Seed ID: " + seedId);
    }

    /**
     * 写入性能相关日志
     */
    public void performance(String message) throws IOException {
        log(PERFORMANCE_LOG, "PERFORMANCE", message);
    }

    /**
     * 写入变异操作相关日志
     */
    public void mutation(String message) throws IOException {
        log(MUTATION_LOG, "MUTATION", message);
    }

    /**
     * 写入crash信息日志
     *
     * @param input        导致crash的输入数据
     * @param exitCode     程序退出码
     * @param errorMessage 错误信息
     */
    public void crash(byte[] input, int exitCode, String errorMessage) throws IOException {
        StringBuilder crashInfo = new StringBuilder();
        crashInfo.append("\nInput[").append(input.length).append("]: ");

        // 将byte[]转换为十六进制表示
        for (byte b : input) {
            crashInfo.append(String.format("%02X", b));
        }
        crashInfo.append('\n');

        crashInfo.append("ExitCode: ").append(exitCode).append('\n');
        if (errorMessage != null) {
            crashInfo.append("Error: ").append(errorMessage);
        }

        log(CRASH_LOG, "CRASH", crashInfo.toString());
    }

    /**
     * 写入调试日志
     */
    public void debug(String message) throws IOException {
        if (isDebugEnabled()) {
            log(GENERAL_LOG, "DEBUG", message);
        }
    }

    private boolean isDebugEnabled() {
        return Boolean.getBoolean("fuzzer.debug");
    }

    /**
     * 核心日志写入方法
     */
    private void log(String logFile, String level, String message) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logEntry = String.format("[%s] [%s] %s%n", timestamp, level, message);

        ReentrantLock lock = getOrCreateLock(logFile);
        lock.lock();
        try {
            Path logPath = logsDir.resolve(logFile);
            Files.write(logPath, logEntry.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清理指定日志文件
     */
    public void clearLog(String logFile) throws IOException {
        ReentrantLock lock = getOrCreateLock(logFile);
        lock.lock();
        try {
            Path logPath = logsDir.resolve(logFile);
            if (Files.exists(logPath)) {
                Files.write(logPath, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取日志目录
     */
    public Path getLogsDirectory() {
        return logsDir;
    }
}
