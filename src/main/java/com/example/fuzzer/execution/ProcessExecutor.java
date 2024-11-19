package com.example.fuzzer.execution;

import com.example.fuzzer.sharedmemory.SharedMemoryManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
                if (!inputFile.delete()) {
                    inputFile.deleteOnExit();  // Fallback if immediate deletion fails
                }
            }
            result.setExecutionTime(System.currentTimeMillis() - startTime);
        }

        return result;
    }

    private ExecutionResult executeProcess(File inputFile, byte[] input) throws IOException, InterruptedException {
        ExecutionResult result = new ExecutionResult();
        result.setInput(input);

        ProcessBuilder pb = new ProcessBuilder(targetProgramPath, inputFile.getAbsolutePath());
        Map<String, String> env = pb.environment();
        env.put("__AFL_SHM_ID", String.valueOf(shmManager.getShmId()));

        // 配置输出重定向
        if (config.isRedirectOutput()) {
            File outputDir = new File(config.getOutputDir());
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            // pb.redirectOutput(new File(outputDir, "stdout.txt"));
            // pb.redirectError(new File(outputDir, "stderr.txt"));
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
            tempFile.deleteOnExit();  // Register for deletion on JVM exit
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
                        file.deleteOnExit();
                    }
                }
            }
        }
    }
}
