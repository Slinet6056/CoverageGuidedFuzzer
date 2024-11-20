package com.example.fuzzer.schedule.energy;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于覆盖率的能量调度器实现
 * 根据种子发现的新边数量来动态调整能量值
 */
public class CoverageBasedEnergyScheduler implements EnergyScheduler {
    private static final int MIN_ENERGY = 1;
    private static final int INITIAL_ENERGY = 10;
    private static final double ENERGY_LIMIT_FACTOR = 3.0; // 允许更高的能量上限
    private static final double COVERAGE_WEIGHT = 2.0; // 覆盖率权重
    private static final double TIME_WEIGHT = 0.5; // 时间权重

    private final Map<byte[], Integer> energyMap;
    private final Map<byte[], Integer> remainingEnergyMap;
    private final Map<byte[], Integer> totalNewEdgesMap; // 记录每个种子累计发现的新边

    public CoverageBasedEnergyScheduler() {
        this.energyMap = new HashMap<>();
        this.remainingEnergyMap = new HashMap<>();
        this.totalNewEdgesMap = new HashMap<>();
    }

    @Override
    public void assignEnergy(byte[] data) {
        if (!energyMap.containsKey(data)) {
            energyMap.put(data, INITIAL_ENERGY);
            remainingEnergyMap.put(data, INITIAL_ENERGY);
            totalNewEdgesMap.put(data, 0);
        }
    }

    @Override
    public boolean hasEnergy(byte[] data) {
        Integer remainingEnergy = remainingEnergyMap.get(data);
        return remainingEnergy != null && remainingEnergy > 0;
    }

    @Override
    public void consumeEnergy(byte[] data) {
        if (remainingEnergyMap.containsKey(data)) {
            int remaining = remainingEnergyMap.get(data);
            if (remaining > 0) {
                remainingEnergyMap.put(data, remaining - 1);
            }
        }
    }

    @Override
    public void updateEnergy(byte[] data, long executionTime, int newBranches) {
        if (!energyMap.containsKey(data)) {
            return;
        }

        // 更新累计发现的新边数量
        int totalNewEdges = totalNewEdgesMap.getOrDefault(data, 0) + newBranches;
        totalNewEdgesMap.put(data, totalNewEdges);

        // 计算覆盖率分数
        double coverageScore = Math.log1p(totalNewEdges) * COVERAGE_WEIGHT;

        // 计算时间效率分数
        double timeScore = 1.0 / Math.max(1, Math.sqrt(executionTime)) * TIME_WEIGHT;

        // 如果这次执行发现了新边，给予额外奖励
        double newEdgeBonus = newBranches > 0 ? 2.0 : 1.0;

        // 综合评分计算新的能量值
        int energy = (int) (INITIAL_ENERGY * (coverageScore + timeScore) * newEdgeBonus);

        // 限制能量范围
        energy = Math.max(MIN_ENERGY, Math.min(energy, (int) (INITIAL_ENERGY * ENERGY_LIMIT_FACTOR)));

        // 更新能量值
        energyMap.put(data, energy);
        remainingEnergyMap.put(data, energy);
    }

    public void resetEnergy(byte[] data) {
        if (energyMap.containsKey(data)) {
            int energy = energyMap.get(data);
            remainingEnergyMap.put(data, energy);
        }
    }
}
