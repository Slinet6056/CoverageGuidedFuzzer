package com.example.fuzzer.schedule;

import java.util.LinkedList;
import java.util.Queue;

public class SimpleSeedScheduler implements SeedScheduler {
    private Queue<Seed> seedQueue = new LinkedList<>();

    public SimpleSeedScheduler(Seed initialSeed) {
        seedQueue.offer(initialSeed);
    }

    @Override
    public Seed selectNextSeed() {
        Seed seed = seedQueue.poll();
        if (seed != null) {
            if (seed.getEnergy() > 0) {
                seed.setEnergy(seed.getEnergy() - 1);
                seedQueue.offer(seed);
            }
        }
        return seed;
    }

    public void addSeed(Seed seed) {
        seedQueue.offer(seed);
    }

    public int getQueueSize() {
        return seedQueue.size();
    }
}
