package com.example.fuzzer.schedule;

public class Seed {
    private byte[] data;
    private int energy;

    public Seed(byte[] data) {
        this.data = data;
        this.energy = 10; // 默认能量值
    }

    // Getter 和 Setter 方法

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
