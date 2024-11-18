package com.example.fuzzer;

import com.example.fuzzer.execution.ExecutionResult;
import com.example.fuzzer.execution.Executor;
import com.example.fuzzer.execution.ExecutorConfig;
import com.example.fuzzer.execution.ProcessExecutor;
import com.example.fuzzer.monitor.SimpleMonitor;
import com.example.fuzzer.mutation.Mutator;
import com.example.fuzzer.mutation.MutatorFactory;
import com.example.fuzzer.schedule.AFLSeedGenerator;
import com.example.fuzzer.schedule.Seed;
import com.example.fuzzer.schedule.SimpleSeedScheduler;
import com.example.fuzzer.sharedmemory.SharedMemoryManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

// TODO:
// - 添加模糊测试统计信息
// - 实现暂停/恢复功能
// - 添加测试用例最小化
// - 实现并行化测试
// - 添加配置文件支持
// - 实现实时监控界面
public class Fuzzer {
    private static final String MUTATOR_TYPE = "afl";
    private static final int MAP_SIZE = 65536;

    private final String targetProgramPath;
    private final String aflSeedDir;
    private final SimpleMonitor monitor;
    private final SharedMemoryManager shmManager;
    private final Executor executor;
    private final SimpleSeedScheduler scheduler;
    private final Mutator mutator;
    private volatile boolean isRunning;
    private int crashCount;

    public Fuzzer(String targetProgramPath, String aflSeedDir) throws IOException {
        this.targetProgramPath = targetProgramPath;
        this.aflSeedDir = aflSeedDir;
        this.isRunning = true;
        this.crashCount = 0;

        // 初始化组件
        this.shmManager = new SharedMemoryManager(MAP_SIZE);
        this.monitor = new SimpleMonitor(MAP_SIZE);
        this.executor = createExecutor();
        this.scheduler = createScheduler();
        this.mutator = MutatorFactory.createMutator(MUTATOR_TYPE);

        setupShutdownHook();
    }

    private Executor createExecutor() {
        ExecutorConfig config = new ExecutorConfig.Builder()
                .timeout(5)
                .maxRetries(3)
                .redirectOutput(true)
                .outputDir("fuzz_output")
                .build();
        return new ProcessExecutor(targetProgramPath, shmManager, config);
    }

    private SimpleSeedScheduler createScheduler() throws IOException {
        AFLSeedGenerator seedGenerator = new AFLSeedGenerator(aflSeedDir);
        List<Seed> initialSeeds = seedGenerator.generateInitialSeeds();

        if (initialSeeds.isEmpty()) {
            System.out.println("警告：没有找到初始种子，使用默认种子");
            byte[] defaultSeed = new byte[]{0x41, 0x42, 0x43, 0x44};
            initialSeeds.add(new Seed(defaultSeed));
        }

        return new SimpleSeedScheduler(initialSeeds);
    }

    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            isRunning = false;
            System.out.println("\n接收到终止信号，正在退出...");
            monitor.printFinalStats();
            cleanup();
        }));
    }

    public void run() {
        printInitialInfo();

        int totalExecutions = 0;
        try {
            while (isRunning) {
                Seed currentSeed = scheduler.selectNextSeed();
                if (currentSeed == null) {
                    break;
                }

                totalExecutions += processSeed(currentSeed, totalExecutions);
            }
        } catch (Exception e) {
            System.err.println("模糊测试过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            printFinalStats(totalExecutions);
            cleanup();
        }
    }

    private int processSeed(Seed currentSeed, int totalExecutions) {
        int executions = 0;
        // printSeedInfo(currentSeed);

        for (int i = 0; i < currentSeed.getEnergy() && isRunning; i++) {
            byte[] mutatedInput = mutator.mutate(currentSeed.getData());
            ExecutionResult result = executor.execute(mutatedInput);
            executions++;

            if ((totalExecutions + executions) % 100 == 0) {
                // printProgress(totalExecutions + executions, scheduler.getQueueSize());
            }

            handleExecutionResult(result, mutatedInput);
        }

        return executions;
    }

    private void handleExecutionResult(ExecutionResult result, byte[] mutatedInput) {
        if (result.getExitCode() != 0) {
            crashCount++;
            handleCrash(result);
            return;
        }

        monitor.recordResult(result);

        if (monitor.hasNewCoverage(result.getCoverageData())) {
            Seed newSeed = new Seed(mutatedInput);
            newSeed.setEnergy(calculateNewSeedEnergy(result));
            scheduler.addSeed(newSeed);
            System.out.println("添加新种子，当前种子池大小: " + scheduler.getQueueSize());
        }
    }

    private void handleCrash(ExecutionResult result) {
        try {
            String crashFileName = String.format("crash-%d-exitcode-%d",
                    crashCount, result.getExitCode());
            Path crashPath = Paths.get("crashes", crashFileName);
            Files.createDirectories(crashPath.getParent());
            Files.write(crashPath, result.getInput());
        } catch (IOException e) {
            System.err.println("保存崩溃样例失败: " + e.getMessage());
        }
    }

    private void cleanup() {
        if (shmManager != null) {
            shmManager.destroySharedMemory();
        }
    }

    private int calculateNewSeedEnergy(ExecutionResult result) {
        // TODO: 实现更复杂的能量计算逻辑
        return 10;
    }

    private void printInitialInfo() {
        System.out.println("初始化完成：");
        System.out.println("- 共享内存大小: " + MAP_SIZE + " bytes");
        System.out.println("- 目标程序路径: " + targetProgramPath);
        System.out.println("- 使用变异器类型: " + MUTATOR_TYPE);
    }

    private static void printSeedInfo(Seed currentSeed) {
        System.out.println("\n当前种子信息：");
        System.out.println("- 种子大小: " + currentSeed.getData().length + " bytes");
        System.out.println("- 剩余能量: " + currentSeed.getEnergy());
    }

    private static void printProgress(int totalExecutions, int queueSize) {
        System.out.println("\n执行状态更新：");
        System.out.println("- 已执行次数: " + totalExecutions);
        System.out.println("- 当前种子池大小: " + queueSize);
    }

    private void printFinalStats(int totalExecutions) {
        System.out.println("\n测试结束统计：");
        System.out.println("- 总执行次数: " + totalExecutions);
        System.out.println("- 发现的崩溃数: " + crashCount);
        System.out.println("种子池为空，测试结束。");
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("用法: java Fuzzer <目标程序路径> <AFL种子目录>");
            System.exit(1);
        }

        try {
            Fuzzer fuzzer = new Fuzzer(args[0], args[1]);
            fuzzer.run();
        } catch (IOException e) {
            System.err.println("初始化Fuzzer失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}