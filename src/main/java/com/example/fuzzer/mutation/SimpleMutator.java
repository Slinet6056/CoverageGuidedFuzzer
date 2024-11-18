package com.example.fuzzer.mutation;

import java.util.Random;

public class SimpleMutator implements Mutator {
    private Random random = new Random();

    @Override
    public byte[] mutate(byte[] input) {
        byte[] mutatedInput = input.clone();
        int index = random.nextInt(mutatedInput.length);
        mutatedInput[index] = (byte) random.nextInt(256);
        return mutatedInput;
    }

    @Override
    public String getMutationStrategy() {
        return "simple_random";
    }
}
