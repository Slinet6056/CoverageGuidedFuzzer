package com.example.fuzzer.execution;

import java.io.IOException;

public class ExecutorException extends IOException {
    public ExecutorException(String message) {
        super(message);
    }

    public ExecutorException(String message, Throwable cause) {
        super(message, cause);
    }
}
