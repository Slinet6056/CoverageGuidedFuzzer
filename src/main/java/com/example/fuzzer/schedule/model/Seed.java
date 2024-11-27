package com.example.fuzzer.schedule.model;

/**
 * 表示一个模糊测试的输入种子
 */
public class Seed {
    private final byte[] data;
    private int energy;

    public Seed(byte[] data) {
        this.data = data.clone();
        this.energy = 1;
    }

    public Seed(byte[] data, int energy) {
        this.data = data.clone();
        this.energy = energy;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        // data 是 final 的，不能被修改
        // this.data = data;
    }

    public int getEnergy() {
        return energy;
    }

    public void setEnergy(int energy) {
        this.energy = energy;
    }
}
