package com.example.fuzzer.schedule.sort;

import com.example.fuzzer.schedule.model.SeedScore;

/**
 * AFL风格的启发式种子排序器实现
 * 综合考虑执行时间、新发现的分支数和能量值进行排序
 */
public class HeuristicSeedSorter extends AbstractSeedSorter {
    private int cycles;

    public HeuristicSeedSorter() {
        super((s1, s2) -> {
            if (s1 == null || s2 == null) return 0;
            float score1 = calculateScore(s1);
            float score2 = calculateScore(s2);
            return Float.compare(score2, score1);
        });
        this.cycles = 0;
    }

    private static float calculateScore(SeedScore seed) {
        if (seed.getExecutionTime() <= 0) return 0;

        // AFL启发式算法：
        // 1. 新分支数越多，分数越高
        // 2. 执行时间越短，分数越高
        // 3. 已经执行过多次的种子，分数会降低
        float baseScore = (float) seed.getNewBranches() / seed.getExecutionTime();
        float cyclesPenalty = (float) Math.pow(0.95, seed.getCycles());
        return baseScore * cyclesPenalty;
    }

    @Override
    public Type getType() {
        return Type.HEURISTIC;
    }
}