package com.example.fuzzer.monitor;

import com.example.fuzzer.execution.ExecutionResult;

public interface Monitor {
    void recordResult(ExecutionResult result);
}
