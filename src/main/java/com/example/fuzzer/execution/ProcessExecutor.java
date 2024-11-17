package com.example.fuzzer.execution;

import com.example.fuzzer.sharedmemory.SharedMemoryManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ProcessExecutor implements Executor {
    private String targetProgramPath;
    private SharedMemoryManager shmManager;

    public ProcessExecutor(String targetProgramPath, SharedMemoryManager shmManager) {
        this.targetProgramPath = targetProgramPath;
        this.shmManager = shmManager;
    }

    @Override
    public ExecutionResult execute(byte[] input) {
        // 将输入写入临时文件
        File inputFile = writeInputToFile(input);

        try {
            // 构造命令行，运行目标程序
            ProcessBuilder pb = new ProcessBuilder(targetProgramPath, inputFile.getAbsolutePath());
            Map<String, String> env = pb.environment();
            env.put("__AFL_SHM_ID", String.valueOf(shmManager.getShmId()));

            Process process = pb.start();

            int exitCode = 0;

            // 等待程序执行完毕或超时
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                // 超时处理
                process.destroyForcibly();
                //设置退出码为超时
                exitCode = 124;
            } else {
                exitCode = process.exitValue();
            }

            // 获取覆盖率数据
            byte[] coverageData = shmManager.readSharedMemory();

            // 构造执行结果
            ExecutionResult result = new ExecutionResult();
            result.setExitCode(exitCode);
            result.setCoverageData(coverageData);
            result.setInput(input);

            // 删除临时文件
            inputFile.delete();

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private File writeInputToFile(byte[] input) {
        try {
            File tempFile = File.createTempFile("fuzz_input_", ".tmp");
            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(input);
            fos.close();
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private byte[] getCoverageData() {
        // TODO: 实现获取覆盖率数据的逻辑
        return new byte[0];
    }
}
