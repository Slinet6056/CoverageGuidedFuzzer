package com.example.fuzzer.monitor;

import com.example.fuzzer.execution.ExecutionResult;

public class SimpleMonitor implements Monitor {
    private byte[] globalCoverage;
    private int mapSize;

    public SimpleMonitor(int mapSize) {
        this.mapSize = mapSize;
        globalCoverage = new byte[mapSize];
    }

    @Override
    public void recordResult(ExecutionResult result) {
        byte[] coverageData = result.getCoverageData();
        boolean newCoverage = false;

        // 比较并更新全局覆盖率位图
        for (int i = 0; i < mapSize; i++) {
            int prev = globalCoverage[i] & 0xFF;
            int curr = coverageData[i] & 0xFF;
            if ((curr > 0) && (prev == 0)) {
                globalCoverage[i] = coverageData[i];
                newCoverage = true;
            }
        }

        if (newCoverage) {
            System.out.println("发现新的覆盖！");
            // 将当前输入作为新种子
            // 需要在外部处理将新种子添加到种子队列
        }
    }

    // 提供方法判断是否有新覆盖
    public boolean hasNewCoverage(byte[] coverageData) {
        for (int i = 0; i < mapSize; i++) {
            int prev = globalCoverage[i] & 0xFF;
            int curr = coverageData[i] & 0xFF;
            if ((curr > 0) && (prev == 0)) {
                return true;
            }
        }
        return false;
    }
}
