package com.example.fuzzer.schedule.sort;

/**
 * 种子排序器接口
 */
public interface SeedSorter {
    /**
     * 添加新的种子
     *
     * @param data 种子数据
     */
    void addSeed(byte[] data);

    /**
     * 获取下一个种子
     *
     * @return 种子数据
     */
    byte[] getNextSeed();

    /**
     * 更新种子的性能信息
     *
     * @param data          种子数据
     * @param executionTime 执行时间
     * @param newBranches   新发现的分支数
     */
    void updateSeedPerformance(byte[] data, long executionTime, int newBranches);

    /**
     * 获取种子数量
     *
     * @return 种子数量
     */
    int size();

    /**
     * 清空种子
     */
    void clear();

    /**
     * 获取排序器类型
     *
     * @return 排序器类型
     */
    Type getType();

    /**
     * 添加新的枚举类型
     */
    enum Type {
        FIFO,           // 按入队顺序
        COVERAGE,       // 按覆盖率
        EXECUTION_TIME, // 按执行时间
        HEURISTIC      // 启发式排序
    }
}
