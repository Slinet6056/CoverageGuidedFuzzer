package com.example.fuzzer.schedule.model;

/**
 * 种子的评分信息
 */
public class SeedScore {
    private final byte[] data;
    private long executionTime;
    private int newBranches;
    private float score;
    private int cycles;  // 执行次数

    public SeedScore(byte[] data) {
        this.data = data;
        this.executionTime = 0L;
        this.newBranches = 0;
        this.score = 0;
        this.cycles = 0;
    }

    public byte[] getData() {
        return data;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }

    public int getNewBranches() {
        return newBranches;
    }

    public void setNewBranches(int newBranches) {
        this.newBranches = newBranches;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public int getCycles() {
        return cycles;
    }

    public void incrementCycles() {
        this.cycles++;
    }

    public void resetCycles() {
        this.cycles = 0;
    }
}
