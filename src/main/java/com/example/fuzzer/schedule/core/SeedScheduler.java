package com.example.fuzzer.schedule.core;

import com.example.fuzzer.schedule.model.Seed;

/**
 * 种子调度器接口
 */
public interface SeedScheduler {
    /**
     * 选择下一个要执行的种子
     *
     * @return 下一个种子，如果没有可用种子则返回null
     */
    Seed selectNextSeed();

    /**
     * 添加新的种子到调度器
     *
     * @param seed 要添加的种子
     */
    void addSeed(Seed seed);

    /**
     * 更新种子的性能指标
     *
     * @param data          种子数据
     * @param executionTime 执行时间
     * @param newBranches   新发现的分支数
     */
    void updatePerformance(byte[] data, long executionTime, int newBranches);

    /**
     * 获取当前队列中种子的数量
     *
     * @return 队列中种子的数量
     */
    int getQueueSize();
}
