package com.example.fuzzer.schedule.core;

import com.example.fuzzer.schedule.energy.EnergyScheduler;
import com.example.fuzzer.schedule.energy.EnergySchedulerFactory;
import com.example.fuzzer.schedule.model.Seed;
import com.example.fuzzer.schedule.sort.SeedSorter;
import com.example.fuzzer.schedule.sort.SeedSorterFactory;

import java.util.List;

/**
 * AFL风格的调度器，组合了种子排序器和能量调度器
 */
public class AFLScheduler implements SeedScheduler {
    private final SeedSorter seedSorter;
    private final EnergyScheduler energyScheduler;

    public AFLScheduler(List<Seed> initialSeeds) {
        this(initialSeeds, EnergyScheduler.Type.COVERAGE_BASED, SeedSorter.Type.HEURISTIC);
    }

    public AFLScheduler(List<Seed> initialSeeds, EnergyScheduler.Type energySchedulerType) {
        this(initialSeeds, energySchedulerType, SeedSorter.Type.HEURISTIC);
    }

    public AFLScheduler(List<Seed> initialSeeds, EnergyScheduler.Type energySchedulerType, SeedSorter.Type sorterType) {
        this.seedSorter = SeedSorterFactory.createSeedSorter(sorterType);
        this.energyScheduler = EnergySchedulerFactory.createEnergyScheduler(energySchedulerType);

        if (initialSeeds != null) {
            for (Seed seed : initialSeeds) {
                addSeed(seed);
            }
        }
    }

    public void addSeed(Seed seed) {
        if (seed == null || seed.getData() == null) {
            throw new IllegalArgumentException("Seed or seed data cannot be null");
        }
        byte[] data = seed.getData();
        seedSorter.addSeed(data);
        energyScheduler.assignEnergy(data);
    }

    @Override
    public Seed selectNextSeed() {
        byte[] data = seedSorter.getNextSeed();
        if (data == null) {
            return null;
        }

        // 如果当前种子的能量已耗尽，重置能量并更新性能指标
        if (!energyScheduler.hasEnergy(data)) {
            energyScheduler.resetEnergy(data);
        }

        energyScheduler.consumeEnergy(data);
        return new Seed(data);
    }

    public void updatePerformance(byte[] data, long executionTime, int newBranches) {
        seedSorter.updateSeedPerformance(data, executionTime, newBranches);
        energyScheduler.updateEnergy(data, executionTime, newBranches);
    }

    public int getQueueSize() {
        return seedSorter.size();
    }
}
