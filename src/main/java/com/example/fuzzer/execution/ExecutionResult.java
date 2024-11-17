package com.example.fuzzer.execution;

public class ExecutionResult {
    private int exitCode;
    private byte[] coverageData;
    private byte[] input;

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
}
