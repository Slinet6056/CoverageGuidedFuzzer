package com.example.fuzzer.schedule.model;

/**
 * 种子的评分信息
 */
public class SeedScore {
    private final byte[] data;
    private int executionTime;
    private int newBranches;
    private float score;

    public SeedScore(byte[] data) {
        this.data = data;
        this.executionTime = 0;
        this.newBranches = 0;
        this.score = 0;
    }

    public byte[] getData() {
        return data;
    }

    public int getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(int executionTime) {
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
}
