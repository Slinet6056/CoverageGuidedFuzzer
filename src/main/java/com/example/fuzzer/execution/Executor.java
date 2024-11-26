package com.example.fuzzer.execution;

public interface Executor {
    ExecutionResult execute(byte[] input);
    ExecutionResult executeMultipleInputs(byte[][] inputs);
}
