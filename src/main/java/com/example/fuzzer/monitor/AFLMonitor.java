package com.example.fuzzer.monitor;

import com.example.fuzzer.execution.ExecutionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AFL风格的监控器实现，提供类似AFL++的监控和统计功能
 */
public class AFLMonitor implements Monitor {
    private static final long UPDATE_INTERVAL = 1000; // 每秒更新一次
    private static final String PROGRESS_BAR_CHARS = " ▏▎▍▌▋▊▉█";
    private static final int PROGRESS_BAR_WIDTH = 40;
    private static final long STATUS_UPDATE_INTERVAL = 1000; // 每秒更新一次
    private final byte[] globalCoverage;
    private final int mapSize;
    private final long startTime;
    private final AtomicLong totalExecutions;
    private final int totalEdges;
    private final AtomicInteger coveredEdges;
    private final ReentrantLock coverageLock;
    private final OutputManager outputManager;
    private final AtomicInteger crashCount;
    private final AtomicInteger queueCount;
    private final AtomicInteger hangCount;  // 新增：超时计数
    private final AtomicLong totalExecutionTime;  // 新增：总执行时间
    private final AtomicInteger maxCoverageIncrease;  // 新增：最大覆盖率增长
    private final AtomicLong lastCoverageIncrease;  // 新增：上次覆盖率增长时间
    private final ReentrantLock outputLock = new ReentrantLock(); // 新增：输出锁
    private volatile long lastUpdateTime;
    private volatile double peakExecSpeed;  // 新增：峰值执行速度

    public AFLMonitor(int mapSize, String outputPath) throws IOException {
        this.mapSize = mapSize;
        this.globalCoverage = new byte[mapSize];
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = startTime;
        this.totalExecutions = new AtomicLong(0);
        this.totalEdges = mapSize;
        this.coveredEdges = new AtomicInteger(0);
        this.coverageLock = new ReentrantLock();
        this.outputManager = new OutputManager(outputPath);
        this.crashCount = new AtomicInteger(0);
        this.queueCount = new AtomicInteger(0);
        this.hangCount = new AtomicInteger(0);  // 新增
        this.totalExecutionTime = new AtomicLong(0);  // 新增
        this.maxCoverageIncrease = new AtomicInteger(0);  // 新增
        this.lastCoverageIncrease = new AtomicLong(startTime);  // 新增
        this.peakExecSpeed = 0.0;  // 新增
        updateCoveredEdges();

        // 写入初始设置信息
        writeFuzzerSetup();
    }

    private void writeFuzzerSetup() throws IOException {
        StringBuilder setup = new StringBuilder();
        setup.append("start_time     : ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        setup.append("fuzzer_pid     : ").append(ProcessHandle.current().pid()).append("\n");
        setup.append("map_size       : ").append(mapSize).append("\n");
        outputManager.writeFuzzerSetup(setup.toString());
    }

    @Override
    public void recordResult(ExecutionResult result) {
        if (result == null) {
            return;
        }

        totalExecutions.incrementAndGet();
        boolean newCoverage = false;

        // 记录边覆盖
        byte[] coverageData = result.getCoverageData();
        if (coverageData == null) {
            return;
        }

        try {
            // 保存当前输入
            outputManager.saveCurrentInput(result.getInput());

            coverageLock.lock();
            try {
                for (int i = 0; i < mapSize; i++) {
                    if (globalCoverage[i] == 0 && coverageData[i] != 0) {
                        globalCoverage[i] = coverageData[i];
                        newCoverage = true;
                    }
                }

                if (newCoverage) {
                    updateCoveredEdges();
                    // 保存产生新覆盖的输入
                    String id = String.format("%06d", queueCount.incrementAndGet());
                    outputManager.saveQueueInput(result.getInput(), id);

                    // 只在发现新覆盖时更新统计信息
                    updateStats();
                }

                // 更新bitmap文件
                outputManager.writeFuzzBitmap(globalCoverage);
            } finally {
                coverageLock.unlock();
            }

            // 处理异常情况
            if (result.isTimeout()) {
                // 保存超时输入
                outputManager.saveHangInput(result.getInput(), result.getExecutionTime());
                hangCount.incrementAndGet();
            } else if (result.getExitCode() != 0) {
                // 保存crash输入
                outputManager.saveCrashInput(result.getInput(), result.getExitCode());
                crashCount.incrementAndGet();
            }

        } catch (IOException e) {
            System.err.println("Error writing output: " + e.getMessage());
        }

        // 定期更新状态（降低更新频率）
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= STATUS_UPDATE_INTERVAL) {
            // 使用tryLock避免阻塞
            if (outputLock.tryLock()) {
                try {
                    printStatus();
                    lastUpdateTime = currentTime;
                } finally {
                    outputLock.unlock();
                }
            }
        }
    }

    private void updateStats() throws IOException {
        long currentTime = System.currentTimeMillis();
        long runTime = (currentTime - startTime) / 1000;
        long totalExecs = totalExecutions.get();
        double execPerSec = totalExecs / Math.max(1, runTime);
        double coveragePercent = (coveredEdges.get() * 100.0) / totalEdges;

        StringBuilder stats = new StringBuilder();
        stats.append("start_time     : ").append(startTime).append("\n");
        stats.append("run_time       : ").append(runTime).append("\n");
        stats.append("total_execs    : ").append(totalExecs).append("\n");
        stats.append("execs_per_sec  : ").append(String.format("%.2f", execPerSec)).append("\n");
        stats.append("paths_total    : ").append(queueCount.get()).append("\n");
        stats.append("paths_crashed  : ").append(outputManager.getUniqueCrashCount()).append("\n");
        stats.append("paths_hanged   : ").append(outputManager.getUniqueHangCount()).append("\n");
        stats.append("coverage       : ").append(String.format("%.2f%%", coveragePercent)).append("\n");
        stats.append("edges_covered  : ").append(coveredEdges.get()).append("\n");
        stats.append("total_edges    : ").append(totalEdges).append("\n");

        outputManager.updateFuzzerStats(stats.toString());

        // 更新plot_data
        String plotLine = String.format("%d,%d,%d,%d,%d,%.2f\n",
                runTime,
                totalExecs,
                queueCount.get(),
                outputManager.getUniqueCrashCount(),
                outputManager.getUniqueHangCount(),
                coveragePercent);
        outputManager.appendPlotData(plotLine);
    }

    public void updateStats(ExecutionResult result) {
        if (result == null) {
            return;
        }
        recordResult(result);
    }

    public void printStats() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= STATUS_UPDATE_INTERVAL) {
            printStatus();
            lastUpdateTime = currentTime;
        }
    }

    private void updateCoveredEdges() {
        int covered = 0;
        for (int i = 0; i < mapSize; i++) {
            if (globalCoverage[i] != 0) {
                covered++;
            }
        }
        coveredEdges.set(covered);
    }

    private String getProgressBar(double percentage) {
        StringBuilder bar = new StringBuilder();
        int fullBlocks = (int) ((percentage * PROGRESS_BAR_WIDTH) / 100);
        int remainder = (int) ((percentage * PROGRESS_BAR_WIDTH) % 100);
        int partialBlock = (remainder * (PROGRESS_BAR_CHARS.length() - 1)) / 100;

        // 添加完整的块
        for (int i = 0; i < fullBlocks; i++) {
            bar.append(PROGRESS_BAR_CHARS.charAt(PROGRESS_BAR_CHARS.length() - 1));
        }

        // 添加部分块（如果有）
        if (fullBlocks < PROGRESS_BAR_WIDTH) {
            bar.append(PROGRESS_BAR_CHARS.charAt(partialBlock));
            // 填充剩余空间
            for (int i = fullBlocks + 1; i < PROGRESS_BAR_WIDTH; i++) {
                bar.append(PROGRESS_BAR_CHARS.charAt(0));
            }
        }

        return bar.toString();
    }

    private void printStatus() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < STATUS_UPDATE_INTERVAL) {
            return;
        }

        outputLock.lock();
        try {
            long runTime = (currentTime - startTime) / 1000;
            long totalExecs = totalExecutions.get();
            double execPerSec = totalExecs / Math.max(1, runTime);
            double coveragePercent = (coveredEdges.get() * 100.0) / totalEdges;

            // 更新峰值执行速度
            peakExecSpeed = Math.max(peakExecSpeed, execPerSec);

            // 清除当前行
            System.out.print("\033[2K\r");

            // 状态栏
            System.out.printf("\033[1m\033[36m[%02d:%02d:%02d]\033[0m ",
                    runTime / 3600, (runTime % 3600) / 60, runTime % 60);

            // 执行速度（带峰值）
            System.out.printf("\033[33mexec/s: %,d\033[0m (peak: \033[33m%,d\033[0m) | ",
                    (int) execPerSec, (int) peakExecSpeed);

            // 用例数量
            System.out.printf("\033[1mcases:\033[0m \033[32m%d\033[0m | ", queueCount.get());

            // crash和hang数量
            int crashes = outputManager.getUniqueCrashCount();
            int hangs = outputManager.getUniqueHangCount();
            if (crashes > 0) {
                System.out.printf("\033[1mcrashes:\033[0m \033[31m%d\033[0m | ", crashes);
            } else {
                System.out.printf("\033[1mcrashes:\033[0m %d | ", crashes);
            }
            if (hangs > 0) {
                System.out.printf("\033[1mhangs:\033[0m \033[33m%d\033[0m\n", hangs);
            } else {
                System.out.printf("\033[1mhangs:\033[0m %d\n", hangs);
            }

            // 覆盖率进度条（使用彩色输出）
            System.out.printf("\033[1mCoverage:\033[0m \033[36m%s\033[0m \033[1m%.1f%%\033[0m",
                    getProgressBar(coveragePercent),
                    coveragePercent);

            // 覆盖率详情
            System.out.printf(" (\033[32m%d\033[0m/\033[33m%d\033[0m edges)",
                    coveredEdges.get(), totalEdges);

            // 上次覆盖率增长时间
            long timeSinceLastCoverage = (currentTime - lastCoverageIncrease.get()) / 1000;
            if (timeSinceLastCoverage > 300) {  // 5分钟没有新覆盖
                System.out.printf(" | \033[31m%02d:%02d:%02d\033[0m since new coverage",
                        timeSinceLastCoverage / 3600,
                        (timeSinceLastCoverage % 3600) / 60,
                        timeSinceLastCoverage % 60);
            }

            System.out.print("\r");
            lastUpdateTime = currentTime;
        } finally {
            outputLock.unlock();
        }
    }

    public void printFinalStats() {
        outputLock.lock();
        try {
            System.out.println("\n\n\033[1m最终测试统计:\033[0m");
            System.out.println("\033[36m============================\033[0m");

            // 总执行次数和速度
            long totalExecs = totalExecutions.get();
            System.out.printf("\033[1m总执行次数:\033[0m %,d\n", totalExecs);

            // 总运行时间
            long totalTime = (System.currentTimeMillis() - startTime) / 1000;
            System.out.printf("\033[1m总运行时间:\033[0m %02d:%02d:%02d\n",
                    totalTime / 3600, (totalTime % 3600) / 60, totalTime % 60);

            // 执行速度统计
            double avgExecPerSec = totalExecs / Math.max(1, totalTime);
            System.out.printf("\033[1m平均执行速度:\033[0m %,.0f exec/s\n", avgExecPerSec);
            System.out.printf("\033[1m峰值执行速度:\033[0m %,.0f exec/s\n", peakExecSpeed);

            // 覆盖率统计
            double coveragePercent = (coveredEdges.get() * 100.0) / totalEdges;
            System.out.printf("\033[1m最终覆盖率:\033[0m %.2f%% (\033[32m%d\033[0m/\033[33m%d\033[0m edges)\n",
                    coveragePercent, coveredEdges.get(), totalEdges);

            // 测试用例统计
            System.out.printf("\033[1m有效测试用例:\033[0m %d\n", queueCount.get());

            // crash统计
            int crashes = outputManager.getUniqueCrashCount();
            if (crashes > 0) {
                System.out.printf("\033[1m发现Crash数量:\033[0m \033[31m%d\033[0m\n", crashes);
            } else {
                System.out.printf("\033[1m发现Crash数量:\033[0m %d\n", crashes);
            }

            // hang统计
            int hangs = outputManager.getUniqueHangCount();
            if (hangs > 0) {
                System.out.printf("\033[1m发现Hang数量:\033[0m \033[33m%d\033[0m\n", hangs);
            } else {
                System.out.printf("\033[1m发现Hang数量:\033[0m %d\n", hangs);
            }

            // 性能统计
            double avgExecTime = totalExecutionTime.get() / (double) totalExecs;
            System.out.printf("\033[1m平均执行时间:\033[0m %.2f ms\n", avgExecTime);

            // 覆盖率增长统计
            System.out.printf("\033[1m最大单次覆盖率增长:\033[0m %.2f%%\n",
                    (maxCoverageIncrease.get() * 100.0) / totalEdges);

            System.out.println("\033[36m============================\033[0m");

            // 生成覆盖率报告
            try {
                generateCoverageReport();
            } catch (IOException e) {
                System.err.println("生成覆盖率报告失败: " + e.getMessage());
            }
        } finally {
            outputLock.unlock();
        }
    }

    private void generateCoverageReport() throws IOException {
        // 生成HTML格式的覆盖率报告
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
                .append("<html><head><title>Coverage Report</title>\n")
                .append("<style>\n")
                .append("body { font-family: Arial, sans-serif; margin: 20px; }\n")
                .append(".progress-bar { width: 100%; background-color: #f0f0f0; }\n")
                .append(".progress { height: 20px; background-color: #4CAF50; }\n")
                .append(".stats { margin: 20px 0; }\n")
                .append(".stats table { border-collapse: collapse; width: 100%; }\n")
                .append(".stats td, .stats th { border: 1px solid #ddd; padding: 8px; }\n")
                .append(".stats th { background-color: #4CAF50; color: white; }\n")
                .append("</style></head><body>\n")
                .append("<h1>Fuzzing Coverage Report</h1>\n");

        // 添加覆盖率进度条
        double coveragePercent = (coveredEdges.get() * 100.0) / totalEdges;
        html.append("<div class='progress-bar'>\n")
                .append("<div class='progress' style='width: ").append(coveragePercent).append("%'></div>\n")
                .append("</div>\n")
                .append("<p>Coverage: ").append(String.format("%.2f%%", coveragePercent)).append("</p>\n");

        // 添加统计表格
        html.append("<div class='stats'>\n")
                .append("<table>\n")
                .append("<tr><th>Metric</th><th>Value</th></tr>\n")
                .append(String.format("<tr><td>Total Executions</td><td>%,d</td></tr>\n", totalExecutions.get()))
                .append(String.format("<tr><td>Execution Speed</td><td>%,.0f/s</td></tr>\n",
                        totalExecutions.get() / Math.max(1, (System.currentTimeMillis() - startTime) / 1000.0)))
                .append(String.format("<tr><td>Peak Speed</td><td>%,.0f/s</td></tr>\n", peakExecSpeed))
                .append(String.format("<tr><td>Queue Size</td><td>%d</td></tr>\n", queueCount.get()))
                .append(String.format("<tr><td>Crashes</td><td>%d</td></tr>\n", outputManager.getUniqueCrashCount()))
                .append(String.format("<tr><td>Hangs</td><td>%d</td></tr>\n", outputManager.getUniqueHangCount()))
                .append("</table>\n")
                .append("</div>\n")
                .append("</body></html>");

        // 写入覆盖率报告
        Path reportPath = outputManager.getOutputDir().resolve("coverage_report.html");
        Files.write(reportPath, html.toString().getBytes(), StandardOpenOption.CREATE);
    }

    public boolean hasNewCoverage(byte[] coverageData) {
        coverageLock.lock();
        try {
            for (int i = 0; i < mapSize; i++) {
                if (globalCoverage[i] == 0 && coverageData[i] != 0) {
                    return true;
                }
            }
            return false;
        } finally {
            coverageLock.unlock();
        }
    }

    public OutputManager getOutputManager() {
        return outputManager;
    }
}
