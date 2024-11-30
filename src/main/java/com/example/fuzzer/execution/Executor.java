package com.example.fuzzer.execution;

import java.io.IOException;

public interface Executor {
    ExecutionResult execute(byte[] input) throws IOException;

    ExecutionResult executeMultipleInputs(byte[][] inputs) throws IOException;
}
