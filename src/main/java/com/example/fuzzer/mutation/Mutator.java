package com.example.fuzzer.mutation;

public interface Mutator {
    byte[] mutate(byte[] input);

    String getMutationStrategy();

    default void setMutationPower(int power) {
    }
}