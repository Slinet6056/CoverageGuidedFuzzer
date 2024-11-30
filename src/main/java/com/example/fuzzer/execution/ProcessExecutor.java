package com.example.fuzzer.execution;

import com.example.fuzzer.logging.FuzzingLogger;
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
    private final FuzzingLogger logger = FuzzingLogger.getInstance();

    public ProcessExecutor(String targetProgramPath, SharedMemoryManager shmManager) {
        this(targetProgramPath, shmManager, new ExecutorConfig());
    }

    public ProcessExecutor(String targetProgramPath, SharedMemoryManager shmManager, ExecutorConfig config) {
        this.targetProgramPath = targetProgramPath;
        this.shmManager = shmManager;
        this.config = config;
    }

    @Override
    public ExecutionResult execute(byte[] input) throws IOException {
        return executeMultipleInputs(new byte[][]{input});
    }

    @Override
    public ExecutionResult executeMultipleInputs(byte[][] inputs) throws IOException {
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
                String msg = "Number of @@ arguments (" + inputFileCount +
                        ") doesn't match number of inputs (" + inputs.length + ")";
                logger.error(msg, null);
                throw new ExecutorException(msg);
            }

            // Create input files
            if (inputFileCount > 0) {
                // Create files for each input when we have @@ arguments
                for (int i = 0; i < inputs.length; i++) {
                    File inputFile = writeInputToFile(inputs[i]);
                    if (inputFile == null) {
                        String msg = "Failed to create input file " + (i + 1);
                        logger.error(msg, null);
                        throw new ExecutorException(msg);
                    }
                    inputFiles.add(inputFile);
                }
            } else if (inputs.length > 0) {
                // If no @@ but we have input, create one file for stdin
                File inputFile = writeInputToFile(inputs[0]);
                if (inputFile == null) {
                    String msg = "Failed to create input file";
                    logger.error(msg, null);
                    throw new ExecutorException(msg);
                }
                inputFiles.add(inputFile);
            }

            result = executeProcess(inputFiles, inputs);

        } catch (ExecutorException e) {
            // 执行器的错误，直接抛出
            throw e;
        } catch (Exception e) {
            // 其他未预期的错误，包装成ExecutorException
            logger.error("Unexpected error during execution", e);
            throw new ExecutorException("Unexpected error during execution", e);
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
                    OutputStream stdin = process.getOutputStream();
                    InputStream stdout = process.getInputStream();
                    InputStream stderr = process.getErrorStream();

                    // 创建输出流读取线程
                    Thread outputReader = new Thread(() -> {
                        byte[] bytes = new byte[1024];
                        try {
                            while (process.isAlive() && stdout.read(bytes) != -1) {
                                // 持续读取输出，防止管道阻塞
                            }
                        } catch (IOException e) {
                            // 忽略读取错误
                        }
                    });

                    Thread errorReader = new Thread(() -> {
                        byte[] bytes = new byte[1024];
                        try {
                            while (process.isAlive() && stderr.read(bytes) != -1) {
                                // 持续读取错误输出，防止管道阻塞
                            }
                        } catch (IOException e) {
                            // 忽略读取错误
                        }
                    });

                    // 启动读取线程
                    outputReader.start();
                    errorReader.start();

                    try {
                        // 写入输入数据
                        stdin.write(inputs[0]);
                        stdin.flush();

                        // 关闭输入流，表示没有更多输入
                        stdin.close();

                        // 等待读取线程结束，但设置超时
                        long timeout = config.getTimeoutSeconds() * 1000L;
                        long startWait = System.currentTimeMillis();
                        while (outputReader.isAlive() || errorReader.isAlive()) {
                            if (System.currentTimeMillis() - startWait > timeout) {
                                break;
                            }
                            Thread.sleep(10);
                        }
                    } catch (IOException | InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        // 确保线程停止
                        outputReader.interrupt();
                        errorReader.interrupt();
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
                        logger.error("Failed to read coverage data", null);
                        throw new ExecutorException("Failed to read coverage data");
                    }

                    result.setCoverageData(coverageData);
                    return result;

                } catch (ExecutionException e) {
                    logger.error("Execution exception", e);
                    throw new ExecutorException("Execution failed", e);
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
            logger.error("Maximum retry count reached", null);
            throw new ExecutorException("Maximum retry count reached");
        }

        return result;
    }

    private void handleTimeout(Process process, ExecutionResult result) throws IOException {
        String msg = "Execution timeout (" + config.getTimeoutSeconds() + " seconds)";
        logger.error(msg, null);
        result.setTimeout(true);
        result.setExitCode(124);
        result.setErrorMessage(msg);

        // Set execution time to timeout duration
        result.setExecutionTime(config.getTimeoutSeconds() * 1000L);
    }

    private void handleProcessOutput(Process process, ExecutionResult result) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String errorOutput = reader.readLine();
            if (errorOutput != null) {
                result.setErrorMessage(errorOutput);
            }
        } catch (IOException e) {
            logger.error("Failed to read process error stream", e);
            throw new ExecutorException("Failed to read process error stream", e);
        }
    }

    private void cleanupProcess(Process process) throws IOException {
        if (process != null && process.isAlive()) {
            try {
                process.destroy();
                // 给进程一点时间来正常终止
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    // 再次等待，确保进程被终止
                    if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                        String msg = "Process could not be terminated (PID: " + process.pid() + ")";
                        logger.error(msg, null);
                        throw new ExecutorException(msg);
                    }
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                logger.error("Process cleanup interrupted", e);
                Thread.currentThread().interrupt();
                throw new ExecutorException("Process cleanup interrupted", e);
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
