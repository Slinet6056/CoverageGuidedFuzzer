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
        return seedQueue.poll();
    }

    public void addSeed(Seed seed) {
        seedQueue.offer(seed);
    }
}
