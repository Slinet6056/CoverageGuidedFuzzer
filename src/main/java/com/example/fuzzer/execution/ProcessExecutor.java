package com.example.fuzzer.execution;

import com.example.fuzzer.sharedmemory.SharedMemoryManager;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class ProcessExecutor implements Executor {
    private final String targetProgramPath;
    private final SharedMemoryManager shmManager;
    private final ExecutorConfig config;

    public ProcessExecutor(String targetProgramPath, SharedMemoryManager shmManager) {
        this(targetProgramPath, shmManager, new ExecutorConfig());
    }

    public ProcessExecutor(String targetProgramPath, SharedMemoryManager shmManager, ExecutorConfig config) {
        this.targetProgramPath = targetProgramPath;
        this.shmManager = shmManager;
        this.config = config;
    }

    @Override
    public ExecutionResult execute(byte[] input) {
        return executeMultipleInputs(new byte[][]{input});
    }

    @Override
    public ExecutionResult executeMultipleInputs(byte[][] inputs) {
        ExecutionResult result = new ExecutionResult();
        List<File> inputFiles = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        try {
            // Count how many @@ we need
            int inputFileCount = 0;
            for (String arg : config.getCommandArgs()) {
                if (arg.equals("@@")) {
                    inputFileCount++;
                }
            }

            // Validate input count
            if (inputFileCount > 0 && inputFileCount != inputs.length) {
                throw new IOException("Number of @@ arguments (" + inputFileCount +
                        ") doesn't match number of inputs (" + inputs.length + ")");
            }

            // Create input files
            if (inputFileCount > 0) {
                // Create files for each input when we have @@ arguments
                for (int i = 0; i < inputs.length; i++) {
                    File inputFile = writeInputToFile(inputs[i]);
                    if (inputFile == null) {
                        throw new IOException("Failed to create input file " + (i + 1));
                    }
                    inputFiles.add(inputFile);
                }
            } else if (inputs.length > 0) {
                // If no @@ but we have input, create one file for stdin
                File inputFile = writeInputToFile(inputs[0]);
                if (inputFile == null) {
                    throw new IOException("Failed to create input file");
                }
                inputFiles.add(inputFile);
            }

            result = executeProcess(inputFiles, inputs);

        } catch (Exception e) {
            result.setErrorMessage(e.getMessage());
            result.setExitCode(-1);
        } finally {
            if (config.isDeleteInputFile()) {
                // Clean up all created input files
                for (File file : inputFiles) {
                    file.delete();
                }
            }
            result.setExecutionTime(System.currentTimeMillis() - startTime);
        }

        return result;
    }

    private ExecutionResult executeProcess(List<File> inputFiles, byte[][] inputs) throws IOException, InterruptedException {
        ExecutionResult result = new ExecutionResult();
        // 使用ByteBuffer优化内存使用
        int totalLength = 0;
        for (byte[] input : inputs) {
            totalLength += input.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        for (byte[] input : inputs) {
            buffer.put(input);
        }
        result.setInput(buffer.array());

        List<String> command = new ArrayList<>();
        command.add(targetProgramPath);

        boolean hasInputFileArg = false;
        int fileIndex = 0;
        for (String arg : config.getCommandArgs()) {
            if (arg.equals("@@")) {
                if (fileIndex < inputFiles.size()) {
                    command.add(inputFiles.get(fileIndex).getAbsolutePath());
                    fileIndex++;
                    hasInputFileArg = true;
                }
            } else {
                command.add(arg);
            }
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        Map<String, String> env = pb.environment();
        env.put("__AFL_SHM_ID", String.valueOf(shmManager.getShmId()));

        if (config.isRedirectOutput()) {
            File outputDir = new File(config.getOutputDir());
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
        }

        int retryCount = 0;
        while (retryCount <= config.getMaxRetries()) {
            Process process = pb.start();
            Future<Boolean> timeoutFuture = null;
            ExecutorService timeoutExecutor = null;
            long startTime = System.currentTimeMillis();

            try {
                // 如果没有通过命令行参数指定输入文件，则通过标准输入传入第一个输入
                if (!hasInputFileArg && inputs.length > 0) {
                    try (OutputStream stdin = process.getOutputStream()) {
                        stdin.write(inputs[0]);
                        stdin.flush();
                    }
                }

                // 创建异步超时检查
                timeoutExecutor = Executors.newSingleThreadExecutor();
                timeoutFuture = timeoutExecutor.submit(() -> {
                    try {
                        return process.waitFor(config.getTimeoutSeconds(), TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        return false;
                    }
                });

                try {
                    boolean finished = timeoutFuture.get();
                    long endTime = System.currentTimeMillis();
                    result.setExecutionTime(endTime - startTime);

                    if (!finished) {
                        handleTimeout(process, result);
                        return result; // Return immediately after timeout
                    }

                    result.setExitCode(process.exitValue());
                    handleProcessOutput(process, result);

                    // 获取覆盖率数据
                    byte[] coverageData = shmManager.readSharedMemory();
                    if (coverageData == null) {
                        result.setErrorMessage("无法读取覆盖率数据");
                        retryCount++;
                        continue;
                    }

                    result.setCoverageData(coverageData);
                    return result;

                } catch (ExecutionException e) {
                    result.setErrorMessage("执行异常: " + e.getMessage());
                    result.setExitCode(-1);
                    break;
                }

            } finally {
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(true);
                }
                if (timeoutExecutor != null) {
                    timeoutExecutor.shutdownNow();
                }
                cleanupProcess(process);
            }
        }

        if (retryCount > config.getMaxRetries()) {
            result.setErrorMessage("达到最大重试次数");
        }

        return result;
    }

    private void handleTimeout(Process process, ExecutionResult result) {
        result.setTimeout(true);
        result.setExitCode(124);
        result.setErrorMessage("执行超时（" + config.getTimeoutSeconds() + "秒）");

        // Set execution time to timeout duration
        result.setExecutionTime(config.getTimeoutSeconds() * 1000L);
    }

    private void handleProcessOutput(Process process, ExecutionResult result) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String errorOutput = reader.readLine();
            if (errorOutput != null) {
                result.setErrorMessage(errorOutput);
            }
        } catch (IOException e) {
            // 忽略读取错误
        }
    }

    private void cleanupProcess(Process process) {
        if (process != null && process.isAlive()) {
            try {
                process.destroy();
                // 给进程一点时间来正常终止
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    // 再次等待，确保进程被终止
                    if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                        System.err.println("警告：进程无法被终止 (PID: " + process.pid() + ")");
                    }
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt(); // 保留中断状态
            }
        }
    }

    private File writeInputToFile(byte[] input) {
        File tempFile = null;
        FileOutputStream fos = null;

        try {
            tempFile = File.createTempFile(
                    config.getTempFilePrefix(),
                    config.getTempFileSuffix(),
                    new File(config.getOutputDir())  // Store temp files in output directory for better management
            );
            fos = new FileOutputStream(tempFile);
            fos.write(input);
            return tempFile;
        } catch (IOException e) {
            if (tempFile != null) {
                tempFile.delete();
            }
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    // Add new method for cleaning up stray files
    public void cleanupStrayFiles() {
        File outputDir = new File(config.getOutputDir());
        if (outputDir.exists() && outputDir.isDirectory()) {
            File[] strayFiles = outputDir.listFiles((dir, name) ->
                    name.startsWith(config.getTempFilePrefix()) && name.endsWith(config.getTempFileSuffix()));

            if (strayFiles != null) {
                for (File file : strayFiles) {
                    if (!file.delete()) {
                        // Silently continue if deletion fails
                    }
                }
            }
        }
    }
}
