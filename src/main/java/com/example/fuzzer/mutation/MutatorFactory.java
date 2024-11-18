package com.example.fuzzer.mutation;

public class MutatorFactory {
    public static Mutator createMutator(String type) {
        switch (type.toLowerCase()) {
            case "afl":
                return new AFLMutator();
            case "simple":
                return new SimpleMutator();
            default:
                throw new IllegalArgumentException("未知的变异器类型: " + type);
        }
    }
} 