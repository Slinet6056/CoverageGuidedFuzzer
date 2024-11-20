package com.example.fuzzer.schedule.sort;

import com.example.fuzzer.schedule.model.SeedScore;

import java.util.Comparator;

public class ExecutionTimeSeedSorter extends AbstractSeedSorter {
    public ExecutionTimeSeedSorter() {
        super(Comparator.comparingLong(SeedScore::getExecutionTime));
    }

    @Override
    public Type getType() {
        return Type.EXECUTION_TIME;
    }
} 