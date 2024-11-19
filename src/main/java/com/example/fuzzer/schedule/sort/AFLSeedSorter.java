package com.example.fuzzer.schedule.sort;

import com.example.fuzzer.schedule.model.SeedScore;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AFL风格的种子排序器实现
 */
public class AFLSeedSorter implements SeedSorter {
    private final PriorityQueue<SeedScore> queue;
    private final Map<String, SeedScore> seedMap;
    private final ReentrantLock lock;
    private int cycles;

    public AFLSeedSorter() {
        // 使用更安全的比较器
        this.queue = new PriorityQueue<>((s1, s2) -> {
            if (s1 == null || s2 == null) return 0;
            float score1 = s1.getScore();
            float score2 = s2.getScore();
            return Float.compare(score2, score1);
        });
        this.seedMap = new HashMap<>();
        this.lock = new ReentrantLock();
        this.cycles = 0;
    }

    private String getDataKey(byte[] data) {
        return Arrays.toString(data);
    }

    @Override
    public void addSeed(byte[] data) {
        if (data == null) return;
        
        String key = getDataKey(data);
        lock.lock();
        try {
            if (seedMap.containsKey(key)) {
                return;
            }

            SeedScore seedScore = new SeedScore(data);
            seedMap.put(key, seedScore);
            queue.offer(seedScore);
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

            queue.offer(seed); // 重新加入队列
            cycles++;
            
            return seed.getData();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateSeedPerformance(byte[] data, int executionTime, int newBranches) {
        if (data == null) return;

        String key = getDataKey(data);
        lock.lock();
        try {
            SeedScore seed = seedMap.get(key);
            if (seed == null) {
                return;
            }

            // 更新种子性能信息
            seed.setExecutionTime(executionTime);
            seed.setNewBranches(newBranches);
            
            // 计算新的分数
            float score = calculateScore(seed);
            seed.setScore(score);

            // 重新排序
            queue.remove(seed);
            queue.offer(seed);
        } finally {
            lock.unlock();
        }
    }

    private float calculateScore(SeedScore seed) {
        if (seed.getExecutionTime() <= 0) return 0;
        
        // AFL启发式评分：新分支数 / 执行时间
        return (float) seed.getNewBranches() / seed.getExecutionTime();
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return seedMap.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            seedMap.clear();
            queue.clear();
            cycles = 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current size of the seed queue
     * @return the number of seeds in the queue
     */
    public int getQueueSize() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }
}
