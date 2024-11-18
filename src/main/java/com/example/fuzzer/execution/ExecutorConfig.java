package com.example.fuzzer.execution;

public class ExecutorConfig {
    private int timeoutSeconds = 5;
    private boolean deleteInputFile = true;
    private int maxRetries = 3;
    private String tempFilePrefix = "fuzz_input_";
    private String tempFileSuffix = ".tmp";
    private boolean redirectOutput = false;
    private String outputDir = "output";

    // getter 和 setter 方法
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("超时时间必须大于0");
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isDeleteInputFile() {
        return deleteInputFile;
    }

    public void setDeleteInputFile(boolean deleteInputFile) {
        this.deleteInputFile = deleteInputFile;
    }

    public String getTempFilePrefix() {
        return tempFilePrefix;
    }

    public void setTempFilePrefix(String tempFilePrefix) {
        if (tempFilePrefix == null || tempFilePrefix.isEmpty()) {
            throw new IllegalArgumentException("临时文件前缀不能为空");
        }
        this.tempFilePrefix = tempFilePrefix;
    }

    public String getTempFileSuffix() {
        return tempFileSuffix;
    }

    public void setTempFileSuffix(String tempFileSuffix) {
        this.tempFileSuffix = tempFileSuffix;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("最大重试次数不能小于0");
        }
        this.maxRetries = maxRetries;
    }

    public boolean isRedirectOutput() {
        return redirectOutput;
    }

    public void setRedirectOutput(boolean redirectOutput) {
        this.redirectOutput = redirectOutput;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        if (outputDir == null || outputDir.isEmpty()) {
            throw new IllegalArgumentException("输出目录不能为空");
        }
        this.outputDir = outputDir;
    }

    // Builder 模式
    public static class Builder {
        private ExecutorConfig config = new ExecutorConfig();

        public Builder timeout(int seconds) {
            config.setTimeoutSeconds(seconds);
            return this;
        }

        public Builder deleteInputFile(boolean delete) {
            config.setDeleteInputFile(delete);
            return this;
        }

        public Builder tempFilePrefix(String prefix) {
            config.setTempFilePrefix(prefix);
            return this;
        }

        public Builder tempFileSuffix(String suffix) {
            config.setTempFileSuffix(suffix);
            return this;
        }

        public Builder maxRetries(int retries) {
            config.setMaxRetries(retries);
            return this;
        }

        public Builder redirectOutput(boolean redirect) {
            config.setRedirectOutput(redirect);
            return this;
        }

        public Builder outputDir(String dir) {
            config.setOutputDir(dir);
            return this;
        }

        public ExecutorConfig build() {
            return config;
        }
    }
} 