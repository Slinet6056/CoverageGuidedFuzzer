package com.example.fuzzer.schedule.sort;

public class FIFOSeedSorter extends AbstractSeedSorter {
    public FIFOSeedSorter() {
        super(null); // 使用 LinkedList 实现 FIFO
    }

    @Override
    public Type getType() {
        return Type.FIFO;
    }
} 