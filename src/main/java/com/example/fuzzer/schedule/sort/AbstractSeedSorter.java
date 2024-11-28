package com.example.fuzzer.schedule.sort;

import com.example.fuzzer.schedule.model.SeedScore;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 种子排序器的抽象实现
 * 提供了通用的种子管理和排序功能
 */
public abstract class AbstractSeedSorter implements SeedSorter {
    // 种子淘汰相关的配置参数
    protected static final int MAX_CONSECUTIVE_LOW_PERFORMANCE = 5;  // 连续低性能次数阈值
    protected static final long SEED_TIMEOUT_MS = 1 * 60 * 1000;  // 种子超时时间（1分钟）
    protected static final int MAX_SEED_COUNT = 1000;  // 最大种子数量
    protected static final float MIN_SCORE_THRESHOLD = 0.1f;  // 最低分数阈值
    protected final Queue<SeedScore> queue;
    protected final Map<String, SeedScore> seedMap;
    protected final ReentrantLock lock;
    protected boolean hasStartedFuzzing = false;

    protected AbstractSeedSorter(Comparator<SeedScore> comparator) {
        this.queue = comparator != null ? new PriorityQueue<>(comparator) : new LinkedList<>();
        this.seedMap = new HashMap<>();
        this.lock = new ReentrantLock();
    }

    protected String getDataKey(byte[] data) {
        return Arrays.toString(data);
    }

    @Override
    public void addSeed(byte[] data) {
        if (data == null) return;

        String key = getDataKey(data);
        lock.lock();
        try {
            if (!seedMap.containsKey(key)) {
                SeedScore seed = new SeedScore(data);
                // 如果是初始化阶段（还没有开始模糊测试），标记为初始种子
                if (!hasStartedFuzzing) {
                    seed.markAsInitialSeed();
                }
                seedMap.put(key, seed);
                queue.offer(seed);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public byte[] getNextSeed() {
        lock.lock();
        try {
            if (queue.isEmpty()) {
                return null;
            }

            SeedScore seed = queue.poll();
            if (seed == null) {
                return null;
            }

            seed.incrementCycles();
            queue.offer(seed);

            return seed.getData();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateSeedPerformance(byte[] data, long executionTime, int newBranches) {
        if (data == null) return;

        String key = getDataKey(data);
        lock.lock();
        try {
            SeedScore seed = seedMap.get(key);
            if (seed != null) {
                // 更新种子的性能信息
                seed.setExecutionTime(executionTime);
                seed.setNewBranches(newBranches);

                // 更新分数
                float newScore = calculateScore(seed);
                seed.setScore(newScore);

                // 如果发现新分支，更新时间戳并重置低性能计数
                if (newBranches > 0) {
                    seed.updateLastNewBranchTime();
                    seed.resetLowPerformance();
                } else {
                    // 如果分数低于阈值，增加低性能计数
                    if (newScore < MIN_SCORE_THRESHOLD) {
                        seed.incrementLowPerformance();
                    } else {
                        seed.resetLowPerformance();
                    }
                }

                // 检查是否需要淘汰这个种子
                if (shouldDiscardSeed(seed)) {
                    removeSeed(key);
                    return;
                }

                // 如果使用优先队列，需要重新排序
                if (queue instanceof PriorityQueue) {
                    queue.remove(seed);
                    queue.offer(seed);
                }

                // 如果种子数量超过上限，移除最差的种子
                if (queue.size() > MAX_SEED_COUNT) {
                    removeWorstSeeds();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    protected boolean shouldDiscardSeed(SeedScore seed) {
        // 不淘汰初始种子
        if (seed.isInitialSeed()) {
            return false;
        }

        // 检查连续低性能次数
        if (seed.getConsecutiveLowPerformance() >= MAX_CONSECUTIVE_LOW_PERFORMANCE) {
            return true;
        }

        // 检查是否长时间没有发现新分支
        long timeSinceLastNewBranch = System.currentTimeMillis() - seed.getLastNewBranchTime();
        if (timeSinceLastNewBranch > SEED_TIMEOUT_MS) {
            return true;
        }

        return false;
    }

    protected void removeSeed(String key) {
        SeedScore seed = seedMap.remove(key);
        if (seed != null) {
            queue.remove(seed);
        }
    }

    protected void removeWorstSeeds() {
        // 移除分数最低的10%的种子，但保留初始种子
        int removeCount = queue.size() / 10;
        List<SeedScore> tempList = new ArrayList<>(queue);
        // 过滤掉初始种子
        tempList.removeIf(SeedScore::isInitialSeed);
        tempList.sort((a, b) -> Float.compare(a.getScore(), b.getScore()));

        for (int i = 0; i < removeCount && i < tempList.size(); i++) {
            SeedScore seed = tempList.get(i);
            String key = getDataKey(seed.getData());
            removeSeed(key);
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            queue.clear();
            seedMap.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 标记开始模糊测试
     */
    public void startFuzzing() {
        hasStartedFuzzing = true;
    }

    /**
     * 获取排序器类型
     * 每个具体的排序器实现都必须指定自己的类型
     *
     * @return 排序器类型
     */
    public abstract Type getType();

    protected float calculateScore(SeedScore seed) {
        // TODO: implement score calculation logic
        return 0;
    }
}