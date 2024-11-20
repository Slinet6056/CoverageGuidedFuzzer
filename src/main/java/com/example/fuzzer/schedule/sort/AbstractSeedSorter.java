package com.example.fuzzer.schedule.sort;

import com.example.fuzzer.schedule.model.SeedScore;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 种子排序器的抽象实现
 * 提供了通用的种子管理和排序功能
 */
public abstract class AbstractSeedSorter implements SeedSorter {
    protected final Queue<SeedScore> queue;
    protected final Map<String, SeedScore> seedMap;
    protected final ReentrantLock lock;

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
                SeedScore seedScore = new SeedScore(data);
                seedMap.put(key, seedScore);
                queue.offer(seedScore);
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

                // 如果使用优先队列，需要重新排序
                if (queue instanceof PriorityQueue) {
                    queue.remove(seed);
                    queue.offer(seed);
                }
            }
        } finally {
            lock.unlock();
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
     * 获取排序器类型
     * 每个具体的排序器实现都必须指定自己的类型
     *
     * @return 排序器类型
     */
    public abstract Type getType();
}