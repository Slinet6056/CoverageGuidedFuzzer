package com.example.fuzzer.schedule.sort;

public class SeedSorterFactory {
    public static SeedSorter createSorter(SeedSorter.Type type) {
        switch (type) {
            case FIFO:
                return new FIFOSeedSorter();
            case COVERAGE:
                return new CoverageSeedSorter();
            case EXECUTION_TIME:
                return new ExecutionTimeSeedSorter();
            case HEURISTIC:
                return new HeuristicSeedSorter();
            default:
                throw new UnsupportedOperationException("Unsupported SeedSorter type: " + type);
        }
    }
} 