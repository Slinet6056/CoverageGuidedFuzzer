package com.example.fuzzer.schedule.model;

/**
 * 表示一个模糊测试的输入种子
 */
public class Seed {
    private byte[] data;
    private int energy;

    public Seed(byte[] data) {
        this.data = data;
        this.energy = 10; // 默认能量值
    }

    public Seed(byte[] data, int energy) {
        this.data = data;
        this.energy = energy;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getEnergy() {
        return energy;
    }

    public void setEnergy(int energy) {
        this.energy = energy;
    }
}
