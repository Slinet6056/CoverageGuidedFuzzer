package com.example.fuzzer.schedule.sort;

public class CoverageSeedSorter extends AbstractSeedSorter {
    public CoverageSeedSorter() {
        super((s1, s2) -> Integer.compare(s2.getNewBranches(), s1.getNewBranches()));
    }

    @Override
    public Type getType() {
        return Type.COVERAGE;
    }
} 