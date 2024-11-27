package com.example.fuzzer.monitor;

import com.example.fuzzer.execution.ExecutionResult;

import java.io.IOException;
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
    private String targetProgram;
    private String[] programArgs;
    private String outputPath;
    private volatile long lastFindTime;
    private volatile long lastCrashTime;
    private volatile long lastHangTime;

    public AFLMonitor(int mapSize, String outputPath) throws IOException {
        this.mapSize = mapSize;
        this.outputPath = outputPath;
        this.globalCoverage = new byte[mapSize];
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = startTime;
        this.lastFindTime = startTime;
        this.lastCrashTime = 0;
        this.lastHangTime = 0;
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
    }

    public void setTargetInfo(String targetProgram, String[] programArgs) {
        this.targetProgram = targetProgram;
        this.programArgs = programArgs;
        try {
            writeFuzzerSetup();
        } catch (IOException e) {
            System.err.println("Error writing fuzzer setup: " + e.getMessage());
        }
    }

    private void writeFuzzerSetup() throws IOException {
        outputManager.writeFuzzerSetup(targetProgram, programArgs, outputPath);
    }

    @Override
    public void recordResult(ExecutionResult result) {
        if (result == null) {
            return;
        }

        long execCount = totalExecutions.incrementAndGet();
        result.setExecutionCount(execCount);
        boolean newCoverage = false;

        // 记录边覆盖
        byte[] coverageData = result.getCoverageData();
        if (coverageData == null) {
            return;
        }

        try {
            coverageLock.lock();
            try {
                for (int i = 0; i < mapSize; i++) {
                    if (globalCoverage[i] == 0 && coverageData[i] != 0) {
                        globalCoverage[i] = coverageData[i];
                        newCoverage = true;
                    }
                }

                // 保存所有输入到队列
                String id = String.format("%06d", queueCount.incrementAndGet());
                outputManager.saveQueueInput(result.getInput(), id, result, newCoverage);

                if (newCoverage) {
                    updateCoveredEdges();
                    lastFindTime = System.currentTimeMillis();
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
                lastHangTime = System.currentTimeMillis();
            } else if (result.getExitCode() != 0) {
                // 保存crash输入
                outputManager.saveCrashInput(result.getInput(), result.getExitCode());
                crashCount.incrementAndGet();
                lastCrashTime = System.currentTimeMillis();
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

        outputManager.updateFuzzerStats(
                startTime,
                totalExecs,
                execPerSec,
                queueCount.get(),
                crashCount.get(),
                hangCount.get(),
                coveragePercent,
                coveredEdges.get(),
                lastFindTime,
                lastCrashTime,
                lastHangTime
        );

        // 更新plot_data
        String plotLine = String.format("%d,%d,%d,%d,%d,%.2f\n",
                runTime,
                totalExecs,
                queueCount.get(),
                crashCount.get(),
                hangCount.get(),
                coveragePercent);
        outputManager.appendPlotData(plotLine);

        // 生成可读的覆盖率报告
        try {
            outputManager.writeCoverageReport(globalCoverage, totalExecutions, startTime,
                    peakExecSpeed, queueCount.get(), crashCount.get(), hangCount.get());
        } catch (IOException e) {
            System.err.println("生成覆盖率报告失败: " + e.getMessage());
        }
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
                outputManager.writeCoverageReport(globalCoverage, totalExecutions, startTime,
                        peakExecSpeed, queueCount.get(), crashCount.get(), hangCount.get());
            } catch (IOException e) {
                System.err.println("生成覆盖率报告失败: " + e.getMessage());
            }
        } finally {
            outputLock.unlock();
        }
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
