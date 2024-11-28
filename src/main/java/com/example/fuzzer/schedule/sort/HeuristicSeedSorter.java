package com.example.fuzzer.schedule.sort;

import com.example.fuzzer.schedule.model.SeedScore;

/**
 * AFL风格的启发式种子排序器实现
 * 综合考虑执行时间、新发现的分支数和能量值进行排序
 */
public class HeuristicSeedSorter extends AbstractSeedSorter {
    private int cycles;

    public HeuristicSeedSorter() {
        super((a, b) -> Float.compare(b.getScore(), a.getScore()));  // 分数高的优先
        this.cycles = 0;
    }

    @Override
    protected float calculateScore(SeedScore seed) {
        if (seed.getExecutionTime() <= 0) return 0;

        // AFL启发式算法：
        // 1. 新分支数越多，分数越高
        // 2. 执行时间越短，分数越高
        // 3. 已经执行过多次的种子，分数会降低
        // 4. 长时间没有发现新分支的种子，分数会降低
        float baseScore = (float) seed.getNewBranches() / seed.getExecutionTime();
        float cyclesPenalty = (float) Math.pow(0.95, seed.getCycles());

        // 根据距离上次发现新分支的时间添加衰减因子
        long timeSinceLastNewBranch = System.currentTimeMillis() - seed.getLastNewBranchTime();
        float timePenalty = (float) Math.pow(0.9, timeSinceLastNewBranch / (5 * 60 * 1000)); // 每5分钟衰减10%

        return baseScore * cyclesPenalty * timePenalty;
    }

    @Override
    public Type getType() {
        return Type.HEURISTIC;
    }
}