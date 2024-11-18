package com.example.fuzzer;

import com.example.fuzzer.execution.ExecutionResult;
import com.example.fuzzer.execution.Executor;
import com.example.fuzzer.execution.ProcessExecutor;
import com.example.fuzzer.monitor.SimpleMonitor;
import com.example.fuzzer.mutation.Mutator;
import com.example.fuzzer.mutation.SimpleMutator;
import com.example.fuzzer.schedule.AFLSeedGenerator;
import com.example.fuzzer.schedule.Seed;
import com.example.fuzzer.schedule.SimpleSeedScheduler;
import com.example.fuzzer.sharedmemory.SharedMemoryManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class Fuzzer {
    private static int crashCount = 0;  // 添加崩溃计数器

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("用法: java Fuzzer <目标程序路径> <AFL种子目录>");
            System.exit(1);
        }

        String targetProgramPath = args[0];
        String aflSeedDir = args[1];

        // 初始化共享内存
        int mapSize = 65536;
        SharedMemoryManager shmManager = new SharedMemoryManager(mapSize);
        Executor executor = new ProcessExecutor(targetProgramPath, shmManager);
        SimpleMonitor monitor = new SimpleMonitor(mapSize);

        // 初始化AFL种子生成器
        AFLSeedGenerator seedGenerator = new AFLSeedGenerator(aflSeedDir);
        List<Seed> initialSeeds = seedGenerator.generateInitialSeeds();

        if (initialSeeds.isEmpty()) {
            System.out.println("警告：没有找到初始种子，使用默认种子");
            byte[] defaultSeed = new byte[]{0x41, 0x42, 0x43, 0x44};
            initialSeeds.add(new Seed(defaultSeed));
        }

        // 使用新的种子初始化调度器
        SimpleSeedScheduler seedScheduler = new SimpleSeedScheduler(initialSeeds);

        // 初始化变异器
        Mutator mutator = new SimpleMutator();

        // 添加初始状态信息
        System.out.println("初始化完成：");
        System.out.println("- 共享内存大小: " + mapSize + " bytes");
        System.out.println("- 目标程序路径: " + targetProgramPath);

        // 模糊测试循环
        int totalExecutions = 0;
        while (true) {
            Seed currentSeed = seedScheduler.selectNextSeed();
            if (currentSeed == null) {
                System.out.println("\n测试结束统计：");
                System.out.println("- 总执行次数: " + totalExecutions);
                System.out.println("- 发现的崩溃数: " + crashCount);
                System.out.println("种子池为空，测试结束。");
                break;
            }

            // 添加当前种子信息
            System.out.println("\n当前种子信息：");
            System.out.println("- 种子大小: " + currentSeed.getData().length + " bytes");
            System.out.println("- 剩余能量: " + currentSeed.getEnergy());

            for (int i = 0; i < currentSeed.getEnergy(); i++) {
                byte[] mutatedInput = mutator.mutate(currentSeed.getData());
                ExecutionResult result = executor.execute(mutatedInput);
                totalExecutions++;

                if (totalExecutions % 100 == 0) {  // 每100次执行打印一次状态
                    System.out.println("\n执行状态更新：");
                    System.out.println("- 已执行次数: " + totalExecutions);
                    System.out.println("- 当前种子池大小: " + seedScheduler.getQueueSize());
                }

                // 检查程序退出状态，处理崩溃等情况
                if (result.getExitCode() != 0) {
                    System.out.println("程序崩溃，退出码：" + result.getExitCode());
                    crashCount++;  // 增加崩溃计数
                    saveCrashInput(mutatedInput);
                    continue;
                }

                // 监控并记录结果
                monitor.recordResult(result);

                // 判断是否有新的覆盖
                if (monitor.hasNewCoverage(result.getCoverageData())) {
                    // 添加为新的种子
                    Seed newSeed = new Seed(mutatedInput);
                    seedScheduler.addSeed(newSeed);
                }
            }
        }

        // 结束后，销毁共享内存
        shmManager.destroySharedMemory();
    }

    // 保存崩溃输入的函数
    private static void saveCrashInput(byte[] input) {
        try {
            // 保存到文件，命名方式可以自定义
            String fileName = "crash_" + System.currentTimeMillis() + ".bin";
            FileOutputStream fos = new FileOutputStream(fileName);
            fos.write(input);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
