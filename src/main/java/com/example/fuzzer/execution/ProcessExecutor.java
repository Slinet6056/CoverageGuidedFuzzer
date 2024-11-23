package com.example.fuzzer.execution;

import com.example.fuzzer.sharedmemory.SharedMemoryManager;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
        ExecutionResult result = new ExecutionResult();
        File inputFile = null;
        long startTime = System.currentTimeMillis();

        try {
            inputFile = writeInputToFile(input);
            if (inputFile == null) {
                throw new IOException("Failed to create input file");
            }

            result = executeProcess(inputFile, input);

        } catch (Exception e) {
            result.setErrorMessage(e.getMessage());
            result.setExitCode(-1);
        } finally {
            if (inputFile != null && config.isDeleteInputFile()) {
                inputFile.delete();  // Just try to delete, ignore if it fails
            }
            result.setExecutionTime(System.currentTimeMillis() - startTime);
        }

        return result;
    }

    private ExecutionResult executeProcess(File inputFile, byte[] input) throws IOException, InterruptedException {
        ExecutionResult result = new ExecutionResult();
        result.setInput(input);

        // 构建命令行参数列表
        List<String> command = new ArrayList<>();
        command.add(targetProgramPath);

        // 添加用户配置的命令行参数，替换@@为输入文件路径
        for (String arg : config.getCommandArgs()) {
            if (arg.equals("@@")) {
                command.add(inputFile.getAbsolutePath());
            } else {
                command.add(arg);
            }
        }

        // 如果没有配置任何参数或没有@@占位符，则默认将输入文件作为最后一个参数
        if (config.getCommandArgs().length == 0 || !Arrays.asList(config.getCommandArgs()).contains("@@")) {
            command.add(inputFile.getAbsolutePath());
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        Map<String, String> env = pb.environment();
        env.put("__AFL_SHM_ID", String.valueOf(shmManager.getShmId()));

        // 配置输出重定向
        if (config.isRedirectOutput()) {
            File outputDir = new File(config.getOutputDir());
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
        }

        int retryCount = 0;
        while (retryCount <= config.getMaxRetries()) {
            Process process = pb.start();
            boolean finished = process.waitFor(config.getTimeoutSeconds(), TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                result.setTimeout(true);
                result.setExitCode(124);
                result.setErrorMessage("执行超时");
                retryCount++;
                continue;
            }

            result.setExitCode(process.exitValue());

            if (result.getExitCode() != 0) {
                // 获取程序的错误输出
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

            // 获取覆盖率数据
            byte[] coverageData = shmManager.readSharedMemory();
            if (coverageData == null) {
                result.setErrorMessage("无法读取覆盖率数据");
                retryCount++;
                continue;
            }

            result.setCoverageData(coverageData);
            return result;
        }

        if (retryCount > config.getMaxRetries()) {
            result.setErrorMessage("达到最大重试次数");
        }

        return result;
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
