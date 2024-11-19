package com.example.fuzzer.schedule.energy;

/**
 * 能量调度器接口
 */
public interface EnergyScheduler {
    /**
     * 为种子分配初始能量
     * @param data 种子数据
     */
    void assignEnergy(byte[] data);

    /**
     * 检查种子是否还有剩余能量
     * @param data 种子数据
     * @return 是否有剩余能量
     */
    boolean hasEnergy(byte[] data);

    /**
     * 消耗种子的能量
     * @param data 种子数据
     */
    void consumeEnergy(byte[] data);

    /**
     * 重置种子的能量
     * @param data 种子数据
     */
    void resetEnergy(byte[] data);

    /**
     * 根据执行结果更新种子的能量
     * @param data 种子数据
     * @param executionTime 执行时间
     * @param newBranches 新发现的分支数
     */
    void updateEnergy(byte[] data, int executionTime, int newBranches);
}
