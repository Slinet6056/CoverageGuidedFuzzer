package com.example.fuzzer.monitor;

import com.example.fuzzer.execution.ExecutionResult;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

// TODO:
// - 实现路径覆盖率统计
// - 添加覆盖率可视化功能
// - 实现覆盖率报告生成
// - 添加分支覆盖率分析
public class SimpleMonitor implements Monitor {
    private static final long UPDATE_INTERVAL = 1000; // 每秒更新一次
    private final byte[] globalCoverage;
    private final int mapSize;
    private final long startTime;
    private final AtomicLong totalExecutions;
    private final int totalEdges;
    private final AtomicInteger coveredEdges;
    private final ReentrantLock coverageLock;
    private volatile long lastUpdateTime;

    public SimpleMonitor(int mapSize) {
        this.mapSize = mapSize;
        this.globalCoverage = new byte[mapSize];
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = startTime;
        this.totalExecutions = new AtomicLong(0);
        this.totalEdges = mapSize;
        this.coveredEdges = new AtomicInteger(0);
        this.coverageLock = new ReentrantLock();
        updateCoveredEdges();
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
            }
        } finally {
            coverageLock.unlock();
        }

        // 定期更新状态
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
            printStatus();
            lastUpdateTime = currentTime;
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
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
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

    private void printStatus() {
        long runTime = (System.currentTimeMillis() - startTime) / 1000;
        long totalExecs = totalExecutions.get();
        double execPerSec = totalExecs / (double) Math.max(runTime, 1);
        double coveragePercent = (coveredEdges.get() * 100.0) / totalEdges;

        // 清除当前行
        System.out.print("\r\033[K");

        // 输出状态信息
        System.out.printf("\r[%02d:%02d:%02d] " +
                        "执行次数: %-8d " +
                        "执行速度: %,.0f/s " +
                        "覆盖率: %.2f%% (%d/%d)",
                runTime / 3600, (runTime % 3600) / 60, runTime % 60,
                totalExecs,
                execPerSec,
                coveragePercent,
                coveredEdges.get(),
                totalEdges);

        System.out.flush();
    }

    public void printFinalStats() {
        System.out.println("\n\n最终测试统计:");
        System.out.println("============================");
        System.out.printf("总执行次数: %d\n", totalExecutions.get());
        System.out.printf("总运行时间: %d秒\n", (System.currentTimeMillis() - startTime) / 1000);
        System.out.printf("最终覆盖率: %.2f%% (%d/%d)\n",
                (coveredEdges.get() * 100.0) / totalEdges, coveredEdges.get(), totalEdges);
        System.out.println("============================");
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
}
