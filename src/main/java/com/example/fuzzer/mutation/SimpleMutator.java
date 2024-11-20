package com.example.fuzzer.mutation;

import java.util.Random;

public class SimpleMutator implements Mutator {
    private final Random random = new Random();
    private MutationStrategy currentStrategy = MutationStrategy.NONE;

    @Override
    public byte[] mutate(byte[] input) {
        currentStrategy = MutationStrategy.BITFLIP;
        byte[] mutated = input.clone();
        // 简单的随机位翻转
        if (mutated.length > 0) {
            int pos = random.nextInt(mutated.length);
            mutated[pos] = (byte) (mutated[pos] ^ (1 << random.nextInt(8)));
        }
        return mutated;
    }

    @Override
    public MutatorType getType() {
        return MutatorType.RANDOM;
    }

    @Override
    public MutationStrategy getCurrentStrategy() {
        return currentStrategy;
    }
}
