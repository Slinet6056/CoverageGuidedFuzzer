package com.example.fuzzer.mutation;

public interface Mutator {
    byte[] mutate(byte[] input);

    MutatorType getType();

    MutationStrategy getCurrentStrategy();

    default void setMutationPower(int power) {
    }

    enum MutatorType {
        AFL,    // AFL风格的变异器
        RANDOM  // 随机变异器
    }

    enum MutationStrategy {
        BITFLIP,    // 位翻转
        ARITHMETIC, // 算术运算
        INTERESTING, // 插入预定义的有趣值
        HAVOC,      // 混沌变异
        SPLICE,     // 拼接变异
        NONE        // 无策略
    }
}