package com.example.fuzzer.mutation;

public class MutatorFactory {
    public static Mutator createMutator(Mutator.MutatorType type) {
        switch (type) {
            case AFL:
                return new AFLMutator();
            case RANDOM:
                return new SimpleMutator();
            default:
                throw new IllegalArgumentException("未知的变异器类型: " + type);
        }
    }
}