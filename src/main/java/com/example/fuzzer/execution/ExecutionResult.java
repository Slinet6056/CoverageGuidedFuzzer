package com.example.fuzzer.execution;

public class ExecutionResult {
    private int exitCode;
    private byte[] coverageData;
    private byte[] input;
    private long executionTime;
    private boolean timeout;
    private String errorMessage;

    public ExecutionResult() {
        this.executionTime = 0;
        this.timeout = false;
    }

    // Getter 和 Setter 方法

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public byte[] getCoverageData() {
        return coverageData;
    }

    public void setCoverageData(byte[] coverageData) {
        this.coverageData = coverageData;
    }

    public byte[] getInput() {
        return input;
    }

    public void setInput(byte[] input) {
        this.input = input;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }

    public boolean isTimeout() {
        return timeout;
    }

    public void setTimeout(boolean timeout) {
        this.timeout = timeout;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
