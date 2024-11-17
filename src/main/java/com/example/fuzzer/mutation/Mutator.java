package com.example.fuzzer.mutation;

public interface Mutator {
    byte[] mutate(byte[] input);
}