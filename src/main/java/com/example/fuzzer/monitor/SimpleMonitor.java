package com.example.fuzzer.monitor;

import com.example.fuzzer.execution.ExecutionResult;

// TODO:
// - 实现路径覆盖率统计
// - 添加覆盖率可视化功能
// - 实现覆盖率报告生成
// - 添加分支覆盖率分析
public class SimpleMonitor implements Monitor {
    private byte[] globalCoverage;
    private int mapSize;
    private long startTime;
    private long totalExecutions;
    private long lastUpdateTime;
    private static final long UPDATE_INTERVAL = 1000; // 每秒更新一次
    private int totalEdges;  // 添加总边数统计
    private int coveredEdges;  // 添加已覆盖边数统计

    public SimpleMonitor(int mapSize) {
        this.mapSize = mapSize;
        this.globalCoverage = new byte[mapSize];
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = startTime;
        this.totalExecutions = 0;
        this.totalEdges = mapSize;
        this.coveredEdges = 0;
        updateCoveredEdges();  // 初始化覆盖边数
    }

    @Override
    public void recordResult(ExecutionResult result) {
        totalExecutions++;
        boolean newCoverage = false;

        // 记录边覆盖
        byte[] coverageData = result.getCoverageData();
        for (int i = 0; i < mapSize; i++) {
            if (globalCoverage[i] == 0 && coverageData[i] != 0) {
                globalCoverage[i] = coverageData[i];
                newCoverage = true;
            }
        }

        if (newCoverage) {
            updateCoveredEdges();
        }

        // 定期更新状态
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
            printStatus();
            lastUpdateTime = currentTime;
        }
    }

    private void updateCoveredEdges() {
        coveredEdges = 0;
        for (int i = 0; i < mapSize; i++) {
            if (globalCoverage[i] != 0) {
                coveredEdges++;
            }
        }
    }

    private void printStatus() {
        long runTime = (System.currentTimeMillis() - startTime) / 1000;
        double execPerSec = totalExecutions / (double) Math.max(runTime, 1);
        double coveragePercent = (coveredEdges * 100.0) / totalEdges;

        // 清除当前行
        System.out.print("\r\033[K");

        // 输出状态信息
        System.out.printf("\r[%02d:%02d:%02d] " +
                        "执行次数: %-8d " +
                        "执行速度: %,.0f/s " +
                        "覆盖率: %.2f%% (%d/%d)",
                runTime / 3600, (runTime % 3600) / 60, runTime % 60,
                totalExecutions,
                execPerSec,
                coveragePercent,
                coveredEdges,
                totalEdges);

        System.out.flush();
    }

    public void printFinalStats() {
        System.out.println("\n\n最终测试统计:");
        System.out.println("============================");
        System.out.printf("总执行次数: %d\n", totalExecutions);
        System.out.printf("总运行时间: %d秒\n", (System.currentTimeMillis() - startTime) / 1000);
        System.out.printf("最终覆盖率: %.2f%% (%d/%d)\n",
                (coveredEdges * 100.0) / totalEdges, coveredEdges, totalEdges);
        System.out.println("============================");
    }

    public boolean hasNewCoverage(byte[] coverageData) {
        for (int i = 0; i < mapSize; i++) {
            if (globalCoverage[i] == 0 && coverageData[i] != 0) {
                return true;
            }
        }
        return false;
    }
}
