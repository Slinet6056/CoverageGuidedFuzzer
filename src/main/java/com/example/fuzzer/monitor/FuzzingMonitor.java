package com.example.fuzzer.monitor;

import com.example.fuzzer.execution.ExecutionResult;
import com.example.fuzzer.schedule.sort.SeedSorter;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AFL风格的监控器实现，提供类似AFL++的监控和统计功能
 */
public class FuzzingMonitor implements Monitor, AutoCloseable {
    private static final String PROGRESS_BAR_CHARS = " ▏▎▍▌▋▊▉█";
    private static final int PROGRESS_BAR_WIDTH = 40;
    private static final long STATUS_UPDATE_INTERVAL = 1000; // 每秒更新一次
    private static final byte[] COUNT_CLASS_LOOKUP = new byte[256];

    static {
        // Initialize count class lookup as per AFL++
        for (int i = 0; i < 256; i++) {
            if (i == 0) COUNT_CLASS_LOOKUP[i] = 0;
            else if (i == 1) COUNT_CLASS_LOOKUP[i] = 1;
            else if (i < 4) COUNT_CLASS_LOOKUP[i] = 2;
            else if (i < 8) COUNT_CLASS_LOOKUP[i] = 3;
            else if (i < 16) COUNT_CLASS_LOOKUP[i] = 4;
            else if (i < 32) COUNT_CLASS_LOOKUP[i] = 5;
            else if (i < 128) COUNT_CLASS_LOOKUP[i] = 6;
            else COUNT_CLASS_LOOKUP[i] = 7;
        }
    }

    private final byte[] globalCoverage;
    private final int mapSize;
    private final long startTime;
    private final AtomicLong totalExecutions;
    private final int totalEdges;
    private final AtomicInteger coveredEdges;
    private final ReentrantLock coverageLock;
    private final OutputManager outputManager;
    private final SeedSorter seedSorter;  // 新增：种子排序器引用
    private final AtomicInteger crashCount;
    private final AtomicInteger newCoverageCount;
    private final AtomicInteger hangCount;  // 新增：超时计数
    private final AtomicLong totalExecutionTime;  // 新增：总执行时间
    private final AtomicLong lastCoverageIncrease;  // 新增：上次覆盖率增长时间
    private final ReentrantLock outputLock = new ReentrantLock(); // 新增：输出锁
    private final ScheduledExecutorService statusUpdater;
    private volatile long lastUpdateTime;
    private volatile double peakExecSpeed;  // 新增：峰值执行速度
    private String targetProgram;
    private String[] programArgs;
    private String outputPath;
    private volatile long lastFindTime;
    private volatile long lastCrashTime;
    private volatile long lastHangTime;

    public FuzzingMonitor(int mapSize, String outputPath, SeedSorter seedSorter) throws IOException {
        this.mapSize = mapSize;
        this.outputPath = outputPath;
        this.seedSorter = seedSorter;  // 新增：初始化种子排序器
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
        this.newCoverageCount = new AtomicInteger(0);
        this.hangCount = new AtomicInteger(0);  // 新增
        this.totalExecutionTime = new AtomicLong(0);  // 新增
        this.lastCoverageIncrease = new AtomicLong(startTime);  // 新增
        this.peakExecSpeed = 0.0;  // 新增

        // Initialize status updater
        this.statusUpdater = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AFL-Status-Updater");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic status updates
        this.statusUpdater.scheduleAtFixedRate(
                this::updateStatusPeriodically,
                STATUS_UPDATE_INTERVAL,
                STATUS_UPDATE_INTERVAL,
                TimeUnit.MILLISECONDS
        );

        updateCoveredEdges();
    }

    private byte classifyCount(byte count) {
        return COUNT_CLASS_LOOKUP[count & 0xFF];
    }

    private void updateStatusPeriodically() {
        if (outputLock.tryLock()) {
            try {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdateTime >= STATUS_UPDATE_INTERVAL) {
                    printStatus();
                    lastUpdateTime = currentTime;
                }
            } catch (Exception e) {
                System.err.println("Error updating status: " + e.getMessage());
            } finally {
                outputLock.unlock();
            }
        }
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

        try {
            // 处理异常情况优先
            if (result.isTimeout()) {
                // 保存超时输入
                long executionTime = result.getExecutionTime();
                outputManager.saveHangInput(result.getInput(), executionTime);
                hangCount.incrementAndGet();
                lastHangTime = System.currentTimeMillis();

                // Update execution statistics
                totalExecutionTime.addAndGet(executionTime);

                // 更新统计信息
                try {
                    updateStats();
                } catch (IOException e) {
                    System.err.println("更新统计信息失败: " + e.getMessage());
                }
                return; // Skip coverage processing for timeout cases
            }

            // 记录边覆盖
            byte[] coverageData = result.getCoverageData();
            if (coverageData == null) {
                return;
            }

            coverageLock.lock();
            try {
                for (int i = 0; i < mapSize; i++) {
                    byte newClass = classifyCount(coverageData[i]);
                    byte oldClass = classifyCount(globalCoverage[i]);

                    if (newClass != oldClass) {
                        // 如果执行次数的分类不同，说明找到了新的路径
                        globalCoverage[i] = coverageData[i];
                        if (oldClass == 0) {
                            // 如果是从未执行变成执行，这是新的覆盖
                            newCoverage = true;
                        }
                    } else if (coverageData[i] > globalCoverage[i]) {
                        // 更新最大执行次数
                        globalCoverage[i] = coverageData[i];
                    }
                }

                if (newCoverage) {
                    // 只有新覆盖才保存到队列
                    String id = String.format("%06d", newCoverageCount.incrementAndGet());
                    outputManager.saveQueueInput(result.getInput(), id, result, true);
                    updateCoveredEdges();
                    updateStats();
                    lastCoverageIncrease.set(System.currentTimeMillis());  // 更新最后一次覆盖率增长时间
                }

                // 更新bitmap文件
                outputManager.writeFuzzBitmap(globalCoverage);
            } finally {
                coverageLock.unlock();
            }

            if (result.getExitCode() != 0) {
                // 保存crash输入
                outputManager.saveCrashInput(result);
                crashCount.incrementAndGet();
                lastCrashTime = System.currentTimeMillis();
            }

            // Update execution statistics
            totalExecutionTime.addAndGet(result.getExecutionTime());

        } catch (IOException e) {
            System.err.println("Error writing output: " + e.getMessage());
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
                newCoverageCount.get(),
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
                newCoverageCount.get(),
                crashCount.get(),
                hangCount.get(),
                coveragePercent);
        outputManager.appendPlotData(plotLine);

        // 生成可读的覆盖率报告
        try {
            outputManager.writeCoverageReport(globalCoverage, totalExecutions, startTime,
                    peakExecSpeed, newCoverageCount.get(), crashCount.get(), hangCount.get());
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

            // pool和用例数量
            System.out.printf("pool: \033[32m%d\033[0m | \033[1mcases:\033[0m \033[32m%d\033[0m | ",
                    seedSorter.size(), newCoverageCount.get());

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

            // 覆盖率进度条
            System.out.printf("\033[1mCoverage:\033[0m \033[36m%s\033[0m \033[1m%.1f%%\033[0m",
                    getProgressBar(coveragePercent),
                    coveragePercent);

            // 覆盖率详情
            System.out.printf(" (\033[32m%d\033[0m/\033[33m%d\033[0m edges)",
                    coveredEdges.get(), totalEdges);

            // 上次覆盖率增长时间
            long timeSinceLastCoverage = (currentTime - lastCoverageIncrease.get()) / 1000;
            if (timeSinceLastCoverage > 300) {  // 5分钟没有新覆盖
                System.out.printf(" | \033[31m%02d:%02d:%02d\033[0m since new path",
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

    /**
     * 手动保存所有pending的crash
     * 这个方法可以在任何时候调用来确保所有crash都被保存
     */
    public void flushCrashes() {
        if (outputManager != null) {
            outputManager.flushCrashes();
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
            System.out.printf("\033[1m有效测试用例:\033[0m %d\n", newCoverageCount.get());

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

            System.out.println("\033[36m============================\033[0m");

            // 生成覆盖率报告
            try {
                outputManager.writeCoverageReport(globalCoverage, totalExecutions, startTime,
                        peakExecSpeed, newCoverageCount.get(), crashCount.get(), hangCount.get());
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
                byte newClass = classifyCount(coverageData[i]);
                byte oldClass = classifyCount(globalCoverage[i]);

                if (newClass != oldClass) {
                    // 如果执行次数的分类不同，说明找到了新的路径
                    return true;
                } else if (coverageData[i] > globalCoverage[i]) {
                    // 更新最大执行次数
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

    @Override
    public void close() {
        if (statusUpdater != null) {
            statusUpdater.shutdown();
            try {
                if (!statusUpdater.awaitTermination(1, TimeUnit.SECONDS)) {
                    statusUpdater.shutdownNow();
                }
            } catch (InterruptedException e) {
                statusUpdater.shutdownNow();
            }
        }

        // 关闭outputManager
        if (outputManager != null) {
            try {
                outputManager.close();
            } catch (Exception e) {
                System.err.println("Error closing output manager: " + e.getMessage());
            }
        }
    }
}
