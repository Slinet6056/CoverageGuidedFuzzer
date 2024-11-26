package com.example.fuzzer;

import com.example.fuzzer.execution.ExecutionResult;
import com.example.fuzzer.execution.Executor;
import com.example.fuzzer.execution.ExecutorConfig;
import com.example.fuzzer.execution.ProcessExecutor;
import com.example.fuzzer.monitor.AFLMonitor;
import com.example.fuzzer.mutation.Mutator;
import com.example.fuzzer.mutation.MutatorFactory;
import com.example.fuzzer.schedule.AFLSeedGenerator;
import com.example.fuzzer.schedule.core.AFLScheduler;
import com.example.fuzzer.schedule.core.SeedScheduler;
import com.example.fuzzer.schedule.energy.EnergyScheduler;
import com.example.fuzzer.schedule.model.Seed;
import com.example.fuzzer.schedule.sort.SeedSorter;
import com.example.fuzzer.schedule.sort.SeedSorterFactory;
import com.example.fuzzer.sharedmemory.SharedMemoryManager;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// TODO:
// - 添加模糊测试统计信息
// - 实现暂停/恢复功能
// - 添加测试用例最小化
// - 实现并行化测试
// - 添加配置文件支持
// - 实现实时监控界面
public class Fuzzer {
    private static final Mutator.MutatorType DEFAULT_MUTATOR_TYPE = Mutator.MutatorType.AFL;
    private static final EnergyScheduler.Type DEFAULT_ENERGY_SCHEDULER_TYPE = EnergyScheduler.Type.COVERAGE_BASED;
    private static final SeedSorter.Type DEFAULT_SEED_SORTER_TYPE = SeedSorter.Type.HEURISTIC;
    private static final int MAP_SIZE = 65536;

    private final String targetProgramPath;
    private final String aflSeedDir;
    private final Mutator.MutatorType mutatorType;
    private final EnergyScheduler.Type energySchedulerType;
    private final SeedSorter.Type seedSorterType;
    private final AFLMonitor monitor;
    private final SharedMemoryManager shmManager;
    private final Executor executor;
    private final SeedScheduler scheduler;
    private final SeedSorter seedSorter;
    private final Mutator mutator;
    private final int numThreads;
    private final AtomicLong totalExecutions;
    private final AtomicInteger crashCount;
    private ExecutorService executorService;
    private List<Executor> executors = new ArrayList<>();
    private volatile boolean isRunning;
    private volatile long endTimeMillis;  // 结束时间（毫秒）
    private String[] programArgs = new String[0];
    private String outputDir;
    private int timeout = 1;

    public Fuzzer(String targetProgramPath, String aflSeedDir) throws IOException {
        this(targetProgramPath, aflSeedDir, DEFAULT_MUTATOR_TYPE, DEFAULT_ENERGY_SCHEDULER_TYPE, DEFAULT_SEED_SORTER_TYPE);
    }

    public Fuzzer(String targetProgramPath, String aflSeedDir, Mutator.MutatorType mutatorType,
                  EnergyScheduler.Type energySchedulerType, SeedSorter.Type seedSorterType) throws IOException {
        this(targetProgramPath, aflSeedDir, mutatorType, energySchedulerType, seedSorterType, Runtime.getRuntime().availableProcessors());
    }

    public Fuzzer(String targetProgramPath, String aflSeedDir, Mutator.MutatorType mutatorType,
                  EnergyScheduler.Type energySchedulerType, SeedSorter.Type seedSorterType, int numThreads) throws IOException {
        this.targetProgramPath = targetProgramPath;
        this.aflSeedDir = aflSeedDir;
        this.mutatorType = mutatorType;
        this.energySchedulerType = energySchedulerType;
        this.seedSorterType = seedSorterType;
        this.numThreads = numThreads;
        this.isRunning = true;
        initializeExecutors();
        this.executors = new ArrayList<>();
        this.totalExecutions = new AtomicLong(0);
        this.crashCount = new AtomicInteger(0);

        // 初始化输出目录
        String outputPath = "fuzz_output/" + System.currentTimeMillis();
        this.outputDir = outputPath;

        // 初始化监控器
        this.monitor = new AFLMonitor(MAP_SIZE, outputPath);
        this.monitor.setTargetInfo(targetProgramPath, programArgs);

        // 初始化共享内存管理器
        this.shmManager = new SharedMemoryManager(MAP_SIZE);

        // 初始化执行器
        ExecutorConfig config = new ExecutorConfig.Builder()
                .timeout(timeout)
                .build();
        this.executor = new ProcessExecutor(targetProgramPath, shmManager, config);

        // 初始化种子排序器
        this.seedSorter = SeedSorterFactory.createSeedSorter(seedSorterType);

        // 初始化变异器
        this.mutator = MutatorFactory.createMutator(mutatorType);

        // 初始化调度器
        List<Seed> initialSeeds = new ArrayList<>();  // 初始为空，稍后通过loadSeeds添加
        this.scheduler = new AFLScheduler(initialSeeds, energySchedulerType);

        // 加载种子
        loadSeeds();

        // 设置默认运行时间（无限）
        this.endTimeMillis = Long.MAX_VALUE;

        // 设置关闭钩子
        setupShutdownHook();
    }

    public static void main(String[] args) {
        Options options = new Options();

        // 添加命令行选项
        options.addOption(Option.builder("p")
                .longOpt("program")
                .desc("目标程序路径")
                .hasArg()
                .required()
                .build());

        options.addOption(Option.builder("s")
                .longOpt("seed-dir")
                .desc("AFL种子目录")
                .hasArg()
                .required()
                .build());

        options.addOption(Option.builder("t")
                .longOpt("time")
                .desc("运行时长(分钟)")
                .hasArg()
                .type(Number.class)
                .build());

        options.addOption(Option.builder("to")
                .longOpt("timeout")
                .desc("单个测试用例的超时时间(秒)，默认为1秒")
                .hasArg()
                .type(Number.class)
                .build());

        options.addOption(Option.builder("m")
                .longOpt("mutator")
                .desc("变异策略 (AFL, RANDOM)")
                .hasArg()
                .build());

        options.addOption(Option.builder("e")
                .longOpt("energy")
                .desc("能量调度策略 (BASIC, COVERAGE_BASED)")
                .hasArg()
                .build());

        options.addOption(Option.builder("ss")
                .longOpt("seed-sort")
                .desc("种子排序策略 (FIFO, COVERAGE, EXECUTION_TIME, HEURISTIC)")
                .hasArg()
                .build());

        options.addOption(Option.builder("j")
                .longOpt("threads")
                .desc("线程数量")
                .hasArg()
                .type(Number.class)
                .build());

        options.addOption(Option.builder("c")
                .longOpt("target-cmdline")
                .desc("目标程序的完整命令行，使用@@作为输入文件占位符。例如：'-a @@' 或 '-d @@'")
                .hasArg()
                .build());

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            CommandLine cmd = parser.parse(options, args);

            String targetProgram = cmd.getOptionValue("program");
            String seedDir = cmd.getOptionValue("seed-dir");

            // 解析可选参数
            Mutator.MutatorType mutatorType = cmd.hasOption("mutator")
                    ? Mutator.MutatorType.valueOf(cmd.getOptionValue("mutator").toUpperCase())
                    : DEFAULT_MUTATOR_TYPE;

            EnergyScheduler.Type energyType = cmd.hasOption("energy")
                    ? EnergyScheduler.Type.valueOf(cmd.getOptionValue("energy").toUpperCase())
                    : DEFAULT_ENERGY_SCHEDULER_TYPE;

            SeedSorter.Type sorterType = cmd.hasOption("seed-sort")
                    ? SeedSorter.Type.valueOf(cmd.getOptionValue("seed-sort").toUpperCase())
                    : DEFAULT_SEED_SORTER_TYPE;

            int threads = cmd.hasOption("threads")
                    ? ((Number) cmd.getParsedOptionValue("threads")).intValue()
                    : Runtime.getRuntime().availableProcessors();

            String targetCmdline = cmd.getOptionValue("target-cmdline", "");
            String[] programArgs = targetCmdline.split("\\s+");  // 按空格分割命令行

            Fuzzer fuzzer = new Fuzzer(targetProgram, seedDir, mutatorType, energyType, sorterType, threads);
            fuzzer.setProgramArgs(programArgs);

            if (cmd.hasOption("time")) {
                int minutes = ((Number) cmd.getParsedOptionValue("time")).intValue();
                fuzzer.setDurationMinutes(minutes);
            }

            // 设置超时时间
            if (cmd.hasOption("timeout")) {
                int timeout = ((Number) cmd.getParsedOptionValue("timeout")).intValue();
                fuzzer.setTimeout(timeout);
            }

            fuzzer.run();

        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("Fuzzer", options);
            System.exit(1);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void initializeExecutors() {
        // 使用有界线程池，避免创建过多线程
        this.executorService = new ThreadPoolExecutor(
                numThreads, // 核心线程数
                numThreads, // 最大线程数
                60L, TimeUnit.SECONDS, // 空闲线程存活时间
                new LinkedBlockingQueue<>(1000), // 使用有界队列
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时，在调用者线程中执行
        );
    }

    /**
     * 设置模糊测试运行时长
     *
     * @param minutes 运行时长（分钟）
     */
    public void setDurationMinutes(long minutes) {
        if (minutes > 0) {
            this.endTimeMillis = System.currentTimeMillis() + (minutes * 60 * 1000);
        }
    }

    public void setProgramArgs(String[] args) throws IOException {
        this.programArgs = args != null ? args : new String[0];
        // 写入命令行到文件
        String[] fullArgs = new String[programArgs.length + 1];
        fullArgs[0] = targetProgramPath;
        System.arraycopy(programArgs, 0, fullArgs, 1, programArgs.length);
        monitor.getOutputManager().writeCmdline(fullArgs);
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    private Executor createExecutor() {
        ExecutorConfig config = new ExecutorConfig.Builder()
                .timeout(timeout)
                .maxRetries(3)
                .redirectOutput(true)
                .outputDir(outputDir)
                .commandArgs(programArgs)
                .multipleInputs(hasMultipleInputs())
                .build();
        return new ProcessExecutor(targetProgramPath, shmManager, config);
    }

    private boolean hasMultipleInputs() {
        if (programArgs == null) return false;
        int count = 0;
        for (String arg : programArgs) {
            if ("@@".equals(arg)) {
                count++;
            }
        }
        return count > 1;
    }

    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            isRunning = false;
            System.out.println("\n接收到终止信号，正在退出...");
            monitor.printFinalStats();
            shutdown();
        }));
    }

    public void run() {
        printInitialInfo();
        System.out.println("使用 " + numThreads + " 个线程进行模糊测试");

        // 启动多个工作线程
        for (int i = 0; i < numThreads; i++) {
            executorService.submit(this::fuzzingWorker);
        }

        // 等待所有线程完成
        try {
            executorService.shutdown();
            while (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                if (!isRunning) {
                    executorService.shutdownNow();
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void fuzzingWorker() {
        Executor threadExecutor = createExecutor();
        executors.add(threadExecutor);  // Add to list for cleanup

        while (isRunning) {
            // 检查是否达到指定运行时长
            if (System.currentTimeMillis() >= endTimeMillis) {
                isRunning = false;
                System.out.println("\n已达到指定运行时长，测试结束");
                break;
            }

            try {
                Seed currentSeed = scheduler.selectNextSeed();
                if (currentSeed == null) {
                    break;
                }

                // 执行变异和测试
                byte[] mutatedInput = mutator.mutate(currentSeed.getData());

                // 创建新的执行结果对象
                ExecutionResult result = new ExecutionResult();
                result.setInput(mutatedInput);

                if (hasMultipleInputs()) {
                    // 如果是多输入模式，为每个 @@ 创建一个变异后的输入
                    int inputCount = 0;
                    for (String arg : programArgs) {
                        if ("@@".equals(arg)) {
                            inputCount++;
                        }
                    }
                    byte[][] inputs = new byte[inputCount][];
                    for (int i = 0; i < inputCount; i++) {
                        inputs[i] = mutator.mutate(currentSeed.getData());
                    }
                    ExecutionResult multiResult = threadExecutor.executeMultipleInputs(inputs);
                    // 复制执行结果
                    result.setCoverageData(multiResult.getCoverageData());
                    result.setExitCode(multiResult.getExitCode());
                    result.setErrorMessage(multiResult.getErrorMessage());
                    result.setExecutionTime(multiResult.getExecutionTime());
                    result.setTimeout(multiResult.isTimeout());
                } else {
                    ExecutionResult execResult = threadExecutor.execute(mutatedInput);
                    // 复制执行结果
                    result.setCoverageData(execResult.getCoverageData());
                    result.setExitCode(execResult.getExitCode());
                    result.setErrorMessage(execResult.getErrorMessage());
                    result.setExecutionTime(execResult.getExecutionTime());
                    result.setTimeout(execResult.isTimeout());
                }

                totalExecutions.incrementAndGet();

                // 处理执行结果
                if (result.getExitCode() != 0) {
                    // handleCrash(result);
                    crashCount.incrementAndGet();
                } else if (!result.isTimeout()) {
                    handleNewCoverage(result, mutatedInput);
                }

                // 更新监控信息
                monitor.updateStats(result);

                // Periodically clean up stray files (every 1000 executions)
                if (totalExecutions.get() % 1000 == 0 && threadExecutor instanceof ProcessExecutor) {
                    ((ProcessExecutor) threadExecutor).cleanupStrayFiles();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleNewCoverage(ExecutionResult result, byte[] mutatedInput) {
        monitor.recordResult(result);

        if (monitor.hasNewCoverage(result.getCoverageData())) {
            Seed newSeed = new Seed(mutatedInput);
            int energy = calculateNewSeedEnergy(result);
            newSeed.setEnergy(energy);
            scheduler.addSeed(newSeed);

            // Update seed performance metrics
            scheduler.updatePerformance(mutatedInput, result.getExecutionTime(), 1);
            seedSorter.updateSeedPerformance(mutatedInput, result.getExecutionTime(), 1);
        } else {
            scheduler.updatePerformance(mutatedInput, result.getExecutionTime(), 0);
            seedSorter.updateSeedPerformance(mutatedInput, result.getExecutionTime(), 0);
        }
    }

    private void shutdown() {
        this.isRunning = false;
        if (executorService != null) {
            executorService.shutdown();
            try {
                // 给线程池一定时间来完成当前任务
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 清理所有执行器
        for (Executor executor : executors) {
            if (executor instanceof ProcessExecutor) {
                ((ProcessExecutor) executor).cleanupStrayFiles();
            }
        }

        // 清理共享内存
        if (shmManager != null) {
            try {
                shmManager.destroySharedMemory();
            } catch (Exception e) {
                System.err.println("清理共享内存时出错: " + e.getMessage());
            }
        }
    }

    private void loadSeeds() throws IOException {
        AFLSeedGenerator seedGenerator = new AFLSeedGenerator(aflSeedDir);
        List<Seed> initialSeeds = seedGenerator.generateInitialSeeds();

        if (initialSeeds.isEmpty()) {
            throw new IOException("No initial seeds found in directory: " + aflSeedDir);
        }

        // Add seeds one by one instead of using addSeeds
        for (Seed seed : initialSeeds) {
            scheduler.addSeed(seed);
        }
    }

    private int calculateNewSeedEnergy(ExecutionResult result) {
        if (result == null) {
            return 10; // 默认能量值
        }

        final double COVERAGE_WEIGHT = 2.0;  // 覆盖率权重
        final double TIME_WEIGHT = 0.5;      // 时间权重
        final int MIN_ENERGY = 1;            // 最小能量值
        final int INITIAL_ENERGY = 10;       // 初始能量值
        final double ENERGY_LIMIT_FACTOR = 3.0;  // 能量上限因子

        // 计算覆盖率分数 - 基于是否有新的覆盖
        double coverageScore = result.getCoverageData() != null && monitor.hasNewCoverage(result.getCoverageData()) ?
                COVERAGE_WEIGHT : 0;

        // 计算时间效率分数 - 执行时间越短，分数越高
        double timeScore = 1.0 / Math.max(1, Math.sqrt(result.getExecutionTime())) * TIME_WEIGHT;

        // 如果发现了新的覆盖，给予额外奖励
        double newCoverageBonus = monitor.hasNewCoverage(result.getCoverageData()) ? 2.0 : 1.0;

        // 综合评分计算新的能量值
        int energy = (int) (INITIAL_ENERGY * (coverageScore + timeScore) * newCoverageBonus);

        // 限制能量范围
        return Math.max(MIN_ENERGY, Math.min(energy, (int) (INITIAL_ENERGY * ENERGY_LIMIT_FACTOR)));
    }

    private void printInitialInfo() {
        System.out.println("- 共享内存大小: " + MAP_SIZE + " bytes");
        System.out.println("- 目标程序路径: " + targetProgramPath);
        System.out.println("- 使用变异器类型: " + mutatorType);
        System.out.println("- 输出目录: " + outputDir);
    }
}