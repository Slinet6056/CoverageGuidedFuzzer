package com.example.fuzzer.schedule.energy;

import java.util.HashMap;
import java.util.Map;

/**
 * 基础能量调度器实现
 */
public class BasicEnergyScheduler implements EnergyScheduler {
    private static final int MIN_ENERGY = 1;
    private static final int INITIAL_ENERGY = 10;
    private static final double ENERGY_LIMIT_FACTOR = 2.0;

    private final Map<byte[], Integer> energyMap;
    private final Map<byte[], Integer> remainingEnergyMap;

    public BasicEnergyScheduler() {
        this.energyMap = new HashMap<>();
        this.remainingEnergyMap = new HashMap<>();
    }

    @Override
    public void assignEnergy(byte[] data) {
        if (!energyMap.containsKey(data)) {
            energyMap.put(data, INITIAL_ENERGY);
            remainingEnergyMap.put(data, INITIAL_ENERGY);
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

    public void updateEnergy(byte[] data, long executionTime, int newBranches) {
        if (!energyMap.containsKey(data)) {
            return;
        }

        // 计算新的能量值
        double timeScore = 1.0 / Math.max(1, executionTime);
        double branchScore = newBranches + 1;

        // 综合评分
        int energy = (int) (INITIAL_ENERGY * timeScore * branchScore);

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
